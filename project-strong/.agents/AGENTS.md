# IPTV Playlist Analytics Dashboard - Agent Workspace Rules

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
> **DO NOT CREATE A NEW WORKSPACE OR BRANCH STRUCTURALLY.** Do not run `git init` to initialize a new git repository inside `C:\Development\Apps\Project Strong`. You MUST connect to the existing remote repository, pull/clone the latest files, and continue the development on the current `main` branch. Use the existing consolidated context from the repository rather than creating independent branches or detached heads.
> 
> **MANDATORY KNOWLEDGE SYNC:** Before commencing any feature development, you MUST read all documentation and developer guides (specifically [GEMINI.md](file:///C:/Development/Apps/Project%20Strong/GEMINI.md) and this [AGENTS.md](file:///C:/Development/Apps/Project%20Strong/.agents/AGENTS.md)) to fully understand the project's architecture, state variables, and execution parameters.

1. **Clone to Temp**: Clone the repository to a temporary directory inside the workspace using `--depth 1` to save time/bandwidth:
   ```powershell
   & "C:\Program Files\Git\cmd\git.exe" clone https://github.com/Fragger7/personal-repo.git "C:\Development\Apps\Project Strong\personal-repo-temp" --depth 1
   ```
2. **Compare and Sync**: Compare files in `personal-repo-temp/project-strong/` with the active workspace `C:\Development\Apps\Project Strong\`. If the remote contains modifications not present locally, copy them into the active workspace to prevent split-brain code states:
   ```powershell
   Copy-Item "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\*" "C:\Development\Apps\Project Strong\" -Recurse -Force
   ```
3. **Clean Up**: Delete the temporary directory immediately after syncing:
   ```powershell
   Remove-Item -Recurse -Force "C:\Development\Apps\Project Strong\personal-repo-temp"
   ```

---

## 🛑 Rule 3: Isolated Git Commits and Pushes (Mono-Repo Safety)
This project is committed under the `project-strong/` folder of a mono-repo. **NEVER** initialize Git directly inside `C:\Development\Apps\Project Strong` or perform commits from the workspace root. Instead, follow this exact workflow:

1. **Clone Remote**: Clone the full repo to a temporary directory:
   ```powershell
   & "C:\Program Files\Git\cmd\git.exe" clone https://github.com/Fragger7/personal-repo.git "C:\Development\Apps\Project Strong\personal-repo-temp"
   ```
2. **Copy Working Files**: Copy your edited workspace files into the repository's `project-strong/` subdirectory:
   ```powershell
   Copy-Item "C:\Development\Apps\Project Strong\app.py" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\app.py" -Force
   Copy-Item "C:\Development\Apps\Project Strong\.gitignore" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\.gitignore" -Force
   Copy-Item "C:\Development\Apps\Project Strong\requirements.txt" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\requirements.txt" -Force
   Copy-Item "C:\Development\Apps\Project Strong\GEMINI.md" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\GEMINI.md" -Force
   Copy-Item "C:\Development\Apps\Project Strong\run.bat" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\run.bat" -Force
   Copy-Item "C:\Development\Apps\Project Strong\.agents\AGENTS.md" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\.agents\AGENTS.md" -Force
   ```
3. **Configure details (if unset)**: Set local configurations inside the temp directory:
   ```powershell
   cd "C:\Development\Apps\Project Strong\personal-repo-temp"
   & "C:\Program Files\Git\cmd\git.exe" config user.name "Antigravity (AI)"
   & "C:\Program Files\Git\cmd\git.exe" config user.email "antigravity@google.com"
   ```
4. **Stage, Commit, and Push**: Stage ONLY the `project-strong/` folder, commit, and push:
   ```powershell
   & "C:\Program Files\Git\cmd\git.exe" add project-strong/
   & "C:\Program Files\Git\cmd\git.exe" commit -m "Commit description"
   & "C:\Program Files\Git\cmd\git.exe" push origin main
   ```
5. **Clean Up**: Delete the temporary directory:
   ```powershell
   Remove-Item -Recurse -Force "C:\Development\Apps\Project Strong\personal-repo-temp"
   ```

---

## 🛑 Rule 4: Secret Management and Throttling Limits
* **Do NOT Hardcode Secrets**: Never commit access passwords, API keys, or raw credentials to the repository. On cloud environments, utilize `st.secrets["ACCESS_PASSWORD"]` for security. For local development, passwords can be injected using the `STREAMLIT_ACCESS_PASSWORD` environment variable or placed in a local `local_password.txt` file (which is processed by `run.bat` and parsed natively by `app.py`). Both `local_password.txt` and the `.streamlit/` directory must remain git-ignored.
* **Keep Throttling Disabled**: Concurrency throttling (semaphores) has been removed at the user's request. Keep queries unthrottled but lazy-loaded (Tiers 1 and 2) to optimize responsiveness.
* **Preserve Logging**: Maintain standard Python logging outputs to stdout so runtime diagnostics are visible in log utilities.

---

## 🛑 Rule 5: Google AI Studio Cloud Workspace Compatibility (Docker/Linux Container)
When this repository is loaded or cloned into a **Google AI Studio Cloud Run development workspace** (or any temporary Linux container):
1. **Host Environment**: Commands are executed directly in a Linux shell, meaning standard commands like `git` and `node` are natively available (do not use the Windows absolute paths).
2. **React Control Panel**: A React-Vite visual shell is configured at the workspace root (`/index.html`, `/src`, etc.) to provide an interactive dashboard summarizing the active sync files, setup configurations, and push triggers.
3. **Automated Secure Push**: A specialized node script `git_push.cjs` is included at the workspace root to automate the isolated mono-repo commit and push process safely.
4. **Authorizing Pushes**:
   - The user must provide a secure `GITHUB_TOKEN` as a Secret/Environment variable in the AI Studio Settings.
   - Run the automated sync & push script from the workstation terminal using:
     ```bash
     npx tsx git_push.cjs
     ```
   - This script creates a temporary clone, stages only modifications inside `project-strong/`, commits, pushes back to GitHub, and cleans up completely without breaking the production branch.

---

## 🛑 Rule 6: UI & App Optimizations (Caching)
If adding new network fetch mechanisms to `app.py`:
* **Streamlit Reruns**: Streamlit triggers a script re-run upon every button click, selection, or interactive element usage. Network logic on the top level must be safeguarded.
* **MANDATORY Application Cache**: For any external diagnostics (like `ip-api.com` or other limit-sensitive tracking services), you **MUST** secure the execution blocks using `@st.cache_data`. This bypasses strict, low rate-limits (`429 Too Many Requests`) from third-party tools during consecutive clicks or accordion navigations.
* **State Caching for Interactive Controls**: When displaying detailed sub-menus (like dropdown package selections) that are loaded on demand, you **MUST** cache the data in `st.session_state` (e.g., using `cached_live_cats`/`cached_live_streams` tied to a `cached_host_key`). If you do not cache them, the data will disappear on the subsequent rerun when the user interacts with the sub-menu dropdown controls.


