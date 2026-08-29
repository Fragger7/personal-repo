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
  * **Tabular Combos (NEW)**: Extracts credentials from raw unstructured combos (`host:port user:pass`, `host:user:pass`, or missing components) commonly found in pasted text blocks.
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

### 3. Provider Intelligence Engine
* **Fingerprint Extraction**: During Tier 1 scans, the engine analyzes HTTP response headers (`Server`, `Date` for timezone, `CF-RAY` for Cloudflare, `X-Powered-By`), protocol configurations, and active JSON metadata responses (`allowed_output_formats`, server versions) to build a technical identity for the domain.
* **Branding Recognition (Tier 2)**: The engine searches the lazy-loaded VOD / Live Stream arrays for recognizable channel branding patterns. This includes detecting embedded community URLs (e.g. Telegram `t.me`, Discord, WhatsApp) and "dummy"/separator channels (e.g., `### Strong 8K ###`, `=== Movies ===`). Pattern weighting identifies the most likely official provider name from the noise.
* **Persistent Knowledge Base**: All learnings are mapped to a clean domain key in `provider_intelligence.json`. Like committed data, this JSON file synchronizes automatically via the GitHub REST API whenever a new fingerprint is gathered during scanning or Tier 2 deep-diving. The system natively merges findings to constantly expand its knowledge graph.

### 4. Application UI & Efficiency Optimizations
* **Dynamic Multi-Theming**: The app features a UI theme selection engine managed via `st.session_state` and a top-level expander ("⚙️ Dashboard Settings & Themes"). Users can dynamically swap CSS visual skins including *Midnight Purple (Focus)*, *Ocean Blue (Glass)*, *Crimson Red (Dark)*, and *Clean Light Mode*. The chosen CSS payload is automatically injected to re-style tabs, containers, and data graphics.
* **Tab-Based Workspace**: The application is divided into a clean, tabbed hierarchy with **JavaScript-injected Next Buttons** bridging the tabs for a linear flow:
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

## 🎨 World-Class UI/UX Design Standards

When building UI components, adding new screens, or refactoring the application interface, the developer or AI agent **MUST act as a world-class UI/UX engineer**:

* **Follow Best Practices**: Design interfaces based on the highest standards taught to the world's leading experts in UI/UX. The user experience must be intuitive, modern, and aesthetically pleasing.
* **Layout & Placement**: Ensure thoughtful, logical placement and grouping of controls (text boxes, buttons, checkboxes, toggles, dropdowns). Elements should be placed purposefully to create a natural flow, never "willy-nilly".
* **Consistency & Continuity**: Maintain absolute continuity and professionalism across all pages, tabs, and layout structures. Elements like sorting, filtering, and data tables must behave seamlessly and cohesively everywhere. 
* **Visual Polish & Precision**: Handle theming, exact color scheme pairings, error messages, and user screen feedback with world-class polish. Provide elegant loading states, toast notifications, clear empty states, and visually reassuring success indicators. Treat the interface as a premium commercial product.

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
Copy-Item "C:\Development\Apps\Project Strong\provider_intelligence.json" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\provider_intelligence.json" -Force -ErrorAction SilentlyContinue

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

---

## 📱 Android Native Application (Kotlin + Jetpack Compose)
To bypass restrictive cloud blockades and IP filtering encountered via web deployments (Streamlit Community Cloud), the project maintains a **Native Android Application** located in the `/android` directory.

* **Feature Parity Goal**: The Android app is structurally mapped to match the Python application's capabilities (Ingestion Parser, Base64 Decoder, Scanner, Xtream/Stalker Deep Dives, and Committed persistence).
* **Networking Evasion**: Running natively on the user's Android device ensures IP requests originate from the user's residential IP or mobile carrier, severely reducing HTTP 403 blockades compared to public cloud IP ranges.
* **UI Architecture**: Built using Kotlin and Jetpack Compose (`Material 3`). Navigation mimics the Python app's master-detail tab flow.
* **CI/CD APK Generation**: A GitHub Action (`.github/workflows/android-build.yml`) automatically compiles a debug `.apk` on every push to `main` that modifies the `/android` directory. Users can download the resulting APK from the GitHub Actions "Artifacts" panel.

---

## 📅 Future Backlog
* **Persistent Themes**: Save user theme preferences (e.g., in `localStorage` via Streamlit cookie managers or custom components) to remember the chosen aesthetic across page reloads and future visits without needing to re-select it in the UI/sidebar each time.


### 🐛 Android GitHub Actions Build Failure (Completed)
* **APK Build Pipeline**: The GitHub Action `.github/workflows/android-build.yml` is currently failing during the `gradle assembleDebug` step.
* **Troubleshooting Status**: Attempted to replicate the Gradle 8.7 / Android SDK build environment locally within the AI Studio container, but encountered persistent Java/Gradle daemon initialization issues (e.g. `Error opening zip file or JAR manifest missing`, out of memory, or Java environment setup failures). 
* **Resolution**: Upgraded Gradle wrapper version from 4.4.1 to 8.7 to match AGP 8.4 requirements, preventing the Java 17 compatibility error. Fixed Kotlin data mappings in ScannerTab.kt to correctly copy new extended metadata fields (Expires, Connections, Timezone) to the UI grid.

