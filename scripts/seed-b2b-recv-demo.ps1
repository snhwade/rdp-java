# B2B recv demo seed — 仅通过对外 HTTP API 造数（BFF / Gateway / Indicator / rule-config）
# 禁止 INSERT 业务表；清库请用 clear-all-data.ps1
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "test-api-lib.ps1")

$Bff = "http://localhost:8080/bff/api/v1"
$Screening = "http://localhost:8085/api/v1"
$Gateway = "http://localhost:8081/api/v1"
$IndicatorStore = "http://localhost:8084/api/v1"
$RuleConfig = "http://localhost:8082/api/v1"
$EventCode = "B2B_RECV"

function Login { return Test-ApiLogin -BffBase $Bff }

function Bff($h, $method, $path, $body = $null) {
    return Invoke-BffApi -Headers $h -BffBase $Bff -Method $method -Path $path -Body $body
}

function Screening($h, $method, $path, $body = $null) {
    $uri = "$Screening$path"
    if ($body -ne $null) {
        $json = $body | ConvertTo-Json -Depth 10 -Compress
        return Invoke-RestMethod -Uri $uri -Method $method -Headers $h `
            -ContentType "application/json; charset=utf-8" `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
    }
    return Invoke-RestMethod -Uri $uri -Method $method -Headers $h
}

Write-Host "==> login"
$h = Login

function Ensure-EventType {
    $h = Login
    $scenarios = @(Bff $h GET "/scenarios/tree")
    $scn = $scenarios | Where-Object { $_.code -eq "SCN_PAYMENT" } | Select-Object -First 1
    if (-not $scn) {
        $scn = $scenarios | Select-Object -First 1
    }
    if (-not $scn) {
        throw "no scenario in tree — run clear-all-data.ps1 first"
    }
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
        Write-Host "  created event $($EventCode) id=$($b2b.id) scenarioId=$($scn.id)"
    }
    try {
        Bff $h PUT "/scenarios/$($scn.id)/events" @{ eventTypeCodes = @($EventCode) }
    } catch {
        Write-Host "  skip scenario link: $($_.Exception.Message)"
    }
    if (-not (Test-EventTypeVisibleToGateway -EventCode $EventCode)) {
        throw "gateway checker cannot see ENABLED $EventCode on GET /event-types (8082)"
    }
    return $b2b
}

Write-Host "==> event type $EventCode"
$h = Login
$b2b = Ensure-EventType

Write-Host "==> fields"
function Ensure-Field($code, $name, $dataType) {
    $all = @(Bff $h GET "/fields")
    $existing = $all | Where-Object { $_.code -eq $code }
    if ($existing) { return $existing | Select-Object -First 1 }
    try {
        return Bff $h POST "/fields" @{ code = $code; name = $name; dataType = $dataType }
    } catch {
        $all = @(Bff $h GET "/fields")
        $existing = $all | Where-Object { $_.code -eq $code }
        if ($existing) { return $existing | Select-Object -First 1 }
        throw
    }
}
$fidMerchant = (Ensure-Field "merchantId" "merchantId" "STRING").id
$fidAmount = (Ensure-Field "amount" "amount" "DOUBLE").id
$eventFields = @(Bff $h GET "/events/$EventCode/fields")
function Ensure-EventField($fieldId) {
    $ex = $eventFields | Where-Object { $_.fieldId -eq $fieldId }
    if ($ex) { return }
    try {
        Bff $h POST "/events/$EventCode/fields" @{ fieldId = $fieldId; purposes = @("COMPUTE", "DECISION"); derived = $false }
    } catch {
        Write-Host "  event field $fieldId skip (exists?)"
    }
}
Ensure-EventField $fidMerchant
Ensure-EventField $fidAmount

Write-Host "==> list dimension / library / entry"
$dims = @(Bff $h GET "/list-dimensions")
$dim = $dims | Where-Object { $_.code -eq "merchantId" }
if (-not $dim) {
    $dim = Bff $h POST "/list-dimensions" @{ code = "merchantId"; name = "merchantId"; fuzzyEnabled = $false; updatedBy = "admin" }
}
$libs = @(Bff $h GET "/list-libraries")
$lib = $libs | Where-Object { $_.code -eq "B2B_RECV_BLACK" }
if (-not $lib) {
    $lib = Bff $h POST "/list-libraries" @{ code = "B2B_RECV_BLACK"; name = "B2B Blacklist"; description = "B2B recv demo" }
}
$entries = @(Bff $h GET "/list-entries?libraryId=$($lib.id)")
if (-not ($entries | Where-Object { $_.dimensionValue -eq "M_BLACK_001" })) {
    Bff $h POST "/list-entries" @{
        libraryId = $lib.id
        dimensionCode = "merchantId"
        dimensionValue = "M_BLACK_001"
        extraAttrs = @{}
    }
}

