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
* **Universal Credential Scanner**: Function `parse_credentials` uses regex patterns to parse both Xtream Codes and Stalker Portal credentials from unstructured text blocks.
* **Patterns Supported**: 
  * **Xtream Codes**: Scans standard player API layouts (`player_api.php?username=...&password=...`) and target fallbacks (`get.php?...`).
  * **Stalker Portals**: Employs a robust state-machine parser to isolate disparate MAC addresses and Host URLs connected via unstructured block fragments or custom scanner headers without failing due to whitespace and formatting breaks.

#### B. Tier 1: Asynchronous Handshake Verification
* **Endpoint (Xtream)**: Initiates a GET request to the host's `/player_api.php` with username/password credentials.
* **Endpoint (Stalker)**: Initiates a high-speed handshake to `/server/load.php?type=stb&action=handshake` injecting MAC cookies and custom User-Agents (e.g., MAG200) to test portal accessibility.
* **HTTP Error & Block Detection**:
  * **HTTP 403**: Mapped to `🛡️ Cloud Blocked (HTTP 403)` to diagnose cloud hosting blocks.
  * **HTTP 521**: Mapped to `🔴 Offline (Server Dead)`.
  * **Other Non-200 Codes**: Mapped to `🛡️ Firewalled / Blocked`.
  * **Unauthorized Text body**: Mapped to `🟡 Expired / Invalid`.
* **Catalog Stats Lazy Load**: Handshake results are stored in `st.session_state["playlist_results"]`. Overall channel and VOD catalog counts are lazy-loaded on demand via a **"Query Channels & VOD Counts"** button to optimize execution speeds.

#### C. Tier 2: Local Category Explorer & Stalker Constraints
* **On-Demand Accordions**: Activating an expander for an active node spawns concurrent async tasks via fetch_lazy_details to fetch `get_live_categories` and `get_live_streams` data.
* **Server-Side Bypass (Xtream)**: Filters and counts category channels locally rather than requesting pre-filtered category URLs from the server, bypassing buggy endpoints and ensuring accurate listings with correct logos.
* **Stalker Limits (Ministra Framework)**: Deep-dive channel classification and VOD grid streaming is structurally blocked for Stalker Portals due to the requirements of the MAC-driven authentication payload dynamically expiring. Deep-dive discovery is explicitly restricted from accessing these nodes to avoid triggering the target server's firewall banning mechanisms. The dashboard will inform the provider.

### 3. Application UI & Efficiency Optimizations
* **Dynamic Multi-Theming**: The app features a UI theme selection engine managed via `st.session_state` and a top-level expander ("⚙️ Dashboard Settings & Themes"). Users can dynamically swap CSS visual skins including *Midnight Purple (Focus)*, *Ocean Blue (Glass)*, *Crimson Red (Dark)*, and *Clean Light Mode*. The chosen CSS payload is automatically injected to re-style tabs, containers, and data graphics.
* **Tab-Based Workspace**: The application is divided into a clean, tabbed hierarchy:
  * **🛠️ Base64 Decoder**: Extracts hidden structural links embedded as text chunks inside unstructured text blocks, automatically stripping garbage or padding limits. Output enables one-click link launching or copying.
  * **📡 Multi-Payload Scanner**: The main bulk ingest and tracking center.
  * **📺 Xtream Codes & 🛸 Stalker Portals**: Dedicated manifest tabs, indicating real-time discovered node counts dynamically in their tab titles. Records now capture target server Timezones and Server regional timings structurally when available.
  * **💾 Committed Data**: A persistence layer data grid that allows users to permanently save ("Commit") verified lines from the Xtream or Stalker tabs. Data is locally saved to `committed.json` and synchronized seamlessly to Git. Free-form text note edits and targeted deletion features track active accounts for historical runs without duplicates. When running in Streamlit Community Cloud (or locally with `GITHUB_TOKEN` secrets configured in Streamlit), saving dynamically triggers a direct push of `committed.json` to the GitHub repository using the GitHub REST API to ensure permanent cloud persistence. To prevent spam and unnecessary API usage, the pushing mechanism intelligently compares local data arrays against remote arrays and skips pushing if records are identical.
