param(
    [string]$WorkspaceRoot = "D:\OpsDesk",
    [string]$DatasetPath = "",
    [ValidateRange(1, 20)]
    [int]$K = 5
)

$ErrorActionPreference = "Stop"
$backendRoot = Join-Path $WorkspaceRoot "opsdesk-backend"
$aiRoot = Join-Path $WorkspaceRoot "opsdesk-ai-service"
$mysql = "D:\IdeaProjects\mysql\bin\mysql.exe"
$env:MYSQL_PWD = "root123456"
if ([string]::IsNullOrWhiteSpace($DatasetPath)) {
    $DatasetPath = Join-Path $aiRoot "evaluation\rag-recall-baseline.json"
}

# 每次运行在内存中生成共享密钥，只传递给本轮启动的两个子进程，不落盘、不输出。
$secretBytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($secretBytes)
$sharedSecret = [Convert]::ToBase64String($secretBytes)
$env:AI_SERVICE_JWT_SECRET = $sharedSecret
$env:AI_INDEXING_ENABLED = "false"
$env:AI_EVENTS_ENABLED = "false"
$env:AI_KEY_PROPERTIES_PATH = Join-Path $backendRoot "src\main\resources\key.properties"

$localAiEnv = Join-Path $WorkspaceRoot "deploy\local-ai\.env.local"
Get-Content -LiteralPath $localAiEnv -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $pair = $line.Split("=", 2)
        [Environment]::SetEnvironmentVariable($pair[0].Trim(), $pair[1].Trim(), "Process")
    }
}
$env:AI_RABBITMQ_USERNAME = $env:RABBITMQ_APP_USER
$env:AI_RABBITMQ_PASSWORD = $env:RABBITMQ_APP_PASSWORD
$env:AI_RABBITMQ_VHOST = $env:RABBITMQ_VHOST
$env:AI_OPENSEARCH_USERNAME = $env:OPENSEARCH_APP_USER
$env:AI_OPENSEARCH_PASSWORD = $env:OPENSEARCH_APP_PASSWORD

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function New-ServiceToken {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $header = @{ alg = "HS256"; typ = "JWT" } | ConvertTo-Json -Compress
    $payload = [ordered]@{
        jti = [Guid]::NewGuid().ToString("N")
        iss = "opsdesk-backend"
        aud = "opsdesk-ai-service"
        sub = "opsdesk-backend"
        service = "opsdesk-backend"
        tokenType = "SERVICE"
        iat = $now
        exp = $now + 300
        userId = "1"
        roles = @("ADMIN")
    } | ConvertTo-Json -Compress
    $encodedHeader = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($header))
    $encodedPayload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
    $content = "$encodedHeader.$encodedPayload"
    $hmac = New-Object Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($sharedSecret)
    $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($content)))
    return "$content.$signature"
}

function Wait-ServiceHealth([string]$Url, [string]$Method, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Url -Method $Method -UseBasicParsing -TimeoutSec 3
            $content = if ($response.Content -is [byte[]]) {
                [Text.Encoding]::UTF8.GetString($response.Content)
            } else {
                [string]$response.Content
            }
            if ($content -match '"status"\s*:\s*"UP"') {
                return $true
            }
        } catch {
            # 服务编译和启动期间连接失败属于预期状态，直到超时才报告失败。
            $null = $_
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Read-IndexTask([string]$TaskId) {
    $query = "USE opsdesk_ai; SELECT task_status,total_count,success_count,failed_count,COALESCE(last_error,'') FROM ai_index_task WHERE id=$TaskId AND deleted=0;"
    $row = & $mysql --default-character-set=utf8mb4 -h localhost -uroot -N -B -e $query
    return @($row -split "`t")
}

function Stop-ProcessTree([int]$RootProcessId) {
    $children = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.ParentProcessId -eq $RootProcessId }
    foreach ($child in $children) {
        Stop-ProcessTree $child.ProcessId
    }
    Stop-Process -Id $RootProcessId -Force -ErrorAction SilentlyContinue
}