### 📱 Android UI/UX Overhaul (Completed)
The current Android Jetpack Compose UI needs significant improvements to reach parity with the Python web application. Key UX feedback to address:
* **Visual Presentation**: The current UI is bland with a 90s/late-2000s vibe in terms of graphics and presentation. Needs a modern, visually striking "jaw-dropping" overhaul.
* **Active Connections Toggle & Columns**: Functionality is missing. Add "Active Connections" toggles to begin the drill-down of connections. Ensure the views support the "cool columns" the Python app provides (Channel counts, VOD counts, etc.).
* **Compact Data Tables vs Cards**: The interface needs to move away from bulky cards. The experience of each result being displayed as a card on the Xtream and Stalker tabs requires endless scrolling. Use compact data tables or highly dense lists with useful columns instead.
* **Scanner Tab Workflow**: The Scanner tab has a horrible user experience because it renders each discovered connection as an individual card, making it useless and redundant. Instead, it should ONLY display the progress of the scan (with counts and a progress bar) and then declare a summary of counts found (via text or toast). Individual connections should only be listed in their dedicated Xtream or Stalker tabs.
* **Xtream / Stalker Drill-Downs**: Currently, clicking a connection card involves endless scrolling only to either verify or commit, without the 3rd-level drill-downs seen in the Python app. It MUST support fetching and displaying Channel groups (Categories) and Channel names, along with easy 1-click buttons to copy connection details (Host, Username, Password) or Commit.

### 🐛 Android Bugs to Fix (Completed)
* **Committed Data Sync**: The Committed Data tab is only fetching 8 results when there are 9 in the Git repository. Investigate and fix the sync discrepancy.
* **App Versioning (Completed)**: Versioning up happens automatically using GITHUB_RUN_NUMBER in build.gradle.kts to dynamically set versionCode and versionName.

### 🔍 Parser Engine Improvements (Completed)
* **Xtream Codes State-Machine**: The current parsers work very well, but there are insights to bolster them even more (ensure changes only increase recognition and do NOT break or impair current functionality). Analysis of "Hit Hunter" style pastebins (e.g. `├● 🔌 ᴍᴀᴄ : ... ├● 🌐 ᴘᴏʀᴛᴀʟ : ...`) reveals that automated checking tools often output credentials across multiple lines using unicode characters (e.g., `ᴜꜱᴇʀ`, `ᴩᴀꜱꜱ`, `ʜᴏꜱᴛ`). The parser should be upgraded with a multi-line state machine for Xtream combos (similar to the Stalker parser) to capture these disconnected host/user/pass blocks.


### 📱 Android Data Grid Parity & Auto-Scroll (Completed)
Addressed significant UX complaints regarding the Android app's tabular data displays:
* **Grid Refactoring**: Replaced bulky, slow-rendering Compose Cards with dense, native horizontal `LazyColumn` grids matching the Python Dataframes.
* **Extended Columns**: Implemented new data columns for Xtream Nodes (Expires, Active Conns, Max Conns, Channels, VODs, Timezone). Modified `IPTVClient.kt` to extract and parse the metadata locally.
* **Master-Detail Flow & Auto-Scroll**: Implemented a responsive fixed deep-dive drawer attached to the bottom of the screen. Upon clicking a node row, the view `animateScrollToItem()` snaps directly to the record while exposing action buttons cleanly.

### 📱 Android Advanced UI Actions & Bug Fixes (Completed)
* **Committed Tab Cloud Sync Crash**: Fixed a critical NPE crash by refactoring `CommittedRecord` parsing logic to utilize safe nullable getters, preventing the app from crashing when encountering null or missing JSON fields (e.g., `notes` or `dateAdded`) from the Python cloud database. Fixed syntax compilation error preventing build.
* **Deep-Dive Category Navigation Crash**: Resolved a core UI crash caused by rapid state changes when navigating backwards from Channel lists to Categories in the Xtream Details view. Re-engineered the LazyColumn generation to use immutable snapshot references of state arrays and unique Compose `key()` blocks to prevent scroll-state index-out-of-bounds crashes during recomposition.
* **Master Grid Data & Column Parity**: Upgraded the Android Xtream and Stalker tabs to display the full dataset found in the Python application. Added "Days Left" column adjacent to VOD counts to quickly determine account lifespan without opening details. Header counts now accurately reflect the active filtered list ("Showing X of Y").
* **Xtream Master Level Channel Query**: Implemented a "Query All Active" master button in the Xtream header. This loops through active nodes concurrently, fetching their Live Channel and VOD counts without requiring users to dive into each row individually. (Stalker architecture inherently blocks this, per documentation).
* **Active Connections Toggle**: Added "Active Only" toggle switches to both Xtream and Stalker tabs to filter out inactive nodes easily, accompanied by descriptive labels.
* **Floating Scroll Buttons**: Added persistent scroll-to-top and scroll-to-bottom Floating Action Buttons (FABs) across the Master Grid screens to quickly navigate lists containing thousands of nodes.
* **Connection Detail Actions**: Added quick action buttons to copy raw M3U Playlist URLs for Xtream nodes, enhancing copy-paste flexibility beyond discrete host/username/password combinations.
### 🐛 Android GitHub Actions Build Failure & Resolution (Completed)
* **Root Cause Analysis**: The GitHub Actions workflow (`android-build.yml`) failed during `compileDebugKotlin` due to three distinct code errors introduced during state management refactoring:
  1. `StalkerTab.kt` & `XtreamTab.kt`: State variables (`sortColumn`, `sortAscending`) were referenced in `when (sortColumn)` before their `var sortColumn by remember` declarations lower in `StalkerMasterGrid` / `XtreamMasterGrid`.
  2. `ScannerTab.kt`: `.awaitAll().awaitAll()` was called on a `List<Unit>` instead of `.awaitAll()`, causing a Kotlin receiver mismatch error.
  3. Missing Coroutine Imports: `async`, `awaitAll`, and `withContext` imports were missing or unimported across the tab files.
