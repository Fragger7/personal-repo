# Lease Hunter - Agent Workspace Rules

This file defines the mandatory operational constraints and Git workflows for any AI agent or LLM taking on this project workspace. These rules are in place to guarantee security, maintain mono-repo folder boundaries, and align with Streamlit Cloud's automated hot-reload triggers.

---

## 🛑 Rule 1: Locate Git Executable
On this Windows host, the raw `git` command may not be in the default shell `PATH`.
* **Action**: If a plain `git` command fails, you **MUST** run all Git operations using the absolute path to the executable:
  `"C:\Program Files\Git\cmd\git.exe"`

---

## 🛑 Rule 2: Git Sync on Session Startup (Mandatory)
Before you edit any files or make any commits, you must check for changes in the remote GitHub repository (`https://github.com/Fragger7/personal-repo.git`) and pull them down:

> [!IMPORTANT]
> **DO NOT CREATE A NEW WORKSPACE OR BRANCH STRUCTURALLY.** Do not run `git init` to initialize a new git repository inside `C:\Development\Apps\Lease Hunter`. You MUST connect to the existing remote repository, pull/clone the latest files, and continue the development on the current `main` branch. Use the existing consolidated context from the repository rather than creating independent branches or detached heads.
> 
> **MANDATORY KNOWLEDGE SYNC:** Before commencing any feature development, you MUST read all documentation and developer guides (specifically [LEASE_HUNTER.md](file:///C:/Development/Apps/Lease%20Hunter/LEASE_HUNTER.md) and this [AGENTS.md](file:///C:/Development/Apps/Lease%20Hunter/.agents/AGENTS.md)) to fully understand the project's architecture, state variables, and execution parameters.

1. **Clone to Temp**: Clone the repository to a temporary directory inside the workspace using `--depth 1` to save time/bandwidth:
   ```powershell
   & "C:\Program Files\Git\cmd\git.exe" clone https://github.com/Fragger7/personal-repo.git "C:\Development\Apps\Lease Hunter\personal-repo-temp" --depth 1
   ```
2. **Compare and Sync**: Compare files in `personal-repo-temp/lease-hunter/` with the active workspace `C:\Development\Apps\Lease Hunter\`. If the remote contains modifications not present locally, copy them into the active workspace to prevent split-brain code states:
   ```powershell
   Copy-Item "C:\Development\Apps\Lease Hunter\personal-repo-temp\lease-hunter\*" "C:\Development\Apps\Lease Hunter\" -Recurse -Force
   ```
3. **Clean Up**: Delete the temporary directory immediately after syncing:
   ```powershell
   Remove-Item -Recurse -Force "C:\Development\Apps\Lease Hunter\personal-repo-temp"
   ```

---

## 🛑 Rule 3: Isolated Git Commits and Pushes (Mono-Repo Safety)
This project is committed under the `lease-hunter/` folder of a mono-repo. **NEVER** initialize Git directly inside `C:\Development\Apps\Lease Hunter` or perform commits from the workspace root. Instead, use the automated tooling.

When executing in Google AI Studio Cloud Container, use the automated `git_push.cjs` utility for pushing back to Github.

`npx tsx git_push.cjs "Your Commit Message"`

---

## 🛑 Rule 4: Secret Management
* **Do NOT Hardcode Secrets**: Never commit access passwords, API keys, or raw credentials to the repository. On cloud environments, utilize `st.secrets["GEMINI_API_KEY"]` for security. For local development, keys can be injected using the `GEMINI_API_KEY` environment variable. Both `.env` and the `.streamlit/` directory must remain git-ignored.

---

## 🛑 Rule 5: Google AI Studio Cloud Workspace Compatibility (Docker/Linux Container)
When this repository is loaded or cloned into a **Google AI Studio Cloud Run development workspace** (or any temporary Linux container):
1. **Host Environment**: Commands are executed directly in a Linux shell, meaning standard commands like `git` and `node` are natively available.
2. **React Control Panel**: A React-Vite visual shell is configured at the workspace root to provide an interactive dashboard summarizing the active sync files, setup configurations, and push triggers.
3. **Automated Secure Push**: A specialized node script `git_push.cjs` is included at the workspace root to automate the isolated mono-repo commit and push process safely.
4. **Authorizing Pushes & PAT Setup Guide**:
   - **How to configure GITHUB_TOKEN in AI Studio UI**:
     1. Open your chat/prompt thread in **Google AI Studio**.
     2. In the right-hand configurations sidebar, locate the **System Instructions** or **Secret Manager / API Keys / Environmental Settings**.
     3. Click **Add Secret** (or **Manage Secrets**).
     4. Set Key Name to: `GITHUB_TOKEN`
     5. Paste your GitHub Personal Access Token (PAT) with write access to `https://github.com/Fragger7/personal-repo.git`.
     6. Save the settings. The container environment will now load it natively under `process.env.GITHUB_TOKEN`.
   - Run the automated sync & push script from the workstation terminal using:
     ```bash
     npx tsx git_push.cjs "Your Commit Message Description"
     ```
   - This script creates a temporary clone, stages only modifications inside `lease-hunter/`, commits, pushes back to GitHub, and cleans up completely without breaking the production branch.

---

## 🛑 Rule 6: Full-Stack Considerations (React + Express)
The repository now runs a full-stack React and Express app. 
* **API Routing**: Create API routes under `/api/*` in `server.ts` or corresponding backend controllers rather than exposing API payloads entirely in the client-side code.
* **Component Modularity**: Refrain from cramming logic into `App.tsx`. Extract files out into properly modularized React structures.

---

## 🛑 Rule 7: Mandatory Documentation Synchronization
Whenever you modify the application code or state boundaries, you **MUST** update the developer documentation (**LEASE_HUNTER.md** and **GEMINI.md**) in the same commit. You must document new states, parameters, logic pathways, or dependencies. This prevents "documentation drift" and ensures that the next agent starting a session has a perfectly mirrored context. Do not push code updates without updating the guides.