$backendProcess = $null
$aiProcess = $null
try {
    $backendProcess = Start-Process -FilePath (Join-Path $backendRoot "mvnw.cmd") `
        -ArgumentList @("-q", "spring-boot:run") -WorkingDirectory $backendRoot `
        -WindowStyle Hidden -PassThru
    $aiProcess = Start-Process -FilePath (Join-Path $aiRoot "mvnw.cmd") `
        -ArgumentList @("-q", "spring-boot:run") -WorkingDirectory $aiRoot `
        -WindowStyle Hidden -PassThru

    if (-not (Wait-ServiceHealth "http://127.0.0.1:8080/api/health/check" "Post" 120)) {
        throw "主应用启动超时"
    }
    if (-not (Wait-ServiceHealth "http://127.0.0.1:8081/actuator/health/liveness" "Get" 120)) {
        throw "AI 服务启动超时"
    }
    Write-Output "services=UP"

    $requestId = "recall5-seed-" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $rebuild = Invoke-RestMethod -Method Post `
        -Uri "http://127.0.0.1:8081/internal/admin/index/rebuild" `
        -Headers @{ Authorization = "Bearer $(New-ServiceToken)" } `
        -ContentType "application/json; charset=utf-8" `
        -Body (@{ confirmText = "REBUILD"; clientRequestId = $requestId } | ConvertTo-Json -Compress) `
        -TimeoutSec 30
    $taskId = [string]$rebuild.data.taskId
    Write-Output "rebuildTask=$taskId"

    $deadline = (Get-Date).AddMinutes(8)
    do {
        Start-Sleep -Seconds 3
        $task = Read-IndexTask $taskId
        $status = $task[0]
        if ($status -in @("SUCCESS", "FAILED")) {
            break
        }
    } while ((Get-Date) -lt $deadline)
    if ($status -ne "SUCCESS") {
        throw "向量重建未成功：$($task -join ' | ')"
    }
    Write-Output "rebuild=SUCCESS,total=$($task[1]),success=$($task[2])"

    $cases = Get-Content -LiteralPath $DatasetPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $hits = 0
    $details = New-Object System.Collections.Generic.List[object]
    $position = 0
    foreach ($case in $cases) {
        $position++
        try {
            $response = Invoke-RestMethod -Method Post `
                -Uri "http://127.0.0.1:8081/internal/knowledge/search" `
                -Headers @{ Authorization = "Bearer $(New-ServiceToken)" } `
                -ContentType "application/json; charset=utf-8" `
                -Body (@{ keyword = $case.question; size = $K } | ConvertTo-Json -Compress) `
                -TimeoutSec 60
            $actual = @($response.data | ForEach-Object { [string]$_.articleId } | Select-Object -Unique)
            $expected = @($case.expectedArticleIds | ForEach-Object { [string]$_ })
            $matched = @($expected | Where-Object { $actual -contains $_ }).Count -gt 0
            if ($matched) {
                $hits++
            }
            $details.Add([ordered]@{
                caseId = $case.caseId
                matched = $matched
                expectedArticleIds = $expected
                actualArticleIds = $actual
            })
        } catch {
            $details.Add([ordered]@{
                caseId = $case.caseId
                matched = $false
                expectedArticleIds = @($case.expectedArticleIds)
                actualArticleIds = @("REQUEST_FAILED")
            })
        }
        if ($position % 6 -eq 0) {
            Write-Output "evaluated=$position/$($cases.Count)"
        }
    }

    $recall = [math]::Round($hits / $cases.Count, 4)
    $failed = @($details | Where-Object { -not $_.matched })
    [ordered]@{
        metric = "Recall@$K"
        total = $cases.Count
        hits = $hits
        value = $recall
        failedCases = $failed
    } | ConvertTo-Json -Depth 7
} finally {
    # 仅停止本脚本启动的确切进程，不影响其他本地 Java 或 Maven 任务。
    if ($aiProcess -and -not $aiProcess.HasExited) {
        Stop-ProcessTree $aiProcess.Id
    }
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-ProcessTree $backendProcess.Id
    }
}

