# 停止本地风控平台常用端口上的进程。
foreach ($p in @(8080, 8081, 8082, 8083, 8084, 8085, 8086, 5173, 8000)) {
    $pids = netstat -ano | findstr ":$p" | findstr LISTENING | ForEach-Object { ($_ -split '\s+')[-1] } | Sort-Object -Unique
    foreach ($procId in $pids) {
        if ($procId -and $procId -ne '0') {
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            Write-Host "stopped port $p pid $procId"
        }
    }
}
