const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const token = process.env.GITHUB_TOKEN;
if (!token) {
    console.error("Error: GITHUB_TOKEN environment variable is not defined.");
    process.exit(1);
}

const repoUrl = `https://${token}@github.com/Fragger7/personal-repo.git`;
const tempDir = path.join(__dirname, 'personal-repo-temp');
const workspace = __dirname;

console.log("Cloning repository...");
try {
    execSync(`git clone ${repoUrl} "${tempDir}"`, { stdio: 'inherit' });
} catch (err) {
    console.error("Failed to clone repository:", err.message);
    process.exit(1);
}

const targetDir = path.join(tempDir, 'lease-hunter');
const targetAgentsDir = path.join(targetDir, '.agents');

try {
    console.log("Copying files...");
    fs.mkdirSync(targetAgentsDir, { recursive: true });

    const filesToCopy = [
        'app_legacy_streamlit.py',
        '.gitignore',
        'requirements.txt',
        'LEASE_HUNTER.md',
        'run.bat',
        'git_push.ps1',
        'flatten_context.py',
        'git_push.cjs',
        'git_pull.cjs',
        'PROMPT_MANIFEST.txt',
        'GEMINI.md',
        'package.json',
        'package-lock.json',
        'vite.config.ts',
        'tsconfig.json',
        'server.ts',
        'index.html'
    ];

    filesToCopy.forEach(file => {
        const src = path.join(workspace, file);
        const dest = path.join(targetDir, file);
        if (fs.existsSync(src)) {
            fs.copyFileSync(src, dest);
        }
    });

    const srcDir = path.join(workspace, 'src');
    const destSrcDir = path.join(targetDir, 'src');
    if (fs.existsSync(srcDir)) {
        fs.cpSync(srcDir, destSrcDir, { recursive: true });
    }

    const srcAgents = path.join(workspace, '.agents', 'AGENTS.md');
    const destAgents = path.join(targetAgentsDir, 'AGENTS.md');
    if (fs.existsSync(srcAgents)) {
        fs.copyFileSync(srcAgents, destAgents);
    }

    console.log("Staging and committing changes...");
    process.chdir(tempDir);
    execSync('git config user.name "Antigravity (AI)"', { stdio: 'inherit' });
    execSync('git config user.email "antigravity@google.com"', { stdio: 'inherit' });
    execSync('git add lease-hunter/', { stdio: 'inherit' });
    
    const commitMsg = process.argv[2] || "Update Lease Hunter via AI Studio";
    execSync(`git commit -m "${commitMsg}"`, { stdio: 'inherit' });

    console.log("Pushing to remote...");
    execSync('git push origin main', { stdio: 'inherit' });
    console.log("Push completed successfully!");
} catch (err) {
    console.error("Error during sync process:", err.message);
} finally {
    console.log("Cleaning up temporary clone...");
    process.chdir(workspace);
    if (fs.existsSync(tempDir)) {
        fs.rmSync(tempDir, { recursive: true, force: true });
    }
}
