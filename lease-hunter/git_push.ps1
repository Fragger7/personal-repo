# git_push.ps1 - Automates local push of Lease Hunter files to the mono-repo folder.
param (
    [string]$CommitMessage = "Update Lease Hunter application and configs"
)

$repoUrl = "https://github.com/Fragger7/personal-repo.git"
$workspace = "C:\Development\Apps\Lease Hunter"
$tempDir = Join-Path $workspace "personal-repo-temp"
$gitBin = "C:\Program Files\Git\cmd\git.exe"

Write-Host "Cloning repository..." -ForegroundColor Cyan
& $gitBin clone $repoUrl $tempDir

if ($LASTEXITCODE -eq 0) {
    Write-Host "Copying files to repo folder..." -ForegroundColor Cyan
    $targetDir = Join-Path $tempDir "lease-hunter"
    $targetAgentsDir = Join-Path $targetDir ".agents"
    
    # Ensure folders exist
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    New-Item -ItemType Directory -Force -Path $targetAgentsDir | Out-Null
    
    # Copy files
    Copy-Item "$workspace\app.py" "$targetDir\app.py" -Force
    Copy-Item "$workspace\.gitignore" "$targetDir\.gitignore" -Force
    Copy-Item "$workspace\requirements.txt" "$targetDir\requirements.txt" -Force
    Copy-Item "$workspace\LEASE_HUNTER.md" "$targetDir\LEASE_HUNTER.md" -Force
    Copy-Item "$workspace\run.bat" "$targetDir\run.bat" -Force
    Copy-Item "$workspace\.agents\AGENTS.md" "$targetAgentsDir\AGENTS.md" -Force
    Copy-Item "$workspace\flatten_context.py" "$targetDir\flatten_context.py" -Force
    if (Test-Path "$workspace\git_push.ps1") {
        Copy-Item "$workspace\git_push.ps1" "$targetDir\git_push.ps1" -Force
    }

    Write-Host "Staging and committing files..." -ForegroundColor Cyan
    Push-Location $tempDir
    & $gitBin config user.name "Antigravity (AI)"
    & $gitBin config user.email "antigravity@google.com"
    & $gitBin add lease-hunter/
    & $gitBin commit -m $CommitMessage
    & $gitBin push origin main
    Pop-Location
    
    Write-Host "Cleaning up temporary directory..." -ForegroundColor Cyan
    Remove-Item -Recurse -Force $tempDir
    Write-Host "Lease Hunter pushed successfully!" -ForegroundColor Green
} else {
    Write-Error "Failed to clone repository. Check your connection or SSH/PAT credentials."
}
