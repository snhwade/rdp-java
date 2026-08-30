# 优化模块集成测试：指标 IR/IS/IV、用户 OU1、查询监控 XT/XS（需服务已启动）
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "test-api-lib.ps1")

$Bff = "http://localhost:8080/bff/api/v1"
$Gateway = "http://localhost:8081/api/v1"
$RuleConfig = "http://localhost:8082/api/v1"
$EventCode = "B2B_RECV"

$Results = [System.Collections.Generic.List[object]]::new()
$Marker = "ZZIT_E2E_"
$RunId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

function Record($id, $name, $pass, $detail) {
    $Results.Add([pscustomobject]@{ Id = $id; Case = $name; Pass = $pass; Detail = $detail })
    $mark = if ($pass) { "PASS" } else { "FAIL" }
    Write-Host "[$mark] $id $name - $detail"
}

function BffGet($h, $path) { return Invoke-BffApi -Headers $h -BffBase $Bff -Method GET -Path $path }
function BffPost($h, $path, $body) { return Invoke-BffApi -Headers $h -BffBase $Bff -Method POST -Path $path -Body $body }
function BffPut($h, $path, $body) { return Invoke-BffApi -Headers $h -BffBase $Bff -Method PUT -Path $path -Body $body }
function BffDelete($h, $path) {
    $uri = "$Bff$path"
    return Invoke-RestMethod -Uri $uri -Method DELETE -Headers @{ Authorization = $h.Authorization }
}

function PostEvent($ctx) {
    return Invoke-GatewayEvent -GatewayBase $Gateway -EventTypeCode $EventCode -Context $ctx
}

function Assert-ServiceUp($name, $url) {
    try {
        Invoke-RestMethod -Uri $url -Method GET -TimeoutSec 5 | Out-Null
        return $true
    } catch {
        return $false
    }
}

Write-Host "========================================"
Write-Host "  Optimization Module Integration Tests"
Write-Host "========================================"

# 前置：BFF / Gateway 健康
$svcOk = $true
if (-not (Assert-ServiceUp "admin-bff" "http://localhost:8080/actuator/health")) {
    Record "P00" "admin-bff health" $false "8080 down"
    $svcOk = $false
}
if (-not (Assert-ServiceUp "decision-gateway" "http://localhost:8081/actuator/health")) {
    Record "P01" "decision-gateway health" $false "8081 down"
    $svcOk = $false
}
if (-not $svcOk) {
    $Results | Format-Table -AutoSize
    exit 1
}
Record "P00" "Services up" $true "8080/8081 ok"

try {
    $h = Test-ApiLogin -BffBase $Bff
    Record "P02" "BFF login (admin)" $true "token ok"
} catch {
    Record "P02" "BFF login (admin)" $false $_.Exception.Message
    $Results | Format-Table -AutoSize
    exit 1
}

# —— OU1：用户治理 ——
Write-Host "`n==> OU1 User governance"
$ouUser = "${Marker}user_${RunId}"
try {
    $created = BffPost $h "/users" @{ username = $ouUser; password = "Passw0rd!"; roles = @("OPERATOR") }
    $uid = $created.id
    $disabled = BffPut $h "/users/$uid/enabled" @{ enabled = $false }
    $enabled = BffPut $h "/users/$uid/enabled" @{ enabled = $true }
    $roles = BffPut $h "/users/$uid/roles" @{ roles = @("AUDITOR") }
    $reset = BffPut $h "/users/$uid/reset-password" @{ password = "NewPass99!" }
    $ok = ($disabled.enabled -eq $false) -and ($enabled.enabled -eq $true) -and ($roles.roles -contains "AUDITOR")
    Record "OU1" "Enable/disable/roles/reset-password" $ok "userId=$uid"
} catch {
    Record "OU1" "User governance via BFF" $false $_.Exception.Message
}