* **Resolution & Fix**:
  - Moved state variable definitions (`sortColumn`, `sortAscending`) to the top of `StalkerMasterGrid` and `XtreamMasterGrid` functions before filtered nodes are evaluated.
  - Fixed `.awaitAll()` invocation in `ScannerTab.kt`.
  - Added explicit coroutines imports (`import kotlinx.coroutines.async`, `import kotlinx.coroutines.awaitAll`, `import kotlinx.coroutines.withContext`) to `ScannerTab.kt`, `StalkerTab.kt`, and `XtreamTab.kt`.
  - Verified clean syntax and scoping across all tabs using `kotlinc`.
* **Build Verification**: GitHub Actions Run #57 (Run ID `31670370319`) completed with `success` status, producing the debug APK artifact cleanly.

### 🚀 CI/CD & Deployment Strategy Overview
* **Workflow Configuration (`.github/workflows/android-build.yml`)**:
  - Triggers automatically on `push` to `main` for changes inside `project-strong/android/**` or `.github/workflows/android-build.yml`.
  - Environment: `ubuntu-latest`, JDK 17 (Temurin), Gradle 8.7 (`setup-gradle@v3`).
  - Output Artifact: `project-strong-debug-apk` containing `app-debug.apk`.
* **Important Note on Path Filters**: Commits modifying only documentation (`GEMINI.md`) or top-level web files without touching `project-strong/android/**` intentionally bypass the Android APK build workflow to conserve GitHub Actions runner minutes.

### 🚧 Backlog & Priority Bugs for Next Session (To Implement)
1. **Scanner Lifecycle Controls**:
   - Add capabilities to Start, Stop, and Pause the ongoing scan process mid-flight.

### ⚡ Performance & UX Polish Updates (Completed)
* **1st Level Query Optimization**: 
  - **Android**: Rewrote the scanner array mapping logic. Instead of executing chunks sequentially (which caused huge lockups waiting on slow/dead hosts), the scanner now utilizes a unified `Semaphore` across all concurrent coroutines. This keeps maximum throughput perfectly saturated. Reduced `OkHttpClient` timeout bounds from 15s down to 7s to fail faster.
  - **Python (Streamlit)**: Added an `asyncio.Semaphore(25)` wrapper to the first-tier `evaluate_account` batch processor to prevent blasting 100+ uncontrolled concurrent requests instantly, keeping server CPU threads stable.
* **Connection Details Enhancements**:
  - **Xtream (Android)**: Removed the redundant "Query Channels & VODs" button from the details pane (users should rely on the master grid or "Load Categories" which handles this implicitly).
  - Added dedicated **Copy Icons** for the "Host URL" fields across both Xtream and Stalker detail screens to match the Username/Password UX.
  - Built a dynamic **M3U PLAYLIST URL** constructor inside the Xtream Details drawer, allowing quick 1-click copying of the fully assembled download link.
* **Committed Data Sync Logic**:
  - **Issue**: Clicking "Reload from Cloud" previously overwrote and wiped out any un-pushed local commits.
  - **Resolution**: Refactored `CommittedManager.kt`'s cloud synchronization to actively **merge** lists. Any local unpushed connections will now persist and append alongside the fetched cloud JSON.
  - Added a dedicated **"Push to Cloud"** action button in the Android `CommittedTab.kt`. Selecting this will prompt a one-time dialog requesting a GitHub Personal Access Token (stored safely in memory for the session) and will securely push the merged local lists up to the master branch using the GitHub REST API without overwriting cloud histories.

### 🐛 Bulk Connection Query Crash & OOM Fix (Completed)
* **Issue**: Triggering "Query All Active" connections caused severe performance degradation and crashed the app (OOM Exceptions / Freezing) when executing against large lists on the Xtream tab.
* **Resolution**: 
  - **Android**: Rewrote the JSON parsing logic in `IPTVClient.kt` to use Android's built-in `JsonReader` stream-parsing API for `getLiveStreamCount` and `getVodStreamCount`. This counts elements instantly with virtually zero memory footprint (bypassing loading 50MB JSON bodies into memory). Also added smaller chunked coroutine grouping.
  - **Python (Streamlit)**: Added an `asyncio.Semaphore(5)` limiter in `app.py` for the "Query Channels & VOD Counts" button to restrict the number of concurrent HTTP requests and prevent freezing the container.

### 📱 Android UI Next/Continue Workflow Navigation (Completed)
* **Issue**: "Next" or "Continue" buttons to move sequentially through tabs existed only on the initial Base64 Decode tab, creating a disjointed user flow.
* **Resolution**: Piped the `onNextTab` state control through `MainActivity.kt` directly down to `ScannerTab`, `XtreamTab`, and `StalkerTab`. Added elegant "Continue to..." primary buttons at the bottom of each respective master grid or parsing layout to allow a smooth, linear progression through the analytics pipeline.

### 📋 "Paste from Clipboard" Action Buttons (Completed)
* **Issue**: Input forms featured "Clear" buttons, but lacked corresponding "Paste from Clipboard" buttons.
* **Resolution**: Added dedicated 1-click "Paste" buttons using Android's `LocalClipboardManager` natively into the `Base64Tab` and `ScannerTab` UIs adjacent to the "Clear" and action buttons for rapid credential ingesting. *(Note: Paste buttons were strictly implemented in Android; Python Streamlit web apps natively block direct OS clipboard reading via JS security limits without user interaction events, so Ctrl+V is standard there).*

