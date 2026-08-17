@echo off
cd /d "%~dp0"
echo Starting Lease Hunter Dashboard...
echo ====================================

REM Start the React+Express development server in the background
echo Launching the server on port 3000...
start cmd /k "npm run dev"

echo Waiting 5 seconds for the server to spin up...
timeout /t 5 /nobreak >nul

REM Open the default browser to the app's local port
start http://localhost:3000
