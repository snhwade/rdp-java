# 运行各微模块 Java 集成测试（需本机 MySQL + Redis）
$ErrorActionPreference = "Stop"

$JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\song\environment\java\jdk-17.0.12" }
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"

$Root = Split-Path -Parent $PSScriptRoot
$Modules = @(
    "rule-config-service",
    "rule-decision-engine",
    "decision-gateway",
    "indicator-store-service",
    "screening-service",
    "merchant-rating-service",
    "admin-bff"
)

$Results = [System.Collections.Generic.List[object]]::new()
$Failed = $false

Write-Host "========================================"
Write-Host "  Java Integration Tests (all modules)"
Write-Host "========================================"

foreach ($mod in $Modules) {
    Write-Host "`n==> $mod"
    Push-Location (Join-Path $Root $mod)
    try {
        mvn test -Dtest="*IntegrationTest*" -q
        if ($LASTEXITCODE -ne 0) {
            $Failed = $true
            $Results.Add([pscustomobject]@{ Module = $mod; Pass = $false })
        } else {
            $Results.Add([pscustomobject]@{ Module = $mod; Pass = $true })
        }
    } finally {
        Pop-Location
    }
}

Write-Host "`n========================================"
Write-Host "  Summary"
Write-Host "========================================"
$Results | Format-Table -AutoSize
$passed = ($Results | Where-Object { $_.Pass }).Count
Write-Host "Total: $passed / $($Results.Count) modules passed"
if ($Failed) { exit 1 }