### 📊 16-Column Committed Data Grid & Master-Detail Navigation (Completed)
* **Master Grid Overhaul**: Expanded the Android `CommittedTab.kt` data table from a partial subset of fields to a comprehensive **16-column enterprise grid**, achieving complete structural parity with the Python dashboard and beyond:
  1. **Date Added** (Primary sorting column, formatted as `YYYY-MM-DD HH:MM:SS`)
  2. **Type** (Xtream / Stalker badges)
  3. **Status** (Color-coded indicators: 🟢 Active, 🔴 Expired, 🟡 Invalid)
  4. **Sync Status** (☁️ Cloud / 📱 Local badges)
  5. **Host URL** (Base URL with 1-click copy)
  6. **Provider** (Provider intelligence branding)
  7. **Username** (Xtream discrete credentials)
  8. **Password** (Xtream discrete credentials)
  9. **MAC** (Stalker hardware identity)
  10. **Channels** (Live stream count badge)
  11. **VODs** (Video on Demand count badge)
  12. **Days Left** (Account lifespan indicator)
  13. **Expires** (Date expiration timestamp)
  14. **Connections** (Active / Max allowed connection ratio)
  15. **Timezone** (Server regional timezone)
  16. **Notes** (Custom user annotations)
* **Default Sort & Header Controls**: Clicking any column header toggles ascending/descending sorts with clear indicator chevrons. Initial view loads default-sorted by **Date Added (Descending / Newest First)**.
* **Master-Detail Flow**: Selecting any row smoothly navigates into the full `CommittedDetailScreen`, exposing discrete credential copy widgets, full M3U Playlist generation, note editing, live verification re-checks, full-screen catalog exploration, and safe deletion.

### 🛡️ Cloud Overwrite Safeguards & Push Confirmation (Completed)
* **Accidental Overwrite Prevention**:
  - **Empty Push Guard**: Implemented strict validation in `CommittedManager.kt` to forbid pushing an empty local dataset over an existing remote database on GitHub.
  - **Confirmation Dialog**: Added an interactive safety modal in `CommittedTab.kt` triggered before pushing to the cloud. It displays total local records, sync status counts, and explicit warnings against unintentional deletions.
  - **GitHub Token Persistence**: Added safe in-memory caching for personal access tokens during the active session with one-click "Clear Stored Token" functionality.

### 🍞 Universal Non-Intrusive Toast Feedback System (Completed)
* **Global Toast Architecture**: Created `ToastManager.kt` and anchored the animated `ToastHost` at the root overlay in `MainActivity.kt`.
* **Instant Visual Feedback**: Replaced disruptive blocking dialogues and silent state updates with elegant, auto-dismissing Material 3 toasts for:
  - Account saves and commits across all tabs
  - Cloud synchronization and GitHub repository push completion
  - Note modifications and record deletions
  - Copying credentials (Host, Username, Password, MAC, M3U URL)
  - Token storage and clearing

### 🔄 End-to-End Metadata Synchronization (Completed)
* **Deep-Dive Parity**: Ensured `Active Connections`, `Max Connections`, `Provider`, and `Server Timezone` metadata captured during initial Tier 1 scans are seamlessly passed into `CommitAccountDialog.kt`, saved to `CommittedRecord`, persisted across local and cloud JSON schemas, and displayed in both master grids and detail screens.

### 🐛 Kotlin & Gradle Compilation Resolution (Completed)
* **Root Causes Addressed**:
  1. Fixed missing exhaustive `else` branches in `CommittedManager.kt` coroutines by replacing expression if-trees with structured `when (res)` matching.
  2. Fixed `AnimatedVisibility` receiver ambiguity in `FullScreenCatalogExplorer.kt` by wrapping toast banners inside explicit `Column` containers.
  3. Fixed mutable state smart-cast errors in `CommittedTab.kt` by passing immutable target states through `AnimatedContent(targetState = selectedRecord) { activeRecord -> ... }`.
* **Verification**: All Kotlin sources now compile cleanly under Gradle 8.7 and AGP 8.4 in GitHub Actions.

### 📱 UI/UX & Synchronization Enhancements (Completed)
* **Universal Toast System Stabilization**: Initialized `ToastManager` with Application Context in `MainActivity.kt` and added automatic main-thread fallback (`Handler(Looper.getMainLooper()).post`) to guarantee reliable toast execution across background coroutine tasks and foreground UI actions.
* **Hierarchical Channel Explorer (Collapsible Category Groups)**: Overhauled `FullScreenCatalogExplorer.kt` to present search results and full channel catalogs grouped by category headers. Groups are individually collapsible/expandable, displaying category names alongside total channel count badges and direct stream copy tools.
* **Action Button Text Truncation**: Refactored `PrimaryButton` and `SecondaryButton` in `ModernComponents.kt` to prevent text truncation, adding flexible horizontal padding, single-line text constraints (`maxLines = 1`, `softWrap = false`), and `overflow = TextOverflow.Ellipsis`.
* **Committed Data Tab Full-Screen Layout Fix**: Fixed the layout collapse issue in `CommittedMasterGrid` by applying `Modifier.fillMaxWidth().weight(1f)` to the table container `Box`. The 16-column enterprise grid now dynamically expands to fill all available vertical space instead of collapsing into a single row.
* **Discrete Push-to-Cloud for Local Records**: Added a dedicated `CloudUpload` push action button directly in the row actions of un-synced local records (`isLocalOnly == true`) in `CommittedMasterGrid` and within the `CommittedDetailScreen`. This enables one-click synchronization to the GitHub remote repository while strictly preserving the remote SHA fetch safeguards to prevent overwriting cloud records.
### 🐛 Critical UI Thread ANR & State Rendering Fixes (Completed)
* **Issue**: Rapid background scanning triggered Application Not Responding (ANR) lockups and erased the `LazyColumn` grids on the Xtream tab when processing massive data blocks (5000+ nodes) due to aggressive state recompilations and duplicate key collisions.
* **Resolution**:
  - **Adaptive Throttling**: Implemented an adaptive debounce/throttle inside the background `ScannerTab` and `XtreamTab` coroutines. Background HTTP scanning continues at maximum speed, but the Compose state list is only updated in batched intervals (twice per second), giving the Choreographer (UI thread) breathing room.
  - **Duplicate Key Protection**: Removed strict `key = { ... }` duplication bindings in `LazyColumn` for master grids. This prevents Compose from panicking and crashing the view when massive raw combo pastes generate multiple identical Host + Username records.
  - **Dynamic Chunked Streaming (Progressive Loading)**: Refactored the `XtreamTab` and `StalkerTab` to filter out `empty` or `isVerifying` nodes from the grid logic. Instead of rendering a sluggish 5000-row grid of empty statuses, the grids actively stream into existence, showing only nodes that have finished verifying.

