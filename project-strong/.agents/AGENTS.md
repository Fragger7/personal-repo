# IPTV Playlist Analytics Dashboard - Agent Workspace Rules

This file defines the mandatory operational constraints and Git workflows for any AI agent or LLM taking on this project workspace. These rules are in place to guarantee security, maintain mono-repo folder boundaries, and align with Streamlit Cloud's automated hot-reload triggers.

---

## 🛑 Rule 1: Locate Git Executable
On this Windows host, the raw `git` command may not be in the default shell `PATH`.
* **Action**: If a plain `git` command fails, you **MUST** run all Git operations using the absolute path to the executable:
  `"C:\Program Files\Git\cmd\git.exe"`

---

## 🛑 Rule 2: Git Sync on Session Startup (Mandatory)
Before you edit any files, make any enhancements, or start a new working session, you MUST download the latest Git files from the remote GitHub repository (`https://github.com/Fragger7/personal-repo.git`) and review the updated knowledge.

1. **Pull Latest Files**:
   First, sync the local environment with the remote to prevent splitting histories.
   **On Windows**:
   ```powershell
   & "C:\Program Files\Git\cmd\git.exe" clone https://github.com/Fragger7/personal-repo.git "C:\Development\Apps\Project Strong\personal-repo-temp" --depth 1
   Copy-Item "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\*" "C:\Development\Apps\Project Strong\" -Recurse -Force
   Remove-Item -Recurse -Force "C:\Development\Apps\Project Strong\personal-repo-temp"
   ```
   **On Linux/AI Studio**:
   Use standard shell commands or node scripts to sync remote contents into the workspace root.

2. **Review Knowledge Data**:
   Once the repository files are pulled down, you **MUST** immediately read and review the contents of `GEMINI.md` and `.agents/AGENTS.md`. This ensures you are fully aligned with the latest architecture decisions, app flow, and deployment triggers before taking action.

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
* **Do NOT Hardcode Secrets**: Never commit access passwords, API keys, or raw credentials to the repository. Use `st.secrets["ACCESS_PASSWORD"]` for verification.
* **Keep Throttling Disabled**: The concurrency throttling (semaphores) has been removed at the user's instruction. Keep queries unthrottled but lazy-loaded (Tiers 1 and 2) to preserve user-experience speeds.
* **Preserve Logging**: Keep standard `logging` prints going to stdout so live diagnostics can be read inside Streamlit's dashboard logs.

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

---

## 🛑 Rule 7: World-Class UI/UX Design Standards
When building UI components, adding new screens, or refactoring the application interface, you **MUST act as a world-class UI/UX engineer**:
* **Apply Best Practices**: Design interfaces following the highest standards taught to the world's leading experts in UI/UX. 
* **Layout & Placement**: Ensure thoughtful, logical placement and grouping of controls (text boxes, buttons, checkboxes, toggles). Order elements to create a natural, intuitive flow for the user experience.
* **Consistency**: Maintain absolute continuity and professionalism across pages, tabs, and layout structures. Elements should not be placed "willy nilly". Functionality like sorting and filtering must behave cohesively everywhere.
* **Visual Polish & Feedback**: Handle theming, exact color scheme pairings, error messages, and user screen feedback with world-class polish. Provide elegant loading states, toast notifications, clear empty states, and visually reassuring success indicators.

---

## 📋 Next Session Backlog & Priority Fixes
1. **Scanner Lifecycle Controls**: Add capabilities to Start, Stop, and Pause the ongoing scan process mid-flight.
2. **Persistent App State**: Preserve scanner results across Android activity destruction/process death.

---

## 🏆 Recent Accomplishments & Milestone Log
* **Android UI Thread ANR & Rendering Stability Fixes**: Implemented adaptive throttling on background Compose state list updates to stop Choreographer starvation, and stripped strict LazyColumn duplication keys to prevent panicking on massive uncleaned pastes.
* **Stream Catalog OOM & JSON Crash Resolution**: Rewrote `IPTVClient.getAllLiveStreams()` and `getLiveCategories()` from in-memory JSONArray instantiation to stream parsing using `android.util.JsonReader`, eliminating out-of-memory errors on 50k+ payloads and safely aborting on malformed `{}` responses.
* **Xtream Tab UI Polish**: Grouped master counts/labels gracefully, downscaled the aggressively large font headers, and separated action toggles into horizontal padded action rows.
* **Cloud Push AI Workflow Corrections**: Addressed "zombie records" issue by ensuring the AI developer container clears local memory cache of `committed.json` and does not blindly overwrite intentional deletions with stale session cache.
* **16-Column Committed Data Grid**: Implemented full master table across Android with 16 columns matching Python metadata, sorting headers, and default sort by `Date Added (Descending)`.



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
| **Dynamic Multi-Theming** | 4 CSS visual themes (Midnight Purple, Ocean Blue, Crimson Dark, Clean Light). | Fixed Dark theme (Indigo/Slate). | 🟡 **Gap to Close** — Implement dynamic ThemeEngine in Android with Material 3 dynamic color palettes and theme switcher. |
| **Cloud Persistence & Git Sync** | Server-side REST Git pushes with automated comparison. | Full GitHub REST API sync with SHA verification, empty-push guards, toast feedback, and local/cloud badge tracking. | 🟢 **Parity Achieved**. |
| **In-App Stream Playback** | None (Requires external player). | None (Currently copy URL to clipboard). | 🚀 **New Frontier** — Integrate native ExoPlayer/Media3 player directly into Android catalog explorer for 1-click stream verification. |

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

