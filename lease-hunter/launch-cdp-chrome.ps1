$ErrorActionPreference = "SilentlyContinue"

Write-Host "=== STABLE CHROME CDP LAUNCHER ==="

$chromePath = "C:\Program Files\Google\Chrome\Application\chrome.exe"
$profileDir = "C:\Development\Apps\Lease Hunter\chrome-debug-stable"

# Clean profile
Remove-Item $profileDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Launching Chrome with stability flags..."
$proc = Start-Process -FilePath $chromePath -ArgumentList @(
    "--remote-debugging-port=9222",
    "--remote-allow-origins=*",
    "--user-data-dir=$profileDir",
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-background-networking",
    "--disable-client-side-phishing-detection",
    "--disable-default-apps",
    "--disable-hang-monitor",
    "--disable-popup-blocking",
    "--disable-prompt-on-repost",
    "--disable-sync",
    "--metrics-recording-only",
    "--safebrowsing-disable-auto-update"
) -PassThru

Write-Host "Chrome PID: $($proc.Id)"

# Wait and keep checking
for ($i = 1; $i -le 20; $i++) {
    Start-Sleep 1
    
    # Check if Chrome is still running
    $running = Get-Process -Id $proc.Id -ErrorAction SilentlyContinue
    if (-not $running) {
        Write-Host "  Chrome process DIED at attempt $i" -ForegroundColor Red
        # Check if it restarted under a different PID
        $chromeProcs = Get-Process chrome -ErrorAction SilentlyContinue
        Write-Host "  Total chrome processes: $($chromeProcs.Count)"
        continue
    }
    
    try {
        $response = Invoke-WebRequest -Uri 'http://127.0.0.1:9222/json/version' -UseBasicParsing -TimeoutSec 2
        Write-Host "  [Attempt $i] CDP ACTIVE!" -ForegroundColor Green
        Write-Host $response.Content
        
        # Keep checking for 5 more seconds to confirm stability
        for ($j = 1; $j -le 5; $j++) {
            Start-Sleep 1
            try {
                Invoke-WebRequest -Uri 'http://127.0.0.1:9222/json/version' -UseBasicParsing -TimeoutSec 2 | Out-Null
                Write-Host "  [Stability check $j/5] STILL ACTIVE" -ForegroundColor Green
            } catch {
                Write-Host "  [Stability check $j/5] DIED" -ForegroundColor Red
            }
        }
        
        # Final check
        try {
            Invoke-WebRequest -Uri 'http://127.0.0.1:9222/json/version' -UseBasicParsing -TimeoutSec 2 | Out-Null
            Write-Host ""
            Write-Host "CHROME CDP IS STABLE AND READY" -ForegroundColor Green
            exit 0
        } catch {
            Write-Host "Chrome died during stability checks" -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "  [Attempt $i] Port not ready yet..."
    }
}

Write-Host "FAILED after 20 attempts" -ForegroundColor Red
exit 1