### 🐛 Stream Catalog Crash & OOM Optimization (Completed)
* **Issue**: When clicking to query stream catalogs, the Android app crashed (`Could not load stream catalog from server`) or ran out of memory when parsing massive 50,000+ item provider payloads.
* **Resolution**: Completely rewrote the parsing architecture in `IPTVClient.getAllLiveStreams()` and `getLiveCategories()` from loading heavy, in-memory `org.json.JSONArray` blobs to using Android's native stream parser (`android.util.JsonReader`). This parses payloads piece-by-piece with near-zero memory footprint, and safely intercepts broken server responses (e.g., returning `{}` instead of `[]`) without throwing JSONExceptions.

### 📱 Xtream Tab UI Redesign & Top Header Polish (Completed)
* **Issue**: The Xtream Codes tab header felt cramped, and labels competed with action buttons horizontally using amateurishly large fonts.
* **Resolution**: Redesigned the Xtream Tab top header. 
  - Downscaled the massive `titleLarge` elements to a sleek `titleMedium`.
  - Separated the layout into dedicated, clean vertical rows: Counts and labels sit prominently at the top, while the "Query Catalogs" and "Active Only" toggles live in a separate action bar with proper padding, removing the cramped feeling.

### ☁️ Cloud Persistence "Zombie Records" Fix (Completed)
* **Issue**: Deleting records via the Android app correctly pushed the change to the Cloud (8 records remaining). However, the background AI workflow script (`git_push.cjs`) blindly merged its stale, cached memory (12 records) back into the push, restoring the deleted "zombie" records.
* **Resolution**: Purged the stale `committed.json` cache from the AI workspace environment and corrected the AI developer constraints to ensure cloud database files are respected rather than blindly merged during deployment pipelines.

### 📱 Android UI/UX & Forensic Diagnostics Polish (Completed)
* **Sherlock Holmes Cyber-Detective App Icon**: Created a high-tech vector asset (`ic_launcher_foreground.xml`) incorporating the classic Sherlock Holmes Deerstalker hat silhouette (crown, ear flaps, and dual visors) paired with an illuminated neon magnifying glass highlighting IPTV signal waves, grip rings, and cyber-reticle corner accents.
* **Playful Cartoon Sherlock Holmes Vector Icon Design**: Upgraded `ic_launcher_foreground.xml` and `ic_launcher_background.xml` to a vibrant cartoon aesthetic:
  - **Deerstalker Hat**: Rounded houndstooth crown with stylish top ribbon and exaggerated dual-bill visors.
  - **Calabash Detective Pipe & Streaming Vapor**: Polished briar/amber bowl with glowing ember blowing out playful, sculpted Wi-Fi / IPTV stream signal vapor clouds (`#38BDF8`, `#34D399`, `#67E8F9`).
  - **Oversized Glossy Magnifying Glass**: Electric cyan rim magnifying a glowing retro-modern TV screen with live streaming play symbol (`▶`), live signal bars, and glass glare reflections.
  - **Web Dashboard Live Previewer**: Integrated a multi-mask adaptive icon previewer (Squircle, Circle, Rounded Square, Teardrop) directly into the AI Studio web control panel.
* **Instant Hardware VPN Detection & Network Shield**: Added zero-latency hardware VPN sensing via `NetworkCapabilities.TRANSPORT_VPN` inside `NetworkMonitor.kt` and `MainActivity.kt`. The app updates its top header state immediately to `🛡️ VPN Active` without waiting on external HTTP checks. Created an interactive `ConnectionStateDialog` providing a real-time network breakdown (IP, ISP, Organization, Country) and manual diagnostic refresh.
* **Scanner Tab Metrics & Controls Separation**: Cleanly separated the action buttons ("Paste from Clipboard" and "Clear Payload") onto their own dedicated top row, and moved the Lines/Character count and Discovered Nodes badges to their own dedicated bottom status bar in `ScannerTab.kt`. Large counts (thousands of nodes) no longer crowd, wrap, or distort button alignments.
* **Master Grid Action Iconography**: Replaced generic refresh icons with the Search Magnifying Glass (`Icons.Default.Search`) across the master grids to intuitively convey inspecting channels and VODs, while sizing action columns appropriately (`140.dp` for Xtream, `110.dp` for Stalker) for seamless horizontal viewing.


---

## 🗺️ Next-Phase Strategic Roadmap & Architecture Specifications

### 1. Gap Analysis: Python Streamlit Web vs. Kotlin Jetpack Compose Android

