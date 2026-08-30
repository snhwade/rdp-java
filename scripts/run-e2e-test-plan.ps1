# B2B_RECV 事中复杂场景 + 引擎/AI 双轨 端到端测试（仅 HTTP API，不写业务 SQL）
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "test-api-lib.ps1")

$Bff = "http://localhost:8080/bff/api/v1"
$Gateway = "http://localhost:8081/api/v1"
$IndicatorStore = "http://localhost:8084/api/v1"
$EventCode = "B2B_RECV"

$Results = [System.Collections.Generic.List[object]]::new()

function Login { return Test-ApiLogin -BffBase $Bff }

function Record($id, $name, $pass, $detail) {
    $Results.Add([pscustomobject]@{
            Id     = $id
            Case   = $name
            Pass   = $pass
            Detail = $detail
        })
    $mark = if ($pass) { "PASS" } else { "FAIL" }
    Write-Host "[$mark] $id $name - $detail"
}

function BffGet($h, $path) {
    return Invoke-BffApi -Headers $h -BffBase $Bff -Method GET -Path $path
}

function Write-Indicator($refName, $merchantId, $value) {
    Invoke-IndicatorWrite -IndicatorBase $IndicatorStore -RefName $refName -DimensionKey $merchantId -Value $value | Out-Null
}

function PostEvent($ctx) {
    return Invoke-GatewayEvent -GatewayBase $Gateway -EventTypeCode $EventCode -Context $ctx
}

function Wait-AiRecord($h, $eventId, $seconds = 5) {
    for ($i = 0; $i -lt $seconds; $i++) {
        try {
            $ai = BffGet $h "/ai-decision-records/$eventId"
            if ($ai -and $ai.status -eq "SUCCESS") { return $ai }
        } catch { }
        Start-Sleep -Seconds 1
    }
    return $null
}

function Assert-DualTrack($h, $id, $label, $ev, $expectFinal, $expectAi, $expectDivergence) {
    $eid = $ev.eventId
    if (-not $eid) {
        Record "${id}a" "$label engine record" $false "no eventId"
        Record "${id}b" "$label AI record" $false "no eventId"
        return
    }
    try {
        $eng = BffGet $h "/engine-decision-records/$eid"
        $engOk = $eng -and $eng.finalDecision -eq $expectFinal
        Record "${id}a" "$label engine final=$expectFinal" $engOk "engine=$($eng.engineDecision) final=$($eng.finalDecision)"
    } catch {
        Record "${id}a" "$label engine record" $false $_.Exception.Message
    }
    $ai = Wait-AiRecord $h $eid
    if (-not $ai) {
        Record "${id}b" "$label AI SUCCESS" $false "timeout"
        return
    }
    $aiOk = ($ai.agentDecision -eq $expectAi)
    if ($null -ne $expectDivergence) {
        $aiOk = $aiOk -and ([bool]$ai.divergence -eq $expectDivergence)
    }
    Record "${id}b" "$label AI agent=$expectAi" $aiOk "agent=$($ai.agentDecision) divergence=$($ai.divergence)"
}

Write-Host "========================================"
Write-Host "  E2E - B2B_RECV Complex In-Flight + Dual Track"
Write-Host "========================================"

try {
    $h = Login
    Record "T00" "BFF login" $true "token ok"
} catch {
    Record "T00" "BFF login" $false $_.Exception.Message
    $Results | Format-Table -AutoSize
    exit 1
}

Write-Host "`n==> Phase 1: seed"
$seedScript = Join-Path $PSScriptRoot "seed-b2b-recv-demo.ps1"
$seedOk = $true
$seedExit = 0
try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $seedScript
    $seedExit = $LASTEXITCODE
    if ($seedExit -ne 0) { $seedOk = $false }
    if (-not (Test-EventTypeVisibleToGateway -EventCode $EventCode)) {
        $seedOk = $false
        Record "T01" "Seed complex B2B demo" $false "B2B_RECV missing on event-types (gateway 404)"
    }
} catch {
    $seedOk = $false
    Record "T01" "Seed complex B2B demo" $false $_.Exception.Message
}
if ($seedOk) {
    Record "T01" "Seed complex B2B demo" $true "seed finished"
}
if (-not $seedOk) {
    Write-Host "Seed failed — skipping gateway tests"
    $Results | ForEach-Object { [pscustomobject]@{ Id = $_.Id; Case = $_.Case; Pass = $_.Pass; Detail = $_.Detail } } | Format-Table -AutoSize
    exit 1
}