# —— 指标 IR1/IS1/IV1（经 BFF 代理 rule-config）——
Write-Host "`n==> Indicator IR1/IS1/IV1"
$refName = "ZZITe2e$RunId"
$indId = $null
try {
    $body = @{
        refName          = $refName
        name             = "E2E-Indicator"
        description      = "E2E-remark"
        eventTypeCodes   = @($EventCode)
        dimensions       = @("merchantId")
        windowDays       = 1
        sliceGranularity = "DAY"
        accScript        = "amount"
    }
    $ind = BffPost $h "/indicator-definitions" $body
    $indId = $ind.id
    $descOk = ($ind.description -eq "E2E-remark")
    Record "ID1" "Indicator description" $descOk "description=$($ind.description)"

    BffPut $h "/indicator-definitions/$indId/online" @{} | Out-Null

    BffPut $h "/indicator-definitions/$indId" (@{
        name = "E2E-V1"; description = "E2E-V1"; eventTypeCodes = @($EventCode)
        dimensions = @("merchantId"); windowDays = 1; sliceGranularity = "DAY"; accScript = "amount"
    }) | Out-Null
    BffPut $h "/indicator-definitions/$indId" (@{
        name = "E2E-V2"; description = "E2E-V2"; eventTypeCodes = @($EventCode)
        dimensions = @("merchantId"); windowDays = 1; sliceGranularity = "DAY"; accScript = "amount"
    }) | Out-Null
    BffPut $h "/indicator-definitions/$indId" (@{
        name = "E2E-V3"; description = "E2E-V3"; eventTypeCodes = @($EventCode)
        dimensions = @("merchantId"); windowDays = 1; sliceGranularity = "DAY"; accScript = "amount"
    }) | Out-Null
    $snaps = BffGet $h "/indicator-definitions/$indId/definition-snapshots"
    $snapOk = ($snaps.Count -ge 2)
    Record "IV1" "Definition snapshots after updates" $snapOk "count=$($snaps.Count)"

    if ($snaps.Count -ge 2) {
        $rolled = BffPost $h "/indicator-definitions/$indId/rollback-last-definition" @{}
        $rollOk = ($rolled.name -eq "E2E-V1")
        Record "IV1b" "Rollback restores previous definition" $rollOk "name=$($rolled.name)"
    }

    # IS1：读缺失写入运行统计（须在 rollback 之后、指标仍存在时触发）
    $readUri = "http://localhost:8084/api/v1/indicators/$refName" + "?dimensionKey=M_NO_DATA&windowDays=1&granularity=DAY"
    $readResult = Invoke-RestMethod -Uri $readUri -Method GET
    $readMissTriggered = $readResult.missing -eq $true
    Start-Sleep -Seconds 2
    $stats = BffGet $h "/indicator-definitions/runtime-stats?refName=$refName"
    $statsOk = $readMissTriggered -and ($stats.Count -ge 1) -and ($stats[0].readMissCount -ge 1)
    Record "IS1" "Runtime stats read-miss count" $statsOk "missing=$readMissTriggered readMiss=$($stats[0].readMissCount)"

    $refsBefore = BffGet $h "/indicator-definitions/references?refName=$refName"
    Record "IR1" "References query" ($null -ne $refsBefore) "count=$($refsBefore.Count)"
} catch {
    Record "IND" "Indicator optimization APIs" $false $_.Exception.Message
} finally {
    if ($indId) {
        try { BffPut $h "/indicator-definitions/$indId/offline" @{} | Out-Null } catch { }
        try { BffDelete $h "/indicator-definitions/$indId" } catch { }
    }
}

# —— XT1 / XS1 / XL1：查询与监控 ——
Write-Host "`n==> XT1/XS1/XL1 Query & observability"
try {
    $ev = PostEvent @{ merchantId = "M_OK_001"; amount = 5000 }
    $eid = $ev.eventId
    if (-not $eid) {
        Record "XT1" "Gateway eventId" $false "no eventId"
    } else {
        Start-Sleep -Seconds 1
        $trace = BffGet $h "/trace/$eid"
        $traceOk = ($trace.eventId -eq $eid) -and ($null -ne $trace.ruleExecutions)
        Record "XT1" "Execution trace via BFF" $traceOk "rules=$($trace.ruleExecutions.Count) selector=$($null -ne $trace.selectorMatch)"

        $eng = BffGet $h "/engine-decision-records/$eid"
        $xlOk = ($eng.eventId -eq $eid) -and ($trace.eventId -eq $eng.eventId)
        Record "XL1" "Trace links same eventId as engine record" $xlOk "eventId=$eid"

        $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        $start = $now - 3600000
        $stats = BffGet $h "/engine-decision-records/stats?startTimeMs=$start&endTimeMs=$now"
        $xsOk = ($stats.total -ge 1) -and ($null -ne $stats.decisionDistribution)
        Record "XS1" "Engine decision stats" $xsOk "total=$($stats.total) avgMs=$($stats.avgElapsedMs)"
    }
} catch {
    Record "QRY" "Query/observability" $false $_.Exception.Message
}

Write-Host "`n========================================"
Write-Host "  Test Summary"
Write-Host "========================================"
$passed = ($Results | Where-Object { $_.Pass }).Count
$total = $Results.Count
$Results | ForEach-Object {
    [pscustomobject]@{ Id = $_.Id; Case = $_.Case; Pass = $_.Pass; Detail = $_.Detail }
} | Format-Table -AutoSize
Write-Host "Total: $passed / $total passed"
if ($passed -lt $total) { exit 1 }
