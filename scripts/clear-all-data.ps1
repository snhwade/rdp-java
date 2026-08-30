$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $PSScriptRoot
$SqlFile = Join-Path $PSScriptRoot "clear-all-data.sql"
$MigrationDir = Join-Path $Root "rule-config-service\src\main\resources\db\migration"
$Mysql = "mysql"
$Db = "risk_decision_platform"
$MysqlCharsetArgs = @("--default-character-set=utf8mb4", "-uroot", "-proot", $Db)

try { chcp 65001 | Out-Null } catch { }
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

function Invoke-MysqlUtf8 {
    param([string]$SqlPath)
    $content = if ($SqlPath -eq $SqlFile) {
        Get-Content $SqlPath -Raw -Encoding UTF8
    } else {
        Get-Content $SqlPath -Raw -Encoding UTF8
    }
    $tempFile = Join-Path $env:TEMP "rdp-sql-$(Get-Random).sql"
    [System.IO.File]::WriteAllText($tempFile, $content, [System.Text.UTF8Encoding]::new($false))
  cmd /c "mysql --default-character-set=utf8mb4 -uroot -proot $Db < `"$tempFile`"" 2>$null
    Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
}

Write-Host "==> truncate business tables"
Invoke-MysqlUtf8 $SqlFile

Write-Host "==> remove Flyway repeatable seed rows"
& $Mysql --default-character-set=utf8mb4 -uroot -proot $Db -e "DELETE FROM flyway_schema_history WHERE script LIKE 'R__%';" 2>$null

Write-Host "==> re-apply repeatable seed SQL (UTF-8)"
$repeatables = @(
    "R__seed_param_management.sql",
    "R__seed_rules.sql",
    "R__seed_rating.sql",
    "R__seed_flows.sql"
)
foreach ($name in $repeatables) {
    $path = Join-Path $MigrationDir $name
    if (Test-Path $path) {
        Write-Host "  running $name"
        Invoke-MysqlUtf8 $path
    }
}

Write-Host "==> try flush Redis (indicator cache)"
try {
    redis-cli FLUSHALL 2>$null | Out-Null
    Write-Host "  redis flushed"
} catch {
    Write-Host "  redis not available, skip"
}

Write-Host "DONE - data cleared. sys_user preserved."
