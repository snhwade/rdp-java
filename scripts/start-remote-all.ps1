# 以 remote 模式启动全部微服务（各端口独立进程）。
# 用法：.\scripts\start-remote-all.ps1
# 停止：.\scripts\stop-all-services.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\song\environment\java\jdk-17.0.12" }
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:RDP_INTEGRATION_MODE = "remote"
$env:SPRING_PROFILES_ACTIVE = "local"

function Stop-Port([int]$Port) {
    $pids = netstat -ano | findstr ":$Port" | findstr LISTENING | ForEach-Object {
        ($_ -split '\s+')[-1]
    } | Sort-Object -Unique
    foreach ($p in $pids) {
        if ($p -and $p -ne '0') {
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
            Write-Host "stopped port $Port pid $p"
        }
    }
}

Write-Host "==> Stopping existing services..."
foreach ($p in @(8080, 8081, 8082, 8083, 8084, 8085, 8086, 5173, 8000)) {
    Stop-Port $p
}
Start-Sleep -Seconds 2

function Start-ServiceModule([string]$Name, [string]$Module, [hashtable]$ExtraEnv = @{}) {
    $dir = Join-Path $Root $Module
    $envLines = @(
        "`$env:JAVA_HOME = '$env:JAVA_HOME'"
        "`$env:Path = `"`$env:JAVA_HOME\bin;`" + `$env:Path"
        "`$env:RDP_INTEGRATION_MODE = 'remote'"
        "`$env:SPRING_PROFILES_ACTIVE = 'local'"
    )
    foreach ($k in $ExtraEnv.Keys) {
        $envLines += "`$env:$k = '$($ExtraEnv[$k])'"
    }
    $envLines += "Set-Location '$dir'"
    $envLines += "mvn -q spring-boot:run '-Dmaven.test.skip=true'"
    $cmd = $envLines -join "; "
    Start-Process powershell -ArgumentList @("-NoExit", "-Command", $cmd) -WindowStyle Minimized
    Write-Host "started $Name ($Module)"
    Start-Sleep -Seconds 8
}

Write-Host "==> Starting microservices (remote mode)..."
# 1. 配置中心 / Flyway 优先
Start-ServiceModule "rule-config :8082" "rule-config-service"
Start-Sleep -Seconds 12

Start-ServiceModule "screening :8085" "screening-service"
Start-ServiceModule "merchant-rating :8086" "merchant-rating-service"
Start-ServiceModule "rule-decision-engine :8083" "rule-decision-engine"
Start-ServiceModule "indicator-store :8084" "indicator-store-service"
Start-ServiceModule "decision-gateway :8081" "decision-gateway"
Start-ServiceModule "admin-bff :8080" "admin-bff" @{ SPRING_FLYWAY_ENABLED = "false" }

Write-Host "==> Starting admin-console :5173..."
Start-Process powershell -ArgumentList @(
    "-NoExit", "-Command",
    "Set-Location '$Root\admin-console'; npm run dev"
) -WindowStyle Minimized

Write-Host "==> Starting ai-training-service :8000..."
$AiDir = Join-Path (Split-Path -Parent $Root) "ai-training-service"
if (-not (Test-Path $AiDir)) {
    $AiDir = Join-Path $Root "..\ai-training-service"
}
$AiDir = (Resolve-Path $AiDir -ErrorAction SilentlyContinue)
if ($AiDir) {
    $aiCmd = @(
        "`$env:MYSQL_URL = 'mysql+pymysql://root:root@localhost:3306/risk_decision_platform'"
        "`$env:INDICATOR_STORE_URL = 'http://localhost:8084'"
        "Set-Location '$AiDir'"
        "python -m uvicorn app.main:app --host 0.0.0.0 --port 8000"
    ) -join "; "
    Start-Process powershell -ArgumentList @("-NoExit", "-Command", $aiCmd) -WindowStyle Minimized
    Write-Host "started ai-training-service :8000"
} else {
    Write-Host "WARN: ai-training-service directory not found; skip port 8000"
}

Write-Host ""
Write-Host "Remote stack starting. Ports:"
Write-Host "  8082 rule-config | 8083 engine | 8084 indicator | 8085 screening | 8086 rating"
Write-Host "  8081 gateway     | 8080 admin-bff | 5173 console | 8000 ai-training"
Write-Host "Login: http://localhost:5173  (admin / admin123)"
Write-Host "JDK: $env:JAVA_HOME"
