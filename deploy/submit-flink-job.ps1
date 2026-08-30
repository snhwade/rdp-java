# 提交指标累计 Flink 作业到 Docker Flink 集群（方案B）。
# 用法：
#   powershell -ExecutionPolicy Bypass -File deploy/submit-flink-job.ps1
#   powershell -ExecutionPolicy Bypass -File deploy/submit-flink-job.ps1 -Latest
param(
    [switch]$Latest,
    [string]$Group = ""
)
$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$env:PATH += ";C:\Program Files\Docker\Docker\resources\bin"

$jar = Join-Path $RepoRoot "indicator-accumulation\target\indicator-accumulation-1.0.0-SNAPSHOT-shaded.jar"
if (-not (Test-Path $jar)) { throw "fat jar 不存在，请先 mvn -pl indicator-accumulation package: $jar" }

Write-Output "拷贝作业 jar 到 JobManager..."
docker cp $jar rdp-flink-jobmanager:/tmp/indicator-job.jar

$grp = if ($Group) { $Group } else { "flink-acc-" + (Get-Date -Format "yyyyMMddHHmmss") }
$offsetVal = if ($Latest) { "latest" } else { "earliest" }
Write-Output "提交作业到 Flink 集群 (group=$grp offsets=$offsetVal)..."
docker exec rdp-flink-jobmanager flink run -d /tmp/indicator-job.jar `
    --kafka rdp-kafka:29092 --sink-topic indicator-slice-updates `
    --rule-config http://host.docker.internal:8082 `
    --definitions-refresh-ms 30000 `
    --group $grp --offsets $offsetVal

Write-Output "已提交。Web UI: http://localhost:8088 group=$grp"