### 4. Integrated In-App IPTV Stream & Channel Player (ExoPlayer / Media3)

* **Feasibility Analysis**:
  * **Verdict: Highly Recommended & Feasible.** Not too ambitious! Android has first-class media capabilities through `androidx.media3:media3-exoplayer` and `androidx.media3:media3-ui`.
  * **Architecture**:
    * Xtream Codes delivers direct MPEG-TS / HLS / MP4 stream URLs in the format: `http://{host}:{port}/live/{user}/{pass}/{stream_id}.ts` or `.m3u8`.
    * By integrating a lightweight `ExoPlayer` overlay directly inside the `FullScreenCatalogExplorer` or `CommittedDetailScreen`, the user can tap any channel in the catalog and immediately see a live video preview in a sleek, floating picture-in-picture or sheet player.
  * **Diagnostics & Stream Health Metrics**:
    * Live playback status (Buffering, Playing, Error with exact HTTP/codec code).
    * Stream technical stats: Resolution (e.g. `1080p60`, `4K`), Video Codec (`H.264`, `HEVC/H.265`), Audio Format (`AAC`, `AC3`), and Real-time Bitrate (kbps).
    * Quick "Stream Works" / "Stream Dead" diagnostic flag to annotate the playlist node!

---

## 🕵️‍♂️ Brand Identity: "Sherlock Streams"

* **Name Inspiration**: An homage to the legendary detective Sherlock Holmes, renowned for astute observation, deductive forensic analysis, and uncovering concealed details. In this application, the engine forensically examines, fingerprints, verifies, and catalogs every minute detail of complex, unstructured IPTV stream nodes and portals.
* **Visual Identity & Icon Metaphor Concept**:
  * **Icon**: A sleek, modern glowing neon magnifying glass intersecting an active digital audio/video waveform/pulse stream, set against a dark obsidian cyber-backdrop.
  * **Color Palette (Creative Deduction Palette)**: 
    * Primary: *Cyber Amber / Golden Brass* (`#F59E0B` / `#D97706`) + *Detective Navy / Deep Indigo* (`#0F172A` / `#1E1E2E`)
    * Accents: *Electric Cyan Stream Pulse* (`#06B6D4` / `#38BDF8`) for live telemetry & active connections
    * Status: *Emerald Green* (`#10B981`) for verified active nodes; *Crimson* (`#EF4444`) for dead/firewalled nodes.

---

## 🎯 Finalized Master Implementation Order

1. **Phase 1: Feature & Parity Gap Closures**
   * **Base64 Tab Upgrade**: Add rich URL action preview cards, batch external browser/M3U launch, and a direct 1-click "Send to Scanner" pipeline button.
   * **Provider Intelligence Engine (Android Port)**: Port the Python app's regex brand-fingerprinting, community link detector (Telegram `t.me`, Discord, WhatsApp), and separator/dummy channel identification into Android's `ProviderIntelligence.kt`.
   * **Dynamic Theme Engine**: Material 3 theme switcher with multi-palette selection (Cyber Sherlock Gold/Navy, Midnight Purple, Ocean Blue, Crimson Dark, and Dynamic Monet).

2. **Phase 2: Production-Level UI/UX Overhaul**
   * **Vector Icon & App Assets**: Create official Android 13+ adaptive vector icons with themed monochrome variant, splash branding, and ambient glassmorphic header glow.
   * **Playful Cartoon Sherlock Holmes Icon Experiment**: Design an alternative playful, expressive cartoon Sherlock Holmes caricature wearing the Deerstalker hat, a pipe blowing out Wi-Fi/streaming clouds, and an oversized magnifying glass reflecting a glowing TV screen.
   * **Mathematical Typography & Layout Polish**: Refine typography hierarchy across all tabs (banning oversized headers), implement 44dp min touch targets, and ensure clean vertical separation for all action bars.
   * **Micro-Interactions**: Fluid tab switching animations, skeleton loaders, and pulsing live diagnostic indicators.

