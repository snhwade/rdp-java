# Kafka + Flink 指标累计链路 E2E（txn_cnt_1d / txn_amount_1d）
# 拓扑：Kafka order-final-state -> Flink (Docker) -> Redis -> indicator-store GET
#
# 前置：
#   - deploy/docker-compose.yml（Kafka + Flink 集群）、Redis、indicator-store 8084
#   - Flink 作业：deploy/submit-flink-job.ps1 -Latest -Group flink-acc-e2e
#   - indicator.accumulate.mode=flink（默认，与 INDICATOR_ACCUMULATE_MODE 一致）
#   - 消息通过 docker cp 写入 Kafka（避免 powershell -File 下 stdin 管道失效）
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "test-api-lib.ps1")

$KafkaTopic = "order-final-state"
$FlinkConsumerGroup = "flink-acc-e2e"
$RunTag = Get-Date -Format "yyyyMMddHHmmss"
$MerchantId = "M_FLINK_$RunTag"
$IndicatorBase = "http://localhost:8084/api/v1"
$DeployScript = Join-Path $PSScriptRoot "..\deploy\submit-flink-job.ps1"

$Results = [System.Collections.Generic.List[object]]::new()

function Record($id, $name, $pass, $detail) {
    $Results.Add([pscustomobject]@{ Id = $id; Case = $name; Pass = $pass; Detail = $detail })
    $mark = if ($pass) { "PASS" } else { "FAIL" }
    Write-Host "[$mark] $id $name - $detail"
}

function Publish-KafkaJson($json) {
    $tmp = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($tmp, $json.Trim() + "`n", [System.Text.UTF8Encoding]::new($false))
        docker cp $tmp rdp-kafka:/tmp/kafka-msg.txt 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "docker cp failed" }
        docker exec rdp-kafka bash -c `
            "kafka-console-producer.sh --bootstrap-server localhost:9092 --topic $KafkaTopic < /tmp/kafka-msg.txt" `
            2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "kafka produce failed" }
    } finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

function Publish-Order($orderId, $amount) {
    $epoch = [long]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
    $json = '{"orderId":"' + $orderId + '","eventTypeCode":"EVT_PAY_RESULT","eventEpochMs":' + $epoch +
        ',"fields":{"merchantId":"' + $MerchantId + '","amount":' + $amount + '}}'
    Publish-KafkaJson $json
}

function Read-Indicator($refName) {
    $uri = "$IndicatorBase/indicators/$refName" +
        "?dimensionKey=$MerchantId&windowDays=1&granularity=DAY"
    return Invoke-RestMethod -Uri $uri -Method GET -TimeoutSec 15
}

function Wait-IndicatorForMerchant($refName, $merchantId, $minValue, $timeoutSec = 90) {
    $uri = "$IndicatorBase/indicators/$refName" +
        "?dimensionKey=$merchantId&windowDays=1&granularity=DAY"
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-RestMethod -Uri $uri -Method GET -TimeoutSec 15
            if (-not $r.missing -and $r.source -eq "REDIS" -and $r.value -ge $minValue) {
                return $true
            }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-Indicators($expectCnt, $expectAmt, $timeoutSec = 90) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $cnt = Read-Indicator "txn_cnt_1d"
            $amt = Read-Indicator "txn_amount_1d"
            if ($cnt.value -eq $expectCnt -and $amt.value -eq $expectAmt -and -not $cnt.missing -and $cnt.source -eq "REDIS") {
                return @{ Cnt = $cnt; Amt = $amt; Ok = $true }
            }
        } catch { }
        Start-Sleep -Seconds 2
    }
    try {
        $cnt = Read-Indicator "txn_cnt_1d"
        $amt = Read-Indicator "txn_amount_1d"
        return @{ Cnt = $cnt; Amt = $amt; Ok = $false }
    } catch {
        return @{ Cnt = $null; Amt = $null; Ok = $false; Err = $_.Exception.Message }
    }
}

function Get-RunningFlinkJobIds {
    $jobsJson = docker exec rdp-flink-jobmanager curl -fs http://localhost:8081/jobs 2>$null
    if (-not $jobsJson) { return @() }
    return [regex]::Matches($jobsJson, '\{"id":"([a-f0-9]+)","status":"RUNNING"\}') |
        ForEach-Object { $_.Groups[1].Value }
}

function Wait-ConsumerReady($timeoutSec = 180, $freshJob = $false) {
    if ($freshJob) {
        Write-Host "  fresh job grace 60s for task deployment..."
        Start-Sleep -Seconds 60
    }
    $probeMerchant = "M_FLINK_PROBE_$RunTag"
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        $probeOrderId = "probe-$RunTag-$attempt"
        try {
            $epoch = [long]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
            $json = '{"orderId":"' + $probeOrderId + '","eventTypeCode":"EVT_PAY_RESULT","eventEpochMs":' +
                $epoch + ',"fields":{"merchantId":"' + $probeMerchant + '","amount":1}}'
            Publish-KafkaJson $json
        } catch { }
        if (Wait-IndicatorForMerchant "txn_cnt_1d" $probeMerchant 1 35) {
            return $true
        }
    }
    return $false
}

Write-Host "========================================"
Write-Host "  E2E - Kafka + Flink Indicator Accumulation"
Write-Host "  merchantId=$MerchantId"
Write-Host "========================================"

Write-Host "`n==> Phase 0: infrastructure"
try {
    docker exec rdp-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list 2>&1 | Out-Null
    Record "F00" "Kafka reachable" ($LASTEXITCODE -eq 0) "topic=$KafkaTopic"
} catch {
    Record "F00" "Kafka reachable" $false $_.Exception.Message
}