| Feature Area | Python Streamlit Dashboard | Android Jetpack Compose Native App | Status / Action Plan |
| :--- | :--- | :--- | :--- |
| **Network Evasion & IP Risk** | Cloud Run / Streamlit Cloud IP ranges get heavily 403-blocked by IPTV firewalls. Requires proxy or local execution. | Runs directly on user's mobile/home WiFi residential carrier IP. Severely minimizes 403 blocks. | ✅ **Android Advantage** — Core architectural win. |
| **Ingestion / Base64 Decoder** | Base64 tab decodes links, strips garbage, allows 1-click open/copy. | Base64Tab exists with decode and copy, but lacks rich URL action cards and visual payload previews. | 🟡 **Gap to Close** — Upgrade Android Base64 tab to include link preview chips, batch URL launch, and direct scanner pipeline push. |
| **Discovery Scanner (Tier 1)** | Unthrottled asyncio concurrent scan with progress bars, status badges, summary counts. | High-speed coroutine worker with progressive chunked streaming (500ms batched UI refresh). | 🟢 **Parity Achieved** — Highly performant on both platforms. |
| **Bulk Catalog Querying** | Background async loop fetching channel & VOD totals on-demand. | Background coroutine worker with Pause, Resume, and Stop controls with live active count badges. | 🟢 **Parity Achieved**. |
| **Master-Detail & Data Grid** | Streamlit dataframes with single-row selection, auto-scroll injection to deep-dive drawer. | Horizontal scrolling 16-column LazyColumn table with header sorting, sort indicators, and auto-scroll snapping to detail screen. | 🟢 **Parity Achieved** — Android Master-Detail flow is fluid. |
| **Tier 2 Category & Channel Explorer** | Accordion views listing categories and stream counts. | FullScreenCatalogExplorer with grouped collapsible categories, search filtering, and 1-click stream URL copy. | 🟢 **Parity Achieved** — Android has superior categorized grouping. |
| **Provider Intelligence Engine** | Automatic fingerprinting, Telegram/Discord/WhatsApp scraper, dummy channel detector, JSON sync. | Provider parsed from domain and server responses, displayed in grids. Advanced Telegram/pattern extraction not yet ported. | 🟡 **Gap to Close** — Port regex brand-fingerprinting and community link detector into Android's `ProviderIntelligence.kt`. |
| **Settings & Intelligence Hub** | Basic sidebar configuration and session state toggles. | Full-featured Settings & Intelligence Tab with real-time hardware VPN sensor, outbound IP geolocation shield, dynamic timeout/concurrency sliders with instant auto-save, cache clearing, and GitHub PAT cloud sync. | 🟢 **Android Exclusive Feature Completed**. |
| **In-App Stream Playback & Telemetry** | None (Requires external player). | Full hardware-accelerated Media3 ExoPlayer with true full-screen, landscape sensor sync, scrub slider for non-live items, real-time bitrate (kbps/Mbps), buffer health cushion (seconds ahead), video/audio codecs, and VLC external player intent. | 🟢 **Android Exclusive Feature Completed**. |

---

### 2. Production UI/UX Overhaul & Visual Identity Design System

* **Visual Identity & Vector Branding**:
  * **App Name**: *Project Strong* / *StreamPulse Analytics* (or custom user-selected brand).
  * **App Icon**: Modern vector icon with high-contrast gradient (e.g. vibrant indigo/cyan broadcast signal intersecting an analytics wave), adaptive icon XML for Android 13+ with themed icon support.
  * **Splash & Header Banner**: Sophisticated ambient header with subtle glassmorphic blur and brand accent glow, eliminating flat, dated boxy headers.
* **Material 3 Design Language & Typography**:
  * **Typography Scale**: Strict mathematical typography scale pairing a clean geometric sans display font for titles (`titleMedium`, `labelLarge`) with refined, high-legibility body fonts (`bodyMedium`, `bodySmall` in `#A0A0B0`). Banning amateur oversized fonts.
  * **Card & Elevation Hierarchy**: Level 1 (Surface `#161622`), Level 2 (Elevated Card `#1E1E2E`), Level 3 (Active Focus `#2A2A3E` with 1.5dp `#3B82F6` border). Flat borders with calculated inner corner radii (`Inner = Outer - Padding`).
  * **Control Grouping**: Dedicated horizontal action bars with consistent 12-16dp padding; no cramping buttons and long status text in the same row.
  * **Animations & Micro-interactions**: Smooth `AnimatedContent` for tab transitions, subtle spring physics on button clicks, skeleton loaders for data grids during fetch, and pulsing status badges for active connections.

---

### 3. About & Settings Hub Screen

Create a dedicated **⚙️ Settings & About** tab or modal inside the Android app featuring:
* **Application Metadata**:
  * App Name, Dynamic Version Name & Version Code (auto-read from `BuildConfig`).
  * Build Type (Debug / Release), Target SDK (Android 14 / API 34).
  * Developer & Project Strong credits, link to GitHub repository.
* **Security & Outbound Network Shield**:
  * Real-time outbound IP address, ISP, and Organization detector (querying `ip-api.com` with cached protection).
  * Cloud/Hosting Firewall warning status (detecting AWS/GCP/DigitalOcean ranges).
* **GitHub Integration & Sync Preferences**:
  * Secure in-app GitHub Personal Access Token (PAT) configuration with validation indicator.
  * Default Repository Target (`Fragger7/personal-repo`) & branch config.
  * "Wipe Cached Token" and "Force Sync from Cloud" master actions.
* **Storage & Cache Management**:
  * Database statistics: Total local accounts, cloud accounts, cached provider intelligence records.
  * 1-click "Clear Volatile Scan Caches" (releasing memory without touching committed records).
* **Dynamic Theme Selector**:
  * *Midnight Purple*, *Ocean Blue*, *Crimson Red*, *Cyber Slate (Default)*, and *Dynamic Monet (System Material You)*.

---

