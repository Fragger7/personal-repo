const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const token = process.env.GITHUB_TOKEN;
const repoUrl = token 
  ? `https://${token}@github.com/Fragger7/personal-repo.git`
  : `https://github.com/Fragger7/personal-repo.git`;

const tempDir = path.join(__dirname, 'personal-repo-temp-pull');
const workspace = __dirname;

console.log("Starting secure Git pull and session synchronization...");
console.log(`Repository target URL: https://github.com/Fragger7/personal-repo.git`);

try {
  // 1. Clone repository to temp directory
  console.log("Cloning remote repository...");
  execSync(`git clone ${repoUrl} "${tempDir}" --depth 1`, { stdio: 'inherit' });
  
  const remoteLeaseHunterDir = path.join(tempDir, 'lease-hunter');
  
  if (!fs.existsSync(remoteLeaseHunterDir)) {
    console.error("Error: lease-hunter directory does not exist in the remote repository.");
    process.exit(1);
  }

  console.log("Synchronizing matching lease-hunter directory files...");
  
  // Recursively copy files from remoteLeaseHunterDir to workspace
  function copyRecursiveSync(src, dest) {
    const exists = fs.existsSync(src);
    const stats = exists && fs.statSync(src);
    const isDirectory = exists && stats.isDirectory();
    if (isDirectory) {
      if (!fs.existsSync(dest)) {
        fs.mkdirSync(dest, { recursive: true });
      }
      fs.readdirSync(src).forEach((childItemName) => {
        copyRecursiveSync(path.join(src, childItemName), path.join(dest, childItemName));
      });
    } else {
      // Skip copying specific metadata/git items if necessary, but bring over apps and configs
      const basename = path.basename(src);
      if (basename !== '.git' && basename !== '.gitignore' || src.includes('.agents')) {
        fs.copyFileSync(src, dest);
        console.log(`Synced: ${path.relative(remoteLeaseHunterDir, src)}`);
      }
    }
  }

  copyRecursiveSync(remoteLeaseHunterDir, workspace);
  console.log("Sync process successfully completed!");
  
} catch (err) {
  console.error("Sync pull execution failed:", err.message);
} finally {
  console.log("Cleaning up temporary clone...");
  if (fs.existsSync(tempDir)) {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}
