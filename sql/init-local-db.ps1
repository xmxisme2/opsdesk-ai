param(
    [string]$Mysql = "D:\IdeaProjects\mysql\bin\mysql.exe",
    [string]$HostName = "localhost",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Password = "root123456"
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not (Test-Path -LiteralPath $Mysql)) {
    $Mysql = "mysql"
}

function Invoke-AiSqlFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SqlFile
    )

    $resolvedPath = (Resolve-Path -LiteralPath $SqlFile).Path.Replace("\", "/")
    $arguments = @(
        "--default-character-set=utf8mb4",
        "--host=$HostName",
        "--port=$Port",
        "--user=$User",
        "--execute=source $resolvedPath"
    )
    if ($Password -ne "") {
        $arguments = @(
            "--default-character-set=utf8mb4",
            "--host=$HostName",
            "--port=$Port",
            "--user=$User",
            "--password=$Password",
            "--execute=source $resolvedPath"
        )
    }

    & $Mysql @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "SQL execution failed: $resolvedPath"
    }
}

Invoke-AiSqlFile -SqlFile (Join-Path $scriptRoot "01_schema.sql")
Invoke-AiSqlFile -SqlFile (Join-Path $scriptRoot "02_seed.sql")
Invoke-AiSqlFile -SqlFile (Join-Path $scriptRoot "verify-schema.sql")

Write-Host "OpsDesk AI local database initialization completed."
