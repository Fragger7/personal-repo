$ErrorActionPreference = "SilentlyContinue"

Write-Host "=== SIMPLE CHROME CDP LAUNCHER ==="
Stop-Process -Name chrome -Force
Start-Sleep 2

$chromePath = "C:\Program Files\Google\Chrome\Application\chrome.exe"
$profileDir = "C:\Development\Apps\Lease Hunter\chrome-debug-simple"

Remove-Item $profileDir -Recurse -Force

Start-Process -FilePath $chromePath -ArgumentList @(
    "--remote-debugging-port=9222",
    "--remote-allow-origins=*",
    "--user-data-dir=$profileDir",
    "--no-first-run",
    "--no-default-browser-check",
    "https://www.cargurus.com"
)
Start-Sleep 5
try {
    $res = Invoke-WebRequest -Uri 'http://127.0.0.1:9222/json/version' -UseBasicParsing -TimeoutSec 2
    Write-Host "CDP READY: $($res.Content)"
} catch {
    Write-Host "CDP FAILED"
}