Write-Host "`n==> Phase 2: basic gateway decisions"
$tc02 = PostEvent @{ merchantId = "M_OK_001"; amount = 5000 }
Record "T02" "Normal small -> PASS" ($tc02.decision -eq "PASS") "decision=$($tc02.decision)"

$tc03 = PostEvent @{ merchantId = "M_BLACK_001"; amount = 5000 }
Record "T03" "Blacklist -> REJECT" ($tc03.decision -eq "REJECT") "decision=$($tc03.decision)"

$tc04 = PostEvent @{ merchantId = "M_OK_002"; amount = 200000 }
Record "T04" "Large amount -> REJECT (flow gateway)" ($tc04.decision -eq "REJECT") "decision=$($tc04.decision)"

Write-Host "`n==> Phase 3: indicator rules"
try {
    Write-Indicator "b2b_daily_amt" "M_IND_E2E" 160000
    $tc07 = PostEvent @{ merchantId = "M_IND_E2E"; amount = 5000 }
    Record "T07" "Daily indicator -> REVIEW" ($tc07.decision -eq "REVIEW") "decision=$($tc07.decision)"
} catch {
    Record "T07" "Daily indicator -> REVIEW" $false $_.Exception.Message
    $tc07 = $null
}

try {
    Write-Indicator "ai_fraud_score" "M_AI_E2E" 0.92
    $tc08 = PostEvent @{ merchantId = "M_AI_E2E"; amount = 5000 }
    Record "T08" "AI fraud score rule -> REVIEW" ($tc08.decision -eq "REVIEW") "decision=$($tc08.decision)"
} catch {
    Record "T08" "AI fraud score rule -> REVIEW" $false $_.Exception.Message
    $tc08 = $null
}

Write-Host "`n==> Phase 4: multi-rule strictest (REJECT > REVIEW)"
try {
    Write-Indicator "b2b_daily_amt" "M_MULTI_001" 160000
    $tc09 = PostEvent @{ merchantId = "M_MULTI_001"; amount = 200000 }
    Record "T09" "Daily REVIEW + large REJECT -> REJECT" ($tc09.decision -eq "REJECT") "decision=$($tc09.decision)"
} catch {
    Record "T09" "Daily REVIEW + large REJECT -> REJECT" $false $_.Exception.Message
    $tc09 = $null
}

Write-Host "`n==> Phase 5: engine + AI dual track"
Assert-DualTrack $h "T02" "Normal" $tc02 "PASS" "PASS" $false
Assert-DualTrack $h "T03" "Blacklist" $tc03 "REJECT" "REJECT" $true
Assert-DualTrack $h "T04" "Large amount" $tc04 "REJECT" "REVIEW" $true
if ($tc07) { Assert-DualTrack $h "T07" "Daily indicator" $tc07 "REVIEW" "PASS" $true }
if ($tc08) { Assert-DualTrack $h "T08" "AI fraud rule" $tc08 "REVIEW" "PASS" $true }

Write-Host "`n==> Phase 6: correlation"
try {
    $engList = BffGet $h "/engine-decision-records?eventId=$($tc02.eventId)"
    $corr = $engList.data[0].correlationId
    $aiByCorr = BffGet $h "/ai-decision-records?correlationId=$corr"
    $linked = @($aiByCorr.data | Where-Object { $_.eventId -eq $tc02.eventId }).Count -ge 1
    Record "T05" "CorrelationId links engine & AI" $linked "correlationId=$corr"
} catch {
    Record "T05" "CorrelationId links engine & AI" $false $_.Exception.Message
}

try {
    $orders = BffGet $h "/orders?merchantId=M_OK_001&pageSize=5"
    $hasOrder = $orders.data -and $orders.data.Count -gt 0
    Record "T06" "Order query" $hasOrder "count=$($orders.data.Count)"
} catch {
    Record "T06" "Order query" $false $_.Exception.Message
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
