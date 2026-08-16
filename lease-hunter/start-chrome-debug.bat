@echo off
REM ===================================================================
REM  Starts Chrome with remote debugging on port 9222
REM  Allows Lease Hunter to ATTACH to a genuine Chrome instance
REM  bypassing Cloudflare/DataDome bot firewalls completely.
REM ===================================================================

set CHROME="C:\Program Files\Google\Chrome\Application\chrome.exe"
if not exist %CHROME% set CHROME="C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
if not exist %CHROME% (
    echo Could not find chrome.exe in standard locations.
    echo Please ensure Google Chrome is installed.
    pause
    exit /b 1
)

echo Starting Chrome with remote debugging on 127.0.0.1:9222 ...
start "" %CHROME% ^
  --remote-debugging-port=9222 ^
  --remote-allow-origins=* ^
  --user-data-dir="%~dp0chrome-debug-profile" ^
  --no-first-run ^
  --no-default-browser-check ^
  https://www.cargurus.com

echo.
echo Waiting for Chrome debug port 9222 to initialize...
timeout /t 3 /nobreak >NUL

powershell -NoProfile -Command ^
  "try { $r = Invoke-WebRequest -Uri 'http://127.0.0.1:9222/json/version' -UseBasicParsing -TimeoutSec 5; Write-Host ''; Write-Host '  SUCCESS - Chrome debug port 9222 is open!' -ForegroundColor Green; } catch { Write-Host ''; Write-Host '  FAILED - port 9222 is not responding.' -ForegroundColor Red; }"

echo.
