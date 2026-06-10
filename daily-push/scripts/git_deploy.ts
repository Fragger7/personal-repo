import { execSync } from "child_process";
import * as fs from "fs";
import * as path from "path";
import dotenv from "dotenv";

dotenv.config();

const pat = process.env.GITHUB_PAT || "";
const username = process.env.GITHUB_USERNAME || "Fragger7";
const repo = process.env.GITHUB_REPO || "personal-repo";
const email = "faraze46m3@gmail.com"; // User's email from metadata

if (!pat) {
  console.error("Error: GITHUB_PAT is not defined in .env");
  process.exit(1);
}

const sourceDir = process.cwd();
const cloneDir = "/tmp/personal-repo-push";
const repoUrl = `https://${username}:${pat}@github.com/${username}/${repo}.git`;

try {
  // 1. Build the codebase
  console.log("=== STEP 1: Building production application ===");
  execSync("npm run build", { stdio: "inherit", cwd: sourceDir });
  console.log("Build completed successfully!");

  // Verify build folder exists
  const distDir = path.join(sourceDir, "dist");
  if (!fs.existsSync(distDir)) {
    throw new Error("Build succeeded but dist/ directory was not found!");
  }

  // 2. Clone the repository
  console.log("=== STEP 2: Cloning remote personal repository ===");
  if (fs.existsSync(cloneDir)) {
    fs.rmSync(cloneDir, { recursive: true, force: true });
  }
  execSync(`git clone ${repoUrl} ${cloneDir}`, { stdio: "inherit" });

  // 3. Make sure daily-push directory exists in clone
  const destDailyPush = path.join(cloneDir, "daily-push");
  if (!fs.existsSync(destDailyPush)) {
    console.log("daily-push folder does not exist, creating it...");
    fs.mkdirSync(destDailyPush, { recursive: true });
  } else {
    console.log("daily-push folder exists. Clearing old assets...");
    // Clear old assets specifically to avoid lingering hashed bundles
    const oldAssets = path.join(destDailyPush, "assets");
    if (fs.existsSync(oldAssets)) {
      fs.rmSync(oldAssets, { recursive: true, force: true });
    }
  }

  // 4. Copy build output from /dist into personal-repo/daily-push
  console.log("=== STEP 3: Copying production bundles into repository daily-push/ ===");
  copyRecursiveSync(distDir, destDailyPush);

  // 5. Copy oauth-callback.html if not copied by vite (from public)
  const oauthCallbackSource = path.join(sourceDir, "public", "oauth-callback.html");
  const oauthCallbackDest = path.join(destDailyPush, "oauth-callback.html");
  if (fs.existsSync(oauthCallbackSource)) {
    fs.copyFileSync(oauthCallbackSource, oauthCallbackDest);
    console.log("Copied oauth-callback.html into place.");
  }

  // 6. Copy key source files to /daily-push for source control backup
  console.log("=== STEP 4: Backing up source files into daily-push/ ===");
  const sourceFoldersToSync = ["src", "docs", "public", "scripts"];
  const sourceFilesToSync = [
    "package.json",
    "tsconfig.json",
    "vite.config.ts",
    "metadata.json",
    "manifest.json",
    ".env.example",
    "sw.js"
  ];

  for (const folder of sourceFoldersToSync) {
    const srcPath = path.join(sourceDir, folder);
    const destPath = path.join(destDailyPush, folder);
    if (fs.existsSync(srcPath)) {
      if (fs.existsSync(destPath)) {
        fs.rmSync(destPath, { recursive: true, force: true });
      }
      copyRecursiveSync(srcPath, destPath);
    }
  }

  for (const file of sourceFilesToSync) {
    const srcPath = path.join(sourceDir, file);
    const destPath = path.join(destDailyPush, file);
    if (fs.existsSync(srcPath)) {
      fs.copyFileSync(srcPath, destPath);
    }
  }

  // 7. Commit and push the changes
  console.log("=== STEP 5: Committing and pushing changes ===");
  execSync("git config user.name '" + username + "'", { cwd: cloneDir });
  execSync("git config user.email '" + email + "'", { cwd: cloneDir });
  
  execSync("git add .", { cwd: cloneDir });
  
  // Check if there are differences to commit
  const status = execSync("git status --porcelain", { cwd: cloneDir }).toString().trim();
  if (!status) {
    console.log("No changes detected. Repository is already up to date!");
  } else {
    console.log("Changes detected:\n" + status);
    const commitMsg = "Sync Daily Push: Locked Client ID, refined UI preset and docs";
    execSync(`git commit -m "${commitMsg}"`, { cwd: cloneDir, stdio: "inherit" });
    
    console.log("Pushing to GitHub...");
    execSync("git push origin main", { cwd: cloneDir, stdio: "inherit" });
    console.log("Git push completed successfully! Project is updated and deployed.");
  }

} catch (error: any) {
  console.error("CRITICAL EXCEPTION IN GIT DEPLOYMENT PIPELINE:", error.message);
  if (error.stdout) console.log("stdout:", error.stdout.toString());
  if (error.stderr) console.log("stderr:", error.stderr.toString());
  process.exit(1);
}

// Helper to recursively copy directories
function copyRecursiveSync(src: string, dest: string) {
  const exists = fs.existsSync(src);
  const stats = exists && fs.statSync(src);
  const isDirectory = exists && stats && stats.isDirectory();
  
  if (isDirectory) {
    if (!fs.existsSync(dest)) {
      fs.mkdirSync(dest, { recursive: true });
    }
    fs.readdirSync(src).forEach((childItemName) => {
      copyRecursiveSync(
        path.join(src, childItemName),
        path.join(dest, childItemName)
      );
    });
  } else if (exists) {
    fs.copyFileSync(src, dest);
  }
}
