import { execSync } from "child_process";

try {
  console.log("Checking Git version and repository status...");
  const gitVersion = execSync("git --version").toString().trim();
  console.log("Git Version:", gitVersion);
  
  const status = execSync("git status").toString().trim();
  console.log("Git Status:\n", status);

  const remotes = execSync("git remote -v").toString().trim();
  console.log("Git Remotes:\n", remotes);
} catch (error: any) {
  console.error("Error executing git command:", error.message);
  if (error.stdout) console.log("stdout:", error.stdout.toString());
  if (error.stderr) console.log("stderr:", error.stderr.toString());
}