### 4. Integrated In-App IPTV Stream & Channel Player (ExoPlayer / Media3 - COMPLETED & VERIFIED)

* **Architecture & Implementation (`StreamPreviewDialog.kt`)**:
  * **Media3 / ExoPlayer Engine**: Embedded hardware-accelerated playback pipeline utilizing custom OkHttp data source with evasion user-agent (`IPTVSmartersPro/1.1.1`), 500ms initial buffer handshake, software decoder fallback, and automated track selector.
  * **Auto-Hiding Floating Controls**: Player controls automatically fade out and slide away after 3 seconds of inactivity during video playback. Tapping anywhere on the video screen restores them instantly with smooth animated transitions.
  * **True Full-Screen Video**: One-tap full-screen toggle syncing with sensor landscape orientation (`ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE`), preserved across rotations via `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden"`.
  * **Aspect Ratio Cycling**: Switch between *Fit*, *Fill*, and *Zoom* on the fly using `AspectRatioFrameLayout`.
  * **Rich Control HUD**: Clear labeled buttons for Play/Pause, Mute/Unmute, Aspect Ratio, Live Re-Sync, Copy URL, and Open in External Player (VLC / MX Player).
  * **Scrub & Catchup Slider**: Dynamic slider with time formatting (`mm:ss` / `hh:mm:ss`) for VOD, catchup, and non-live timeshift streams.
  * **Forensic Diagnostics HUD**: Real-time telemetry monitoring Live Bitrate Throughput (kbps / Mbps), Buffer Cushion (seconds ahead), Video Resolution, Video Codec & FPS, Audio Encoding, and First-Frame Socket Latency.

---

### 5. Network & Handshake Forensics for Strict Xtream Providers (Ongoing Investigation)

* **Behavior Analysis (e.g. `bestiptvgo.net`)**:
  * Certain high-security or protected IPTV providers employ strict CDN firewalls (Cloudflare, DDoS-Guard, custom middleware) that reject or drop connections from unknown clients while passing commercial players (TiviMate, IPTV Smarters, XCIPTV) when operating behind a VPN.
  * **Investigative Vectors & Future Exploration**:
    * **TLS / SSL Cipher Suite & SNI Matching**: Android OkHttp default TLS ciphers vs native OpenSSL/BoringSSL handshakes in C++ players (VLC/libmpv/ijkplayer).
    * **HTTP Request Headers & User-Agent Parity**: Exact header sequencing (`Accept: */*`, `Accept-Encoding: gzip, deflate`, `Connection: Keep-Alive`, `User-Agent: IPTVSmartersPro/3.1.5.1` or `okhttp/3.14.9`).
    * **Redirect & Cookie Retention**: Ensuring HTTP 301/302/307 redirects across domains retain authentication parameters and session cookies.
    * **Stream Output Extensions**: Comparing server responses for `.ts`, `.m3u8`, `/live/{u}/{p}/{id}`, and `/play/{u}/{p}/{id}` variants.

---

## 🕵️‍♂️ Brand Identity: "Sherlock Streams"

* **Name Inspiration**: An homage to the legendary detective Sherlock Holmes, renowned for astute observation, deductive forensic analysis, and uncovering concealed details. In this application, the engine forensically examines, fingerprints, verifies, and catalogs every minute detail of complex, unstructured IPTV stream nodes and portals.
* **Visual Identity & Icon Metaphor Concept**:
  * **Icon**: A sleek, modern glowing neon magnifying glass intersecting an active digital audio/video waveform/pulse stream, set against a dark obsidian cyber-backdrop.
  * **Vector Brand Asset (`ic_sherlock_brand.xml`)**: Custom Sherlock silhouette with patched deerstalker hat, neon magnifying lens, and glowing stream signal, integrated into the Top App Bar and Settings Hub banner.
  * **Color Palette (Creative Deduction Palette)**: 
    * Primary: *Cyber Amber / Golden Brass* (`#F59E0B` / `#D97706`) + *Detective Navy / Deep Indigo* (`#0F172A` / `#1E1E2E`)
    * Accents: *Electric Cyan Stream Pulse* (`#06B6D4` / `#38BDF8`) for live telemetry & active connections
    * Status: *Emerald Green* (`#10B981`) for verified active nodes; *Crimson* (`#EF4444`) for dead/firewalled nodes.

---

## 🎯 Finalized Master Implementation Order & Backlog

### ✅ Completed Milestones
1. **Integrated In-App IPTV Stream & Channel Player (Media3 / ExoPlayer - VERIFIED & COMPLETED)**: Hardware-accelerated video playback modal with full-screen orientation lock, auto-hiding controls (3s inactivity timer / tap-to-show), labeled action buttons, scrub timeline slider, and live bitrate/buffer telemetry.
2. **Provider Intelligence & Forensic Brand Engine (Android Port - VERIFIED & COMPLETED)**: Real-time fingerprinting of server headers, welcome messages, dummy channel watermarks (e.g. `### Strong 8K ###`), and official community links (Telegram `t.me`, Discord `discord.gg`, WhatsApp `wa.me`). Interactive `ProviderIntelligenceCard` forensic drawer with 1-click copy/open, technical micro-grid (Server, CF-RAY, Timezone, Formats), and seamless bidirectional GitHub sync with `provider_intelligence.json`.
3. **Settings & Intelligence Hub**: Complete preferences tab with real-time VPN hardware monitor, IP geolocation shield, concurrency/timeout sliders with instant auto-save, cache clearing, and GitHub PAT sync.
4. **Responsive Multi-Orientation Detail Layouts**: Full vertical scrolling on all master-detail drawers (Committed, Xtream, Stalker, Scanner) and flexible channel title layouts in catalog explorer.
5. **Sherlock Streams Visual Branding**: Adaptive launcher icons and vector brand emblems across the UI.
6. **Universal Toast Architecture & Git Cloud Persistence**: Reliable main-thread feedback and bidirectional GitHub synchronization with safety merge guards.

