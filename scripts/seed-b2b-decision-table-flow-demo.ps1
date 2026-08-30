# B2B_RECV 最小演示：决策表 + 决策流 + 事件绑定 + Gateway 造数验证
# 仅通过 HTTP API（BFF / Gateway）；不直写 SQL
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "test-api-lib.ps1")

$Bff = "http://localhost:8080/bff/api/v1"
$Gateway = "http://localhost:8081/api/v1"
$RuleConfig = "http://localhost:8082/api/v1"
$EventCode = "B2B_RECV"
$TableName = "DT_B2B_RECV_AMT"
$FlowName = "B2B_RECV_DT_FLOW"
$OldFlowName = "B2B_RECV_FLOW"

function Login { return Test-ApiLogin -BffBase $Bff }

function Bff($h, $method, $path, $body = $null) {
    return Invoke-BffApi -Headers $h -BffBase $Bff -Method $method -Path $path -Body $body
}

Write-Host "==> login"
$h = Login

Write-Host "==> ensure event $EventCode + amount field"
$scenarios = @(Bff $h GET "/scenarios/tree")
$scn = $scenarios | Where-Object { $_.code -eq "SCN_PAYMENT" } | Select-Object -First 1
if (-not $scn) { $scn = $scenarios | Select-Object -First 1 }
if (-not $scn) { throw "no scenario — run clear-all-data.ps1 or seed-b2b-recv-demo.ps1 first" }

$events = @(Bff $h GET "/events")
$b2b = $events | Where-Object { $_.code -eq $EventCode } | Select-Object -First 1
if (-not $b2b) {
    $b2b = Bff $h POST "/events" @{
        code = $EventCode
        name = "B2B Receive"
        scenarioId = $scn.id
        eventKind = "FACT"
        purposes = @("COMPUTE", "DECISION")
    }
}
try { Bff $h PUT "/scenarios/$($scn.id)/events" @{ eventTypeCodes = @($EventCode) } } catch { }

$allFields = @(Bff $h GET "/fields")
$fidAmount = ($allFields | Where-Object { $_.code -eq "amount" } | Select-Object -First 1).id
if (-not $fidAmount) {
    $fidAmount = (Bff $h POST "/fields" @{ code = "amount"; name = "amount"; dataType = "DOUBLE" }).id
}
$eventFields = @(Bff $h GET "/events/$EventCode/fields")
if (-not ($eventFields | Where-Object { $_.fieldId -eq $fidAmount })) {
    try {
        Bff $h POST "/events/$EventCode/fields" @{
            fieldId = $fidAmount
            purposes = @("COMPUTE", "DECISION")
            derived = $false
        }
    } catch { }
}

Write-Host "==> decision table $TableName (amount thresholds -> PASS/REVIEW/REJECT)"
$tables = @(Bff $h GET "/decision-tables?eventTypeCode=$EventCode")
$table = $tables | Where-Object { $_.name -eq $TableName } | Select-Object -First 1
$tableBody = @{
    name = $TableName
    eventTypeCode = $EventCode
    hitPolicy = "FIRST"
    columns = @(
        @{ var = "amount"; source = "context" }
    )
    rows = @(
        @{
            conditions = @(@{ var = "amount"; op = "GE"; value = 500000; value2 = $null; values = $null })
            decision = "REJECT"
            priority = 10
        },
        @{
            conditions = @(@{ var = "amount"; op = "GE"; value = 100000; value2 = $null; values = $null })
            decision = "REVIEW"
            priority = 20
        },
        @{
            conditions = @(@{ var = "amount"; op = "GE"; value = 0; value2 = $null; values = $null })
            decision = "PASS"
            priority = 30
        }
    )
}
if (-not $table) {
    $table = Bff $h POST "/decision-tables" $tableBody
    Write-Host "  created table id=$($table.id)"
} else {
    $table = Bff $h PUT "/decision-tables/$($table.id)" ($tableBody + @{ status = "ENABLED" })
    Write-Host "  updated table id=$($table.id)"
}
$tableId = $table.id

