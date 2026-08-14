# git_push.ps1 - Automates local push of Lease Hunter files to the mono-repo folder.
param (
    [string]$CommitMessage = "Update Lease Hunter application, crawler engines, and GEMINI documentation"
)

$repoUrl = "https://github.com/Fragger7/personal-repo.git"
$workspace = "C:\Development\Apps\Lease Hunter"
$tempDir = Join-Path $workspace "personal-repo-temp"
$gitBin = "C:\Program Files\Git\cmd\git.exe"

Write-Host "Cloning repository..." -ForegroundColor Cyan
& $gitBin clone $repoUrl $tempDir

if ($LASTEXITCODE -eq 0) {
    Write-Host "Copying all workspace files to repo folder..." -ForegroundColor Cyan
    $targetDir = Join-Path $tempDir "lease-hunter"
    
    # Ensure target directory exists
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    
    # Copy all files and directories excluding node_modules, temp, scratch, and .git
    Get-ChildItem -Path $workspace -Exclude "node_modules", "personal-repo-temp", ".git", "scratch" | ForEach-Object {
        Copy-Item -Path $_.FullName -Destination $targetDir -Recurse -Force
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
