# IPTV Playlist Analytics Dashboard - System Architecture & Developer Guide

This document contains the complete system architecture, operational decisions, cloud deployment triggers, and step-by-step Git lifecycle workflows for the **IPTV Playlist Analytics Dashboard**. It is designed to serve as a comprehensive knowledge source for both human developers and autonomous AI agents (LLMs).

---

## 📌 Project Overview
The IPTV Playlist Analytics Dashboard is a lightweight, high-performance Streamlit application. It is designed to parse, validate, and analyze IPTV playlist credentials (in the Xtream Codes API format) from messy, unstructured text blocks. The tool employs a multi-tiered async workflow with outbound network protection, rate limiting, and lazy evaluation to ensure speed, efficiency, and safety.

---

## 🏗️ Architecture & Core Components

### 1. Security, Password Access & Outbound Protection

#### A. Secure Access Gateway (Password Gate)
To protect public cloud deployments from unauthorized access while maintaining ease of use for local development, the app includes a conditional password barrier:
* **Production/Cloud Check**: The app inspects `st.secrets` for `ACCESS_PASSWORD`. If configured, it blocks page rendering and presents a password input gate. If the input matches `ACCESS_PASSWORD`, access is cached in `st.session_state["password_correct"]`. If verification fails or is empty, execution is terminated immediately using `st.stop()`.
* **Developer Bypass (Local)**: If `ACCESS_PASSWORD` is absent in `st.secrets` (default state in fresh local development environments), the application automatically bypasses the password screen and launches in open mode.
* **Local Security Testing**: To test the security gate locally, developers can populate a local `.streamlit/secrets.toml` file with:
  ```toml
  ACCESS_PASSWORD = "your_test_password"
  ```

#### B. Geolocation Outbound Network Shield
Accidental exposure of a private home IP address during playlist checks can compromise privacy. The system implements a strict safety shield:
* **API Query**: The dashboard queries `http://ip-api.com/json/` to fetch the external IP address, Internet Service Provider (ISP), and Organization (Org) running the app.
* **Disconnected Check**: If the query fails, the IP status is set to `DISCONNECTED / UNKNOWN`. In this state, the **"Analyze Playlist Nodes"** button is disabled to prevent accidental DNS or IP leakage.
* **Hosting Detector & Warning**: The app matches the returned ISP/Org values against a list of public cloud providers (`amazon`, `aws`, `google`, `azure`, `cloudflare`, `digitalocean`, etc.). If a match is found, a warning banner alerts the user that public cloud ranges are frequently blocked by IPTV provider firewalls.

---

### 2. Multi-Tiered Discovery Flow