try {
    $jobs = docker exec rdp-flink-jobmanager curl -fs http://localhost:8081/overview 2>&1
    Record "F01" "Flink cluster reachable" ($LASTEXITCODE -eq 0) "overview ok"
} catch {
    Record "F01" "Flink cluster reachable" $false $_.Exception.Message
}

function Cancel-RunningFlinkJobs {
    foreach ($jid in (Get-RunningFlinkJobIds)) {
        cmd /c "docker exec rdp-flink-jobmanager flink cancel $jid 1>nul 2>nul"
    }
    Start-Sleep -Seconds 10
}

Write-Host "`n==> Phase 1: Flink job + Kafka consumer (group=$FlinkConsumerGroup)"
$flinkOk = $false
$consumerReady = $false
$needSubmit = $false
try {
    $running = @(Get-RunningFlinkJobIds)
    if ($running.Count -gt 1) {
        Write-Host "  cancel $($running.Count) duplicate RUNNING jobs..."
        Cancel-RunningFlinkJobs
        $running = @()
    }
    if ($running.Count -eq 0) {
        Write-Host "  submitting Flink job (stable group, offsets latest)..."
        cmd /c "powershell -NoProfile -ExecutionPolicy Bypass -File `"$DeployScript`" -Latest -Group $FlinkConsumerGroup 1>nul 2>nul"
        Start-Sleep -Seconds 10
        $running = @(Get-RunningFlinkJobIds)
        $needSubmit = $true
    } else {
        Write-Host "  reuse RUNNING job $($running[0])"
    }
    $flinkOk = $running.Count -eq 1
    Record "F02" "Exactly one Flink job RUNNING" $flinkOk "running=$($running.Count) jid=$($running[0])"

    if ($flinkOk) {
        Write-Host "  probe publish to confirm consumer ready (offsets latest)..."
        $consumerReady = Wait-ConsumerReady 180 $needSubmit
        Record "F02b" "Flink consumer ready (latest offset)" $consumerReady `
            $(if ($consumerReady) { "probe merchant REDIS ok" } else { "probe timeout 180s" })
    }
} catch {
    Record "F02" "Exactly one Flink job RUNNING" $false $_.Exception.Message
}

Write-Host "`n==> Phase 2: publish 3 orders to Kafka"
$orders = @(
    @{ Id = "ord-$RunTag-1"; Amount = 1500 },
    @{ Id = "ord-$RunTag-2"; Amount = 2500 },
    @{ Id = "ord-$RunTag-3"; Amount = 3500 }
)
$expectedCnt = 3
$expectedAmt = 7500
if (-not $consumerReady) {
    Record "F03" "Kafka produce (skipped)" $false "consumer not ready"
} else {
    foreach ($o in $orders) {
        try {
            Publish-Order $o.Id $o.Amount
            Record "F03-$($o.Id)" "Kafka produce" $true "amount=$($o.Amount)"
        } catch {
            Record "F03-$($o.Id)" "Kafka produce" $false $_.Exception.Message
        }
    }
}

Write-Host "`n==> Phase 3: Flink -> Redis -> indicator-store read"
$wait = Wait-Indicators $expectedCnt $expectedAmt
if ($wait.Cnt) {
    Record "F04" "txn_cnt_1d = $expectedCnt" ($wait.Cnt.value -eq $expectedCnt) `
        "value=$($wait.Cnt.value) source=$($wait.Cnt.source)"
    Record "F05" "txn_amount_1d = $expectedAmt" ($wait.Amt.value -eq $expectedAmt) `
        "value=$($wait.Amt.value) source=$($wait.Amt.source)"
} else {
    $errDetail = if ($wait.Err) { $wait.Err } else { "timeout cnt=$($wait.Cnt.value) amt=$($wait.Amt.value)" }
    Record "F04" "txn_cnt_1d = $expectedCnt" $false $errDetail
    Record "F05" "txn_amount_1d = $expectedAmt" $false $errDetail
}

Write-Host "`n==> Phase 4: idempotency"
try {
    Publish-Order "ord-$RunTag-1" 1500
    $wait2 = Wait-Indicators $expectedCnt $expectedAmt 20
    Record "F06" "Duplicate order no double count" $wait2.Ok `
        "cnt=$($wait2.Cnt.value) amt=$($wait2.Amt.value)"
} catch {
    Record "F06" "Duplicate order no double count" $false $_.Exception.Message
}

Write-Host "`n==> Phase 5: Redis SliceKey format"
try {
    $raw = redis-cli keys "ind:txn_cnt_1d:${MerchantId}:DAY:*" 2>$null
    $keys = @($raw | Where-Object { $_ -and $_.Trim() -ne "" })
    $keyOk = ($keys.Count -ge 1) -and ($keys[0] -match [regex]::Escape($MerchantId))
    Record "F07" "Redis key ind:ref:dim:DAY:ts" $keyOk ($keys -join ", ")
} catch {
    Record "F07" "Redis key format" $false $_.Exception.Message
}

Write-Host "`n========================================"
Write-Host "  Flink Kafka Test Summary"
Write-Host "========================================"
$passed = ($Results | Where-Object { $_.Pass }).Count
$total = $Results.Count
$Results | ForEach-Object {
    [pscustomobject]@{ Id = $_.Id; Case = $_.Case; Pass = $_.Pass; Detail = $_.Detail }
} | Format-Table -AutoSize
Write-Host "Total: $passed / $total passed"
Write-Host "Flink UI: http://localhost:8088"
if ($passed -lt $total) { exit 1 }