Write-Host "==> gateway lists API"
try {
    Screening $h POST "/lists" @{
        listType = "BLACK"
        dimension = "merchantId"
        dimensionValue = "M_BLACK_001"
        reason = "B2B demo blacklist"
    }
} catch {
    Write-Host "  lists entry may exist"
}

Write-Host "==> indicator group + definition"
$groups = @(Bff $h GET "/indicator-groups")
$ig = $groups | Where-Object { $_.id -ne $null -and $_.name -eq "B2B_RECV_IND" }
if (-not $ig) {
    $ig = Bff $h POST "/indicator-groups" @{
        name = "B2B_RECV_IND"
        orgName = "HQ"
        eventTypeCodes = @($EventCode)
        description = "B2B recv demo indicators"
    }
}
$defs = @(Bff $h GET "/indicator-definitions?groupId=$($ig.id)")
$ind = $defs | Where-Object { $_.refName -eq "b2b_daily_amt" }
if (-not $ind) {
    $ind = Bff $h POST "/indicator-definitions" @{
        groupId = $ig.id
        refName = "b2b_daily_amt"
        name = "B2B daily amount"
        eventTypeCodes = @($EventCode)
        dimensions = @("merchantId")
        windowDays = 1
        sliceGranularity = "DAY"
        accScript = "current + amount"
        defaultValueStrategy = "ZERO"
        templateType = "AMOUNT_SUM"
        templateConfig = @{}
    }
}
if ($ind.status -ne "ONLINE") {
    $ind = Bff $h PUT "/indicator-definitions/$($ind.id)/online" @{}
}

Write-Host "==> AI fraud score indicator definition"
$aiInd = $defs | Where-Object { $_.refName -eq "ai_fraud_score" }
if (-not $aiInd) {
    $aiInd = Bff $h POST "/indicator-definitions" @{
        groupId = $ig.id
        refName = "ai_fraud_score"
        name = "AI fraud score"
        eventTypeCodes = @($EventCode)
        dimensions = @("merchantId")
        windowDays = 1
        sliceGranularity = "DAY"
        accScript = "value"
        defaultValueStrategy = "ZERO"
        templateType = "CUSTOM"
        templateConfig = @{}
    }
}
if ($aiInd.status -ne "ONLINE") {
    Bff $h PUT "/indicator-definitions/$($aiInd.id)/online" @{}
}

Write-Host "==> rule package + rules"
$h = Login
$pkgs = @(Bff $h GET "/rule-packages?eventCode=$EventCode")
$pkg = $pkgs | Where-Object { $_.code -eq "PKG_B2B_HIT" } | Select-Object -First 1
if (-not $pkg) {
    $pkg = Bff $h POST "/rule-packages" @{
        code = "PKG_B2B_HIT"
        name = "B2B Hit Rules"
        triggerMode = "HIT"
        eventTypeCodes = @($EventCode)
    }
}
$rules = @(Bff $h GET "/rule-packages/$($pkg.id)/rules")

function Ensure-Rule($code, $name, $priority, $riskLevel, $condition) {
    $h = Login
    try {
        $pkgRules = @(Bff $h GET "/rule-packages/$($pkg.id)/rules")
    } catch {
        $h = Login
        $pkgRules = @(Bff $h GET "/rule-packages/$($pkg.id)/rules")
    }
    $existing = $pkgRules | Where-Object { $_.code -eq $code } | Select-Object -First 1
    $r = $null
    try {
        if ($null -ne $existing -and $null -ne $existing.id) {
            $r = Bff $h GET "/rules-v2/$($existing.id)"
        } else {
            $created = Bff $h POST "/rules-v2" @{
                code = $code
                name = $name
                rulePackageId = $pkg.id
                ruleKind = "HIT"
                eventTypeCode = $EventCode
                riskLevelCode = $riskLevel
                priority = $priority
                condition = $condition
            }
            $r = Bff $h GET "/rules-v2/$($created.id)"
        }
    } catch {
        Write-Host "  rule $code error: $($_.Exception.Message)"
        return $null
    }
    if ($null -eq $r -or $null -eq $r.id) {
        Write-Host "  rule $code setup failed"
        return $null
    }
    if ($r.status -ne "ONLINE") {
        try {
            Bff $h PUT "/rules-v2/$($r.id)/status" @{ status = "ONLINE" }
        } catch {
            Write-Host "  online rule $($r.code): $($_.Exception.Message)"
        }
    }
  try {
    Bff $h POST "/rule-packages/$($pkg.id)/rules" @{ ruleV2Id = $r.id; priority = $priority }
  } catch {
    Write-Host "  associate $($r.code): $($_.Exception.Message)"
  }
    return $r
}