#### A. Ingestion Parser Engine
* **Xtream Codes Layout Scanner**: Function [parse_m3u_urls](file:///C:/Development/Apps/Project%20Strong/app.py) uses regex patterns to parse Xtream credentials from unstructured text blocks.
* **Patterns Supported**: Scans both standard player API layouts (`player_api.php?username=...&password=...`) and standard get fallbacks (`get.php?username=...&password=...`).

#### B. Tier 1: Asynchronous Handshake Verification
* **Endpoint**: Initiates a GET request to the host's `/player_api.php` with credentials.
* **HTTP Error & Block Detection**:
  * **HTTP 403**: Mapped to `🛡️ Cloud Blocked (HTTP 403)` to diagnose cloud hosting blocks.
  * **HTTP 521**: Mapped to `🔴 Offline (Server Dead)`.
  * **Other Non-200 Codes**: Mapped to `🛡️ Firewalled / Blocked`.
  * **Unauthorized Text body**: Mapped to `🟡 Expired / Invalid`.
* **Catalog Stats Lazy Load**: Handshake results are stored in `st.session_state["playlist_results"]`. Overall channel and VOD catalog counts are lazy-loaded on demand via a **"Query Channels & VOD Counts"** button to optimize execution speeds.

#### C. Tier 2: Local Category Explorer
* **On-Demand Accordions**: Activating an expander for an active node spawns concurrent async tasks via [fetch_lazy_details](file:///C:/Development/Apps/Project%20Strong/app.py) to fetch `get_live_categories` and `get_live_streams` data.
* **Server-Side Bypass**: Filters and counts category channels locally rather than requesting pre-filtered category URLs from the server, bypassing buggy IPTV endpoints and ensuring accurate listings with correct logos.

---

## 🛠️ Technology Stack
* **UI Framework**: Streamlit (Python)
* **Networking**: HTTPX (Asynchronous client, evasion headers mimicking standard IPTV Smarters software configurations)
* **Data Handling**: Pandas, JSON
* **Concurrency**: Asyncio

---

## 🚀 Running Locally
To test or run the dashboard locally:
1. **Dependencies**:
   ```bash
   pip install streamlit httpx pandas
   ```
2. **Execute via Module**:
   ```bash
   python -m streamlit run app.py
   ```
3. **Helper Script**: Alternatively, double-click [run.bat](file:///C:/Development/Apps/Project%20Strong/run.bat) to run the script in windowless Python (`pythonw`) mode.

---

## ☁️ Cloud Deployment Pipeline (Streamlit Community Cloud)

Streamlit Community Cloud monitors the remote Git repository and hot-reloads changes automatically.

* **Repository Address**: `https://github.com/Fragger7/personal-repo`
* **Target Branch**: `main`
* **Target App File**: `project-strong/app.py`
* **Secrets Configuration**: Accessible via the App Dashboard under **Advanced Settings > Secrets**. Add the following line to authorize secure access:
  ```toml
  ACCESS_PASSWORD = "your_chosen_secret_password"
  ```
* **Git Hot-Reload Trigger**: Any commit pushed to the `main` branch of the GitHub repository triggers Streamlit to pull the updates, install dependencies listed in `requirements.txt`, and redeploy the live application instantly.

---

## 🤖 AI Agent Git Operations Lifecycle (Important for LLMs)

Since Git commands may not always be in the system PATH, always locate the git binary at **`C:\Program Files\Git\cmd\git.exe`** if raw `git` commands fail.

To keep the repository clean and avoid polluting sister project files at the root of the repository, follow the two workflows below exactly:

### 1. Session Startup Synchronization Flow (Pull/Sync)
At the start of every session, verify and import updates from the remote repository before making any workspace changes:

```powershell
# Step 1: Clone remote repository to temporary folder
& "C:\Program Files\Git\cmd\git.exe" clone https://github.com/Fragger7/personal-repo.git "C:\Development\Apps\Project Strong\personal-repo-temp" --depth 1

# Step 2: Compare files in personal-repo-temp/project-strong/ with local workspace C:\Development\Apps\Project Strong\
# Step 3: Copy any remote updates back into the local workspace directory
Copy-Item "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\*" "C:\Development\Apps\Project Strong\" -Recurse -Force

# Step 4: Delete the temporary repository clone folder
Remove-Item -Recurse -Force "C:\Development\Apps\Project Strong\personal-repo-temp"
```

### 2. Session Commit & Publish Flow (Push)
When you are ready to commit and push changes, use a temporary directory clone to isolate changes and prevent pushing files to other root directories:

```powershell
# Step 1: Clone the remote repository to temporary folder
& "C:\Program Files\Git\cmd\git.exe" clone https://github.com/Fragger7/personal-repo.git "C:\Development\Apps\Project Strong\personal-repo-temp"

# Step 2: Copy updated workspace files to personal-repo-temp/project-strong/
Copy-Item "C:\Development\Apps\Project Strong\app.py" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\app.py" -Force
Copy-Item "C:\Development\Apps\Project Strong\.gitignore" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\.gitignore" -Force
Copy-Item "C:\Development\Apps\Project Strong\requirements.txt" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\requirements.txt" -Force
Copy-Item "C:\Development\Apps\Project Strong\GEMINI.md" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\GEMINI.md" -Force
Copy-Item "C:\Development\Apps\Project Strong\run.bat" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\run.bat" -Force
Copy-Item "C:\Development\Apps\Project Strong\.agents\AGENTS.md" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\.agents\AGENTS.md" -Force

# Step 3: Stage, commit, and push from personal-repo-temp
cd "C:\Development\Apps\Project Strong\personal-repo-temp"
& "C:\Program Files\Git\cmd\git.exe" add project-strong/
& "C:\Program Files\Git\cmd\git.exe" commit -m "Commit message detailing changes"
& "C:\Program Files\Git\cmd\git.exe" push origin main

# Step 4: Clean up temporary folder
Remove-Item -Recurse -Force "C:\Development\Apps\Project Strong\personal-repo-temp"
```

---

## 🌩️ Google AI Studio Cloud Container Architecture
For developers and AI agents running within serverless container workspaces (e.g., Google AI Studio):

* **Workspace Control Center**: A React-Vite visual shell is set up at the sandbox root to track tracked files, config status, and credentials.
* **Automated Sync & Push Script (`git_push.cjs`)**: An automated Javascript script is placed in the workspace root to carry out standard Git publishes non-interactively.
* **To Sync & Push on AI Studio**:
  1. Add a `GITHUB_TOKEN` secret in the Google AI Studio Settings menu.
  2. Execute the commit utility script:
     ```bash
     npx tsx git_push.cjs
     ```
  3. The utility performs isolated staging of only files in `project-strong/`, configures metadata safely, pushes, and executes total cleanup.

