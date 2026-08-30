# Merchant rating demo seed via BFF HTTP API
$ErrorActionPreference = "Continue"
. (Join-Path $PSScriptRoot "test-api-lib.ps1")

$Bff = "http://localhost:8080/bff/api/v1"

Write-Host "==> login"
$h = Test-ApiLogin -BffBase $Bff

# weights: industry=30, region=30, history=40; factor values 0..1
$merchants = @(
    @{ id = "MCH-DEMO-001"; factors = @{ industry = 0.2; region = 0.2; history = 0.1 } }
    @{ id = "MCH-DEMO-002"; factors = @{ industry = 0.5; region = 0.4; history = 0.35 } }
    @{ id = "MCH-DEMO-003"; factors = @{ industry = 0.7; region = 0.65; history = 0.6 } }
    @{ id = "MCH-DEMO-004"; factors = @{ industry = 0.95; region = 0.9; history = 0.85 } }
    @{ id = "MCH-DEMO-005"; factors = @{ industry = 1.0; region = 1.0; history = 1.0 } }
)

Write-Host "==> trigger merchant ratings"
foreach ($m in $merchants) {
    try {
        $r = Invoke-BffApi -Headers $h -BffBase $Bff -Method POST -Path ("/merchants/{0}/rating" -f $m.id) -Body @{
            factors = $m.factors
        }
        Write-Host ("  {0} -> score={1} level={2} status={3}" -f $m.id, $r.score, $r.level, $r.status)
    } catch {
        Write-Host ("  FAIL {0}: {1}" -f $m.id, $_.Exception.Message) -ForegroundColor Red
    }
}

Write-Host "==> list merchant ratings"
try {
    $list = Invoke-BffApi -Headers $h -BffBase $Bff -Method GET -Path '/merchant-ratings?page=1&pageSize=20'
    Write-Host ("  total={0}" -f $list.total)
    foreach ($row in @($list.data)) {
        Write-Host ("    {0} | score={1} level={2} status={3} updatedAt={4}" -f `
            $row.merchantId, $row.score, $row.level, $row.status, $row.updatedAt)
    }
} catch {
    Write-Host ("  list FAIL: {0}" -f $_.Exception.Message) -ForegroundColor Red
    if ($_.Exception.Response) {
        $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        Write-Host $reader.ReadToEnd()
    }
}

Write-Host "==> done - open http://localhost:5173/merchant-rating"