### 🚀 Upcoming Active Backlog (Next Session Implementation Plan)
1. **Ultra-Scale Performance Tuning & ANR Prevention (3,000+ Node Payloads)**:
   * Implement chunked batch state emits to buffer background worker discovery updates and dispatch to UI state in 250ms intervals.
   * Apply coroutine dispatcher throttling (`Dispatchers.IO.limitedParallelism(24..32)`) and unified semaphores to prevent thread starvation during massive combo imports.
2. **Base64 Tab Power Actions & Ingestion Pipeline**:
   * Add rich URL action preview chips and batch external browser/M3U launcher.
   * Add 1-click "Send Decoded URLs to Scanner" direct pipeline button.
3. **Dynamic Theme Engine (Multi-Palette Switcher)**:
   * Material 3 dynamic color scheme engine supporting *Cyber Sherlock Amber/Navy (Default)*, *Midnight Purple*, *Ocean Blue*, *Crimson Dark*, and *System Monet*.
4. **Landscape Split-Pane Master-Detail Tablet/Foldable View**:
   * Expand wide screens into side-by-side master list + live detail inspector pane.

---

## 🛡️ Ironclad Rule: Non-Negotiable Regression Protection & Stable Baseline

### 1. Stable Production Baseline (v1.x Golden Snapshot - Verified Milestone)
* **Status**: The Android native application possesses 100% verified, crash-free stability on massive 5,000+ line unstructured payloads, dynamic chunked streaming, responsive master-detail grids, native JsonReader stream parsing, zero OOM errors, verified GitHub bidirectional synchronization, Sherlock Holmes vector adaptive launcher, streamlined vertical viewport, mini brand top bar emblem, and comprehensive explanatory settings info badges.
* **Golden Snapshot Tagging**: This baseline state (`v1.10+`) is the official reference build. All future additions must build strictly additively upon this golden foundation.

### 2. Verified Milestones & Brand Architecture
* **Brand Name**: Sherlock Streams ("The Digital Stream Detective")
* **Adaptive Launcher Icon**: Custom vector mascot featuring tweed deerstalker hat with patched textures, expressive smirk, detective trench coat/collar, and neon glowing stream play magnifying glass.
* **Top Bar Mini Emblem**: Compact emerald & cyan detective magnifying emblem alongside "Sherlock Streams" title without consuming extra vertical space.
* **Settings & Intelligence Hub**: Interactive settings modal equipped with detailed `SettingInfoCard` explanation badges on Concurrency, Timeout Latencies, Git Cloud Synchronization, Volatile Cache vs Git Vault, and Evasion Headers.

### 3. Next Milestone Candidates ("What's Next" Roadmap)
1. **Integrated In-App IPTV Stream & Channel Player (Media3 / ExoPlayer - COMPLETED)**:
   - Floating Picture-in-Picture & Modal stream inspector with ExoPlayer hardware-accelerated playback.
   - Stream health diagnostics overlay (Resolution e.g. `1080p FHD`, Video Codec e.g. `H.264/HEVC`, Audio Codec e.g. `AAC/AC3`, Latency ms).
   - Fast 1-tap quick stream tester inside the Full-Screen Channel & Catalog Explorer.
2. **Provider Intelligence & Forensic Brand Engine (Android Port - COMPLETED)**:
   - Upgraded provider recognition using category name watermarks (e.g., Strong 8K, T-Rex, Dream 4K, Dino), dummy/separator banner streams, and Telegram/Discord community channel signatures.
   - Server technical specs micro-grid (`Server`, `CF-RAY`, `timezone`, format capabilities) and bidirectional GitHub sync with `provider_intelligence.json`.
3. **Performance Tuning & Memory Backlog (HIGH PRIORITY)**:
   - Throttled coroutine chunking and memory-efficient recyclers on multi-thousand row payloads to ensure 60fps scrolling and eliminate any potential memory pressure.
4. **Base64 Tab Power Actions & Automation Pipeline**:
   - Automated Base64 chunk discovery with 1-click "Push to Scanner", URL action chips, and external M3U player launch intents.
5. **Dynamic Theme Engine (Multi-Palette Selection)**:
   - Dynamic switching between *Cyber Amber & Deep Indigo (Sherlock Default)*, *Midnight Purple*, *Ocean Blue*, *Crimson Red*, and *System Monet*.

### 2. Regression Testing & Safe Deployment Mandates
* **No Unsolicited Architecture Rewrites**: The core concurrent coroutine loop, OkHttp client setup, 500ms batched UI throttler, and JsonReader stream parsing logic in `IPTVClient.kt` and `ScannerTab.kt` MUST NOT be refactored or replaced without explicit regression testing.
* **Regression Testing Suite & Test Data**:
  * Prior to merging major features, run unit tests against realistic unstructured paste data (multi-line Xtream combos, Unicode character mappings like `ᴜꜱᴇʀ` / `ᴩᴀꜱꜱ`, Stalker MAC cookies, and broken/empty JSON object responses `{}` from non-standard servers).
  * If test datasets are needed, request Pastebin dumps from the user to stress-test candidate builds before pushing.
* **Dynamic / Flexible Roadmap Sequencing**:
  * Roadmap phases are flexible. If delivering a dependency requires prior UI infrastructure (e.g. building the **Settings & About Hub** first to house the Theme Selector before rolling out the multi-palette Theme Engine), the agent and developer should logically adapt the execution sequence without friction.