* **State Caching (st.cache_data)**: The application utilizes Streamlit's data caching (`@st.cache_data(ttl=300)`) strictly to cache outbound verification blocks (e.g. `ip-api.com`). This is absolutely critical because Streamlit executes top-to-bottom on every user interaction (clicks, toggles) which will otherwise rapidly hammer public rate-limited limits (45 reqs/min for ip-api.com) when navigating libraries.
* **Tiered Loading & Master-Detail View**: All tabular views use a **Master-Detail interaction paradigm** and prominently display their total visible record metrics (`st.caption(f"Showing **X** records.")`). 
  * The top data-grid is selected by clicking a row (`selection_mode="single-row"` and `on_select="rerun"`).
  * Selection triggers an auto-scrolling Javascript injection to snap the browser down to the **Deep-Dive Drawer**.
  * The deep detail drawer explicitly generates **Discrete Login Credentials** (separating Host, Username, and Password into their own easily copyable widgets) instead of just dropping an `M3U Playlist URL`. This provides an easy fallback for IPTV apps where standard M3U downloads (via `/get.php`) have been deliberately restricted.
  * Tier 2 Live Catalogs and VODs are lazy-evaluated on-demand visually within the active Detail view (available in **Xtream**, **Stalker**, and **Committed Data** tabs). To prevent Streamlit's aggressive re-runs from resetting the UI or making redundant network requests during category selection, Tier 2 fetch results are explicitly preserved inside `st.session_state` using unique compound keys (e.g., `t2_{base_url}_{username}`).
  * When manually fetching cloud changes in the Committed Data tab via the "Reload from Cloud" button, the UI simulates a smooth progress bar overlay (`st.progress`) to visually signal the backend synchronization process occurring over GitHub APIs, providing a better user experience over instant flashes.

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
* **Secrets Configuration**: Accessible via the App Dashboard under **Advanced Settings > Secrets**. Add the following lines to authorize secure access and auto-commits:
  ```toml
  ACCESS_PASSWORD = "your_chosen_secret_password"
  GITHUB_TOKEN = "your_github_personal_access_token_for_auto_saves"
  ```
* **Git Hot-Reload Trigger**: Any commit pushed to the `main` branch of the GitHub repository triggers Streamlit to pull the updates, install dependencies listed in `requirements.txt`, and redeploy the live application instantly.

---

## 🤖 AI Agent Git Operations Lifecycle (Important for LLMs)

Since Git commands may not always be in the system PATH, always locate the git binary at **`C:\Program Files\Git\cmd\git.exe`** if raw `git` commands fail.

To keep the repository clean and avoid polluting sister project files at the root of the repository, follow the two workflows below exactly:

### 1. Mandatory Session Startup Synchronization Flow (Pull/Sync)
At the start of every session—before working on any new enhancement, making code changes, or exploring the environment—you MUST download the latest Git files from the remote repository. Once synchronized, you MUST review the latest knowledge from `GEMINI.md` and `.agents/AGENTS.md`.

```powershell
# Step 1: Clone remote repository to temporary folder
& "C:\Program Files\Git\cmd\git.exe" clone https://github.com/Fragger7/personal-repo.git "C:\Development\Apps\Project Strong\personal-repo-temp" --depth 1

# Step 2: Compare files in personal-repo-temp/project-strong/ with local workspace C:\Development\Apps\Project Strong\
# Step 3: Copy any remote updates back into the local workspace directory
Copy-Item "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\*" "C:\Development\Apps\Project Strong\" -Recurse -Force

# Step 4: Delete the temporary repository clone folder
Remove-Item -Recurse -Force "C:\Development\Apps\Project Strong\personal-repo-temp"
```

**(For Linux/AI Studio Workspaces)**:
You can pull the latest files using shell commands or `git_clone.js`, replacing the active workspace files. After downloading, ALWAYS read the latest `GEMINI.md` and `.agents/AGENTS.md` to refresh constraints and project context.

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
Copy-Item "C:\Development\Apps\Project Strong\committed.json" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\committed.json" -Force -ErrorAction SilentlyContinue

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

