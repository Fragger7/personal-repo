@echo off
REM ===================================================================
REM  Starts a Chrome instance with remote debugging so the tracker can
REM  ATTACH to it rather than launching a browser under automation
REM  control (which Akamai detects and blocks on tesla.com).
REM
REM  Uses a DEDICATED profile folder. Reason: Chrome often keeps
REM  background processes alive, and if any exist the --remote-debugging
REM  flag is silently ignored and the port never opens (that produces
REM  "ECONNREFUSED 9222"). A separate profile guarantees a real new
REM  instance regardless of what else Chrome is doing.
REM
REM  This is still YOUR Chrome, started by YOU — not automation-launched.
REM  First run: browse tesla.com a bit in this window so the profile
REM  picks up normal cookies.
REM
REM  Order:
REM    1. Run this file (leave the browser open)
REM    2. Run start-tracker.bat
REM ===================================================================

set CHROME="C:\Program Files\Google\Chrome\Application\chrome.exe"
if not exist %CHROME% set CHROME="C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
if not exist %CHROME% (
    echo Could not find chrome.exe in the usual locations.
    echo Edit this file and set CHROME to your Chrome path.
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
  https://www.tesla.com/inventory/used/ms

echo.
echo Waiting for the debug port to come up...
timeout /t 5 /nobreak >NUL

powershell -NoProfile -Command ^
  "try { $r = Invoke-WebRequest -Uri 'http://127.0.0.1:9222/json/version' -UseBasicParsing -TimeoutSec 5; Write-Host ''; Write-Host '  SUCCESS - debug port is open.' -ForegroundColor Green; Write-Host ('  ' + ($r.Content | ConvertFrom-Json).Browser) } catch { Write-Host ''; Write-Host '  FAILED - port 9222 is not responding.' -ForegroundColor Red; Write-Host '  Close ALL Chrome windows and try again.' }"

echo.
echo If SUCCESS above: leave this browser open and run start-tracker.bat
echo If the Tesla page loaded normally, you are past Akamai.
echo.
pause
