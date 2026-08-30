# 测试/造数共用：UTF-8 + BFF 调用（禁止业务数据直写 SQL）
$ErrorActionPreference = "Continue"

if ($PSVersionTable.PSVersion.Major -ge 6) {
    $PSDefaultParameterValues['*:Encoding'] = 'utf8'
}
try { chcp 65001 | Out-Null } catch { }
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$OutputEncoding = [System.Text.UTF8Encoding]::new()

function Test-ApiLogin {
    param([string]$BffBase = "http://localhost:8080/bff/api/v1")
    $r = Invoke-RestMethod -Uri "$BffBase/auth/login" -Method POST `
        -ContentType "application/json; charset=utf-8" `
        -Body '{"username":"admin","password":"admin123"}'
    return @{ Authorization = "Bearer $($r.token)" }
}

function Invoke-BffApi {
    param(
        [hashtable]$Headers,
        [string]$BffBase = "http://localhost:8080/bff/api/v1",
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )
    $uri = "$BffBase$Path"
    $jsonHeaders = @{ Authorization = $Headers.Authorization }
    if ($Body -ne $null) {
        $json = $Body | ConvertTo-Json -Depth 25
        return Invoke-RestMethod -Uri $uri -Method $Method -Headers $jsonHeaders `
            -ContentType "application/json; charset=utf-8" -Body $json
    }
    return Invoke-RestMethod -Uri $uri -Method $Method -Headers $jsonHeaders
}

function Invoke-GatewayEvent {
    param(
        [string]$GatewayBase = "http://localhost:8081/api/v1",
        [string]$EventTypeCode,
        [hashtable]$Context
    )
    $body = @{ eventTypeCode = $EventTypeCode; context = $Context } | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Uri "$GatewayBase/risk-events" -Method POST `
        -ContentType "application/json; charset=utf-8" -Body $body
}

function Invoke-IndicatorWrite {
    param(
        [string]$IndicatorBase = "http://localhost:8084/api/v1",
        [string]$RefName,
        [string]$DimensionKey,
        [double]$Value
    )
    $body = @{
        dimensionKey = $DimensionKey
        value        = $Value
        granularity  = "DAY"
        source       = "API_SEED"
    } | ConvertTo-Json
    return Invoke-RestMethod -Uri "$IndicatorBase/indicators/$RefName" -Method POST `
        -ContentType "application/json; charset=utf-8" -Body $body
}

function Invoke-RuleConfigApi {
    param(
        [string]$RuleConfigBase = "http://localhost:8082/api/v1",
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = $null
    )
    $uri = "$RuleConfigBase$Path"
  $reqHeaders = @{}
  if ($Headers -and $Headers.Authorization) {
    $reqHeaders.Authorization = $Headers.Authorization
  }
    if ($Body -ne $null) {
        $json = $Body | ConvertTo-Json -Depth 25
        return Invoke-RestMethod -Uri $uri -Method $Method -Headers $reqHeaders `
            -ContentType "application/json; charset=utf-8" -Body $json
    }
    return Invoke-RestMethod -Uri $uri -Method $Method -Headers $reqHeaders
}

function Test-EventTypeVisibleToGateway {
    param(
        [string]$RuleConfigBase = "http://localhost:8082/api/v1",
        [string]$EventCode
    )
    $list = @(Invoke-RestMethod -Uri "$RuleConfigBase/event-types")
    $hit = $list | Where-Object { $_.code -eq $EventCode -and $_.status -eq "ENABLED" } | Select-Object -First 1
    return $null -ne $hit
}