3. **Phase 3: Ultra-Scale Performance Tuning (3,000+ Node Payloads & ANR Prevention)**
   * **Root Cause & Diagnosis**: Large unstructured payloads (3,000+ connections) can trigger Android ANR ("Application Not Responding / Wait or Close") prompts due to:
     1. High-frequency Compose state recompositions saturating the UI/Render thread.
     2. Frequent large-array memory copies (`+ item` or full list copies on each node scan).
     3. High-concurrency socket queueing under low Android thread priority.
   * **Architectural Mitigations**:
     * **Chunked Batch State Emits**: Buffer background worker discovery updates and dispatch to `DataStore.scannedNodes` in throttled batch intervals (e.g. every 250ms or every 50 nodes) to keep UI rendering locked at 60fps.
     * **Coroutine Dispatcher Throttling**: Use `Dispatchers.IO.limitedParallelism(24..32)` and unified coroutine `Semaphore` limits to prevent thread starvation.
     * **Virtual LazyList Pagination / Index Keys**: Ensure Compose `LazyColumn` uses stable compound keys (`key = { node.baseUrl + node.user }`) with lightweight view-model state mapping.

4. **Phase 4: Integrated In-App IPTV Stream & Diagnostics Player (Media3 / ExoPlayer)**
   * **Floating Mini-Player**: Initiates as a sleek picture-in-picture floating mini-player inside the category/channel explorer with play/pause and live buffer progress.
   * **Expand / Full-Screen Mode**: One-tap expand to a full-screen hardware-accelerated ExoPlayer interface.
   * **Forensic Diagnostics HUD**: Overlay stream telemetry showing real-time Resolution (e.g. `1080p60`, `4K`), Video/Audio Codecs (`H.264/HEVC`, `AAC/AC3`), and Live Bitrate (kbps) to immediately confirm stream health.

5. **Phase 5: Settings & About Hub**
   * **App & Build Metadata**: Sherlock Streams branding, dynamic version name/code from `BuildConfig`, target API, and developer credits.
   * **Outbound IP & ISP Shield**: Real-time IP geolocation and cloud firewall warning detector.
   * **GitHub Integration Controls**: Personal Access Token (PAT) manager, validation tester, and cache clearing utilities.

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
1. **Integrated In-App IPTV Stream & Channel Player (Media3 / ExoPlayer)**:
   - Floating Picture-in-Picture live preview inside the Channel & VOD Explorer drawer.
   - Stream health diagnostics overlay (Resolution e.g. `1080p60`, Video Codec `H.264`/`HEVC`, Audio Codec `AAC`/`AC3`, Bitrate kbps).
   - Instant "Stream Plays" / "Dead Stream" verification flag.
2. **Provider Intelligence Engine (Android Port)**:
   - Porting the regex brand fingerprinting and Telegram/Discord community link detectors into `ProviderIntelligence.kt`.
3. **Base64 Tab Power Actions**:
   - URL action cards, external M3U player launch intents, and direct 1-click pipeline to the Multi-Payload Scanner.
4. **Dynamic Theme Engine (Multi-Palette Selection)**:
   - Dynamic switching between *Cyber Amber & Deep Indigo (Sherlock Default)*, *Midnight Purple*, *Ocean Blue*, *Crimson Red*, and *System Monet*.

### 2. Regression Testing & Safe Deployment Mandates
* **No Unsolicited Architecture Rewrites**: The core concurrent coroutine loop, OkHttp client setup, 500ms batched UI throttler, and JsonReader stream parsing logic in `IPTVClient.kt` and `ScannerTab.kt` MUST NOT be refactored or replaced without explicit regression testing.
* **Regression Testing Suite & Test Data**:
  * Prior to merging major features, run unit tests against realistic unstructured paste data (multi-line Xtream combos, Unicode character mappings like `ᴜꜱᴇʀ` / `ᴩᴀꜱꜱ`, Stalker MAC cookies, and broken/empty JSON object responses `{}` from non-standard servers).
  * If test datasets are needed, request Pastebin dumps from the user to stress-test candidate builds before pushing.
* **Dynamic / Flexible Roadmap Sequencing**:
  * Roadmap phases are flexible. If delivering a dependency requires prior UI infrastructure (e.g. building the **Settings & About Hub** first to house the Theme Selector before rolling out the multi-palette Theme Engine), the agent and developer should logically adapt the execution sequence without friction.