Write-Host "==> disable other ENABLED flows on $EventCode (runtime binds lowest id)"
$flows = @(Bff $h GET "/decision-flows?eventTypeCode=$EventCode")
foreach ($f in $flows) {
    if ($f.name -ne $FlowName -and $f.status -eq "ENABLED") {
        Write-Host "  disabling flow id=$($f.id) name=$($f.name)"
        $detail = Bff $h GET "/decision-flows/$($f.id)"
        Bff $h PUT "/decision-flows/$($f.id)" @{
            name = $detail.name
            startNodeId = $detail.startNodeId
            status = "DISABLED"
            nodes = $detail.nodes
            edges = $detail.edges
        }
        try { Bff $h POST "/decision-flows/$($f.id):offline" @{} } catch { }
    }
}

Write-Host "==> decision flow $FlowName"
$flow = $flows | Where-Object { $_.name -eq $FlowName } | Select-Object -First 1
if (-not $flow) {
    $flow = Bff $h POST "/decision-flows" @{ name = $FlowName; eventTypeCode = $EventCode }
}
$flowBody = @{
    name = $FlowName
    startNodeId = "start"
    status = "ENABLED"
    nodes = @(
        @{ nodeId = "start"; type = "START"; refType = $null; refId = $null; config = $null }
        @{
            nodeId = "dt_amt"
            type = "DECISION_TOOL"
            refType = "DECISION_TABLE"
            refId = $tableId
            config = $null
        }
        @{ nodeId = "gw"; type = "CONDITION_GATEWAY"; refType = $null; refId = $null; config = $null }
        @{ nodeId = "end_reject"; type = "END"; refType = $null; refId = $null; config = '{"endDecision":"AUTO_REJECT"}' }
        @{ nodeId = "end_review"; type = "END"; refType = $null; refId = $null; config = '{"endDecision":"MANUAL_REVIEW"}' }
        @{ nodeId = "end_pass"; type = "END"; refType = $null; refId = $null; config = '{"endDecision":"AUTO_PASS"}' }
    )
    edges = @(
        @{ from = "start"; to = "dt_amt"; condition = $null; isDefault = $false }
        @{ from = "dt_amt"; to = "gw"; condition = $null; isDefault = $false }
        @{ from = "gw"; to = "end_reject"; condition = "lastDecision == 'REJECT'"; isDefault = $false }
        @{ from = "gw"; to = "end_review"; condition = "lastDecision == 'REVIEW'"; isDefault = $false }
        @{ from = "gw"; to = "end_pass"; condition = $null; isDefault = $true }
    )
}
Bff $h PUT "/decision-flows/$($flow.id)" $flowBody
$h = Login
$versions = @(Bff $h GET "/decision-flows/$($flow.id)/versions")
$latestVer = ($versions | Sort-Object version -Descending | Select-Object -First 1).version
try {
    Bff $h POST "/decision-flows/$($flow.id)/versions/${latestVer}:online" @{}
    Write-Host "  flow id=$($flow.id) version $latestVer ONLINE"
} catch {
    Write-Host "  BFF online failed: $($_.Exception.Message); retry login + rule-config"
    $h = Login
    try {
        Invoke-RuleConfigApi -RuleConfigBase $RuleConfig -Method POST `
            -Path "/decision-flows/$($flow.id)/versions/${latestVer}:online" -Headers $h
        Write-Host "  flow version $latestVer ONLINE (8082 direct)"
    } catch {
        Write-Host "  WARN: publish failed — engine may still run draft fallback"
    }
}

Write-Host "==> gateway smoke (decision table demo)"
function Test-Event($label, $ctx) {
    $r = Invoke-GatewayEvent -GatewayBase $Gateway -EventTypeCode $EventCode -Context $ctx
    Write-Host "  [$label] decision=$($r.decision) eventId=$($r.eventId)"
}
Test-Event "pass-small" @{ merchantId = "M_DT_OK"; amount = 5000 }
Test-Event "review-mid" @{ merchantId = "M_DT_REV"; amount = 150000 }
Test-Event "reject-large" @{ merchantId = "M_DT_REJ"; amount = 600000 }

Write-Host ""
Write-Host "DONE"
Write-Host "  decision table id=$tableId name=$TableName"
Write-Host "  decision flow id=$($flow.id) name=$FlowName (bound to $EventCode)"
Write-Host "  console: 决策表 -> 决策流墙 -> POST http://localhost:8081/api/v1/risk-events"
