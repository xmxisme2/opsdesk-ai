param(
    [Parameter(Mandatory = $true)]
    [string]$DatasetPath,
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [ValidateRange(1, 20)]
    [int]$K = 5
)

$ErrorActionPreference = "Stop"

# Service JWT 仅从进程环境读取，避免进入脚本、评测集或报告文件。
$serviceToken = $env:AI_EVAL_SERVICE_TOKEN
if ([string]::IsNullOrWhiteSpace($serviceToken)) {
    throw "请通过 AI_EVAL_SERVICE_TOKEN 提供短期 Service JWT"
}

$cases = Get-Content -LiteralPath $DatasetPath -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $cases -or $cases.Count -eq 0) {
    throw "评测集不能为空"
}

$hitCount = 0
$details = foreach ($case in $cases) {
    $body = @{ keyword = $case.question; size = $K } | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/internal/knowledge/search" `
        -Headers @{ Authorization = "Bearer $serviceToken" } `
        -ContentType "application/json; charset=utf-8" -Body $body
    $actualIds = @($response.data | ForEach-Object { [string]$_.articleId } | Select-Object -Unique)
    $expectedIds = @($case.expectedArticleIds | ForEach-Object { [string]$_ })
    $matched = @($expectedIds | Where-Object { $actualIds -contains $_ }).Count -gt 0
    if ($matched) { $hitCount++ }
    [ordered]@{
        caseId = $case.caseId
        matched = $matched
        expectedArticleIds = $expectedIds
        actualArticleIds = $actualIds
    }
}

$recallAtK = [math]::Round($hitCount / $cases.Count, 4)
[ordered]@{
    metric = "Recall@$K"
    total = $cases.Count
    hits = $hitCount
    value = $recallAtK
    details = $details
} | ConvertTo-Json -Depth 6