$ruleBig = Ensure-Rule "RULE_B2B_BIGAMT" "B2B large amount" 100 "HIGH" @{
    op = "LEAF"
    left = @{ source = "FIELD"; ref = "amount"; dataType = "NUMBER" }
    operator = "GT"
    right = @{ kind = "CONST"; value = 100000 }
}
$ruleDaily = Ensure-Rule "RULE_B2B_DAILY_IND" "B2B daily indicator threshold" 200 "MEDIUM" @{
    op = "LEAF"
    left = @{ source = "INDICATOR"; ref = "b2b_daily_amt"; dataType = "NUMBER" }
    operator = "GT"
    right = @{ kind = "CONST"; value = 150000 }
}
$ruleAi = Ensure-Rule "RULE_B2B_AI_FRAUD" "AI fraud score high" 300 "MEDIUM" @{
    op = "LEAF"
    left = @{ source = "INDICATOR"; ref = "ai_fraud_score"; dataType = "NUMBER" }
    operator = "GT"
    right = @{ kind = "CONST"; value = 0.8 }
}

Write-Host "==> link rules to package + enable package"
try {
    Invoke-BffApi -Headers $h -BffBase $Bff -Method PUT -Path "/rule-packages/$($pkg.id)/status?enabled=true"
} catch {
    try {
        Invoke-RuleConfigApi -RuleConfigBase $RuleConfig -Method PUT -Path "/rule-packages/$($pkg.id)/status?enabled=true"
    } catch {
        Write-Host "  enable package: $($_.Exception.Message)"
    }
}

Write-Host "==> decision flow (list -> rules -> gateway -> END branches)"
$flows = @(Bff $h GET "/decision-flows?eventTypeCode=$EventCode")
$flow = $flows | Where-Object { $_.name -eq "B2B_RECV_FLOW" }
if (-not $flow) {
    $flow = Bff $h POST "/decision-flows" @{ name = "B2B_RECV_FLOW"; eventTypeCode = $EventCode }
}
$pkgId = $pkg.id
$flowBody = @{
    name = "B2B_RECV_FLOW"
    startNodeId = "start"
    status = "ENABLED"
    nodes = @(
        @{ nodeId = "start"; type = "START"; refType = $null; refId = $null; config = $null }
        @{ nodeId = "list_chk"; type = "LIST_CHECK"; refType = $null; refId = $null; config = $null }
        @{ nodeId = "rp_b2b"; type = "RULE_PACKAGE"; refType = "RULE_PACKAGE"; refId = $pkgId; config = $null }
        @{ nodeId = "gw"; type = "CONDITION_GATEWAY"; refType = $null; refId = $null; config = $null }
        @{ nodeId = "end_reject"; type = "END"; refType = $null; refId = $null; config = '{"endDecision":"AUTO_REJECT"}' }
        @{ nodeId = "end_review"; type = "END"; refType = $null; refId = $null; config = '{"endDecision":"MANUAL_REVIEW"}' }
        @{ nodeId = "end_pass"; type = "END"; refType = $null; refId = $null; config = '{"endDecision":"AUTO_PASS"}' }
    )
    edges = @(
        @{ from = "start"; to = "list_chk"; condition = $null; isDefault = $false }
        @{ from = "list_chk"; to = "rp_b2b"; condition = $null; isDefault = $false }
        @{ from = "rp_b2b"; to = "gw"; condition = $null; isDefault = $false }
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
    Write-Host "  flow version $latestVer published ONLINE (BFF)"
} catch {
    try {
        $h = Login
        Invoke-RuleConfigApi -RuleConfigBase $RuleConfig -Method POST `
            -Path "/decision-flows/$($flow.id)/versions/${latestVer}:online" -Headers $h
        Write-Host "  flow version $latestVer published ONLINE (rule-config 8082)"
    } catch {
        Write-Host "  online version failed: $($_.Exception.Message)"
    }
}

Write-Host "==> gateway smoke tests"
function Write-Indicator($refName, $merchantId, $value) {
    Invoke-IndicatorWrite -IndicatorBase $IndicatorStore -RefName $refName -DimensionKey $merchantId -Value $value | Out-Null
}
function Test-Event($label, $ctx) {
    $r = Invoke-GatewayEvent -GatewayBase $Gateway -EventTypeCode $EventCode -Context $ctx
    Write-Host "  [$label] decision=$($r.decision) eventId=$($r.eventId)"
}
Test-Event "ok-small" @{ merchantId = "M_OK_001"; amount = 5000 }
Test-Event "blacklist" @{ merchantId = "M_BLACK_001"; amount = 5000 }
Test-Event "large-amt" @{ merchantId = "M_OK_002"; amount = 200000 }
Write-Indicator "b2b_daily_amt" "M_IND_001" 160000
Test-Event "daily-ind" @{ merchantId = "M_IND_001"; amount = 5000 }
Write-Indicator "ai_fraud_score" "M_AI_001" 0.92
Test-Event "ai-fraud" @{ merchantId = "M_AI_001"; amount = 5000 }

Write-Host "DONE - flow id=$($flow.id) pkg id=$($pkg.id) rules: big/daily/ai indicator group id=$($ig.id)"
