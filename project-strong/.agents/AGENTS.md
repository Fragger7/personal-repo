# 🕵️‍♂️ Sherlock Streams (IPTV Playlist Analytics & Forensic Dashboard)
# System Architecture & Developer Guide

This document contains the complete system architecture, operational decisions, cloud deployment triggers, Android native specifications, and step-by-step Git lifecycle workflows for the **Sherlock Streams (IPTV Playlist Analytics Dashboard)**. It is designed to serve as a comprehensive, self-contained knowledge source for both human developers and autonomous AI agents (LLMs) to seamlessly continue developing and maintaining the project.

---

## 📌 1. Project Overview & Brand Identity

* **Brand Name**: **Sherlock Streams** (*"The Digital Stream Detective"*)
* **Core Mission**: Parse, validate, forensic-fingerprint, and catalog IPTV playlist credentials (Xtream Codes API format & Stalker Portals) from messy, unstructured text pastes, Hit Hunter outputs, and Pastebin dumps with high-speed async evaluation, hardware-accelerated stream playback, and permanent Git-backed data archiving.
* **Dual-Platform Architecture**:
  1. **📱 Native Android Application (Kotlin + Jetpack Compose)** located in `/android`: Runs directly on the user's mobile carrier or home residential IP network to completely bypass cloud-hosting IP firewalls (HTTP 403) common to Streamlit/Cloud Run deployments. Features hardware-accelerated stream playback (Media3 / ExoPlayer), 16-column enterprise data tables, and full offline snapshot reading.
  2. **🐍 Python Streamlit Web Dashboard (`app.py`)**: Multi-tiered async scanner utilizing HTTPX evasion headers, dynamic multi-theming, automated provider intelligence fingerprinting, and bidirectional GitHub REST API persistence.

---

## 🏗️ 2. Architecture & Core Subsystems

### A. Security, Password Gate & Outbound Network Protection
* **Secure Access Gateway (Password Gate)**:
  * In production/cloud deployments, the app checks `st.secrets` or local configuration for `ACCESS_PASSWORD`. If configured, page rendering halts until the correct password is provided.
  * In fresh local environments where `ACCESS_PASSWORD` is absent, the gate automatically enters open developer mode.
* **Geolocation Outbound Network Shield & Hardware VPN Detection**:
  * **Python Dashboard**: Queries `http://ip-api.com/json/` (cached via `@st.cache_data(ttl=300)`) to check external IP, ISP, and Organization. If the hosting provider matches public clouds (AWS, GCP, Azure, Cloudflare, DigitalOcean), a prominent warning alerts the user to potential IPTV firewall blocks.
  * **Android Native**: Integrates zero-latency hardware VPN sensing via `NetworkCapabilities.TRANSPORT_VPN` inside `NetworkMonitor.kt`, immediately reflecting `🛡️ VPN Active` state on the top app bar without waiting for HTTP handshakes. Provides an interactive `ConnectionStateDialog` with real-time network breakdown (IP, ISP, Organization, Country).

---

### B. Multi-Tier Discovery & Ingestion Parser Engine
* **Universal Credential Scanner**:
  * **Xtream Codes Layouts**: Matches player API structures (`player_api.php?username=...&password=...`) and direct fallback endpoints (`get.php?...`).
  * **Multi-Line & Unicode State Machine**: Scans unstructured text blocks, tabular combos (`host:port user:pass`, `host:user:pass`), and automated Hit Hunter scanner headers containing unicode characters (e.g., `ᴜꜱᴇʀ`, `ᴩᴀꜱꜱ`, `ʜᴏꜱᴛ`, `├● 🔌 ᴍᴀᴄ`).
  * **Stalker Portals**: State-machine parser isolating MAC addresses (`00:1A:79:...`) and Portal URLs connected across fragmented text blocks.
* **Tier 1: Asynchronous Handshake Verification**:
  * **Xtream Endpoint**: GET request to `/player_api.php` testing authentication, account expiration, max connections, active connections, and server timezone.
  * **Stalker Endpoint**: High-speed handshake to `/server/load.php?type=stb&action=handshake` injecting MAG200 user-agent headers and MAC cookies.
  * **Diagnostic Status Mapping**:
    * `HTTP 403` ➔ `🛡️ Cloud Blocked (HTTP 403)`
    * `HTTP 521` ➔ `🔴 Offline (Server Dead)`
    * Other non-200 ➔ `🛡️ Firewalled / Blocked`
    * Unauthorized body ➔ `🟡 Expired / Invalid`
    * Valid response ➔ `🟢 Active`
* **Tier 2: Local Category Explorer & Live Catalog Streaming**:
  * On-demand async retrieval of `get_live_categories` and `get_live_streams`.
  * **Android Zero-Memory JsonReader**: Rewritten in `IPTVClient.kt` using Android's native `android.util.JsonReader` stream parser to count and parse 50,000+ channel payloads with near-zero memory footprint and no OOM crashes.
  * **Hierarchical Channel Explorer**: Collapsible category groups with fast search filtering, category badges, direct stream URL copying, and instant playback inspection.

---

### C. 🏛️ "Forever Source" Snapshot, Dual Traceability & Git Archive Engine
* **The Problem**: 
  1. Public pastebins, pastetext links, and ephemeral scrape dumps are frequently deleted or banned, resulting in lost context for discovered IPTV nodes.
  2. Ingesting threads (e.g. Reddit posts referencing Pastebin links) previously blurred the line between the thread link and the raw playlist link, occasionally mistaking the forum or paste host for an IPTV server host.
* **The Solution (Forever Source & Dual Traceability)**:
  1. **Dual Traceability Separation**:
     * **`source_link` (`sourceLink`)**: The direct raw IPTV payload URL (Pastebin, Paste.sh, Rentry, Dpaste, raw M3U).
     * **`origin_link` (`originLink`)**: The parent context, forum thread, or discussion URL where the payload was discovered (e.g. `reddit.com/r/...`, `t.me/...`, forum thread).
     * **Domain Blacklist Filter (`isBlacklistedHost`)**: Hardened regex and domain checks prevent non-IPTV domains (Reddit, Pastebin, Paste.sh, Telegram, Discord, GitHub, etc.) from ever being parsed or assigned as an IPTV `base_url`.
  2. **Snapshot Capture**: Upon scanning any text payload or pastebin URL, the ingestion engine captures a complete raw snapshot of the text block.
  3. **Git Repository Archive**: When an account is committed, the engine saves the raw text snapshot directly to the GitHub repository under **`project-strong/sources/{filename}.txt`** (e.g. `pastebin_com_xyz123.txt` or `raw_snapshot_hash.txt`) via the GitHub REST API.
  4. **Schema Association**: The snapshot filename is permanently stored in `committed.json` under `source_archive_file` (mapped to Kotlin's `CommittedRecord.sourceArchiveFile`) alongside `origin_link` (mapped to `CommittedRecord.originLink`).
  5. **In-App Monospace Source Viewer (`SourceArchiveViewerDialog.kt`)**:
     * Streams the cached snapshot directly from Git/local storage.
     * Monospace code viewer with line numbers, text filter/search, and word-wrap toggle.
     * **1-Click Actions**:
       * 📋 **Copy Snapshot**: Copies the entire raw text to clipboard (with robust fallback handling across Android versions).
       * ⚡ **Send to Scanner**: Directly injects the historical raw text into `DataStore.scannerInput` and routes the user into the Multi-Payload Scanner to re-verify all neighbor lines in that batch.
       * 🌐 **Open Original Source**: Launches the original external web link if still active.

---

### D. Provider Intelligence & Forensic Brand Engine
* **Multi-Layer Forensic Consensus**:
  * Analyzes HTTP response headers (`Server`, `Date` for server timezone, `CF-RAY` for Cloudflare, `X-Powered-By`).
  * Scrapes dummy/delimiter banner channels (e.g., `### Strong 8K ###`, `=== Movies ===`) and official community invite links (Telegram `t.me`, Discord `discord.gg`, WhatsApp `wa.me`).
  * Yields 99% cross-verified confidence ratings (`Verified Brand`, `Category Watermark`, `Domain Heuristic`).
* **Bundled Knowledge Base**:
  * Bundles 2,127+ provider forensic profiles in `provider_intelligence.json` and Android assets for instant zero-latency offline brand recognition on first boot.
  * Synchronizes bidirectional updates seamlessly to GitHub repository when new fingerprints are detected.

---

### E. In-App IPTV Stream Player (Media3 / ExoPlayer)
* **Embedded Hardware Playback (`StreamPreviewDialog.kt`)**:
  * Hardware-accelerated playback with OkHttp custom evasion data source (`User-Agent: IPTVSmartersPro/1.1.1`), 500ms buffer handshake, and software decoder fallback.
  * **Auto-Hiding HUD**: Floating controls slide and fade away after 3 seconds of user inactivity; tap anywhere to reveal.
  * **True Full-Screen Video**: One-tap full-screen toggle linked with landscape orientation sensor (`ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE`).
  * **Aspect Ratio Cycling**: Switch between *Fit*, *Fill*, and *Zoom* using `AspectRatioFrameLayout`.
  * **Live Forensic Telemetry**: Real-time monitoring of Bitrate Throughput (kbps/Mbps), Buffer Health Cushion (seconds ahead), Video Resolution, Video/Audio Codecs, and Socket Latency.
  * **Scrub & Catchup Slider**: Dynamic slider with time formatting for VOD and non-live timeshift streams.
  * **External Player Intent**: 1-tap handoff to VLC or MX Player.

---

### F. 16-Column Enterprise Data Grid & Master-Detail Navigation
* **Android Master Grid (`CommittedTab.kt`, `XtreamTab.kt`, `StalkerTab.kt`)**:
  * Dense, high-performance horizontal scrolling `LazyColumn` tables with column sorting (Date Added, Type, Status, Sync Status, Host, Provider, Username, Password, MAC, Channels, VODs, Days Left, Expires, Connections, Timezone, Notes).
  * Auto-scrolling drawer snapping to the **Deep-Dive Drawer** upon clicking any row.
  * Discrete login credential copy widgets (separating Host, Username, and Password into individual 1-click copy boxes).
  * **Dynamic M3U URL Constructor**: Instant 1-click assembled download link generator.
* **Committed Record Deletion & Permanent Cloud Sync**:
  * Deleting records updates local storage and triggers a direct deletion update to `committed.json` via GitHub API.
  * Safeguards in `CommittedManager.kt` prevent empty-list overwrites and ensure deleted records are permanently removed without "zombie" rollbacks.

---

### G. Settings & Intelligence Hub (`SettingsTab.kt`)
* Real-time hardware VPN sensor & outbound IP geolocation shield.
* Dynamic timeout & concurrency sliders with instant `SharedPreferences` auto-save.
* Multi-theme palette switcher: *Cyber Sherlock Amber/Navy (Default)*, *Midnight Purple*, *Ocean Blue*, *Crimson Dark*, and *System Monet*.
* Safe GitHub Personal Access Token (PAT) in-memory/session storage with 1-click "Clear Stored Token" action.
* Volatile cache clearance without touching committed records.

---

### H. Stream Egress & Ghost Line Verification Engine (`probeStreamEgress`)
* **The Problem (Ghost Lines & Egress Blocks)**:
  * Certain IPTV providers return HTTP 200 on `/player_api.php` authentication and report thousands of active channels, but completely block actual media streaming with **HTTP 456** (Stream Egress Disabled / Token Revoked) or **HTTP 884** (Anti-Dump Lockout / ISP Filtering).
* **The Solution**:
  * **Zero-Memory Streaming Sampling**: Uses Android's low-level `JsonReader` to sample stream IDs directly from the network socket without loading huge channel arrays into RAM.
  * **Dual-Format Byte Validation**: Probes raw `.ts` MPEG-TS chunks and `.m3u8` HLS playlist headers with verified byte egress confirmation and round-trip socket latency benchmarking.
  * **Consensus-Based Decision Engine**: Differentiates between intermittent CDN network timeouts (`Inconclusive`) vs. definitive provider lockout (`Ghost Line 456` or `Anti-Dump 884`).
  * **Live Visual Progress UI**: Real-time progress bar, step description, and sample counter inside the deep-dive drawer during probing.
  * **Deep Scan & Settings Integration**: Configurable socket timeout (2-15s), sample stream count (1-5), and automated execution during batch deep queries (`autoEgressOnDeepScan`).

---

### I. Fast-Fail Hedging & Scan Stalling Protection
* **Tail-Latency Elimination**: When evaluating dead, non-existent, or timed-out hosts, the network engine skips redundant User-Agent retries on socket timeouts, `UnknownHostException`, and `ConnectException` when fast-fail hedging is enabled.
* **Coroutines Timeout Guard**: Bounded with `withTimeoutOrNull` limits on batch operations so unclosed remote sockets never stall the scan worker pool.

---

### J. Landscape Layout Optimization & Full-Width Data Grids
* **Full Viewport Master Grids**:
  * On mobile devices in landscape orientation, data grids across `CommittedTab.kt`, `XtreamTab.kt`, and `StalkerTab.kt` span the entire viewport width (`1.0f`), removing cramped split-pane rows. This affords the 16 enterprise data columns maximum horizontal breathing room.
  * Clicking any row triggers a seamless transition to the comprehensive Deep-Dive Inspector view with back navigation (`selectedNode = null` / `selectedRecord = null`).
* **Compact Single-Row Action Headers**:
  * Top control and filter cards dynamically detect landscape mode via `LocalConfiguration.current.orientation`.
  * Header layout condenses from a tall 2-tier stacked card (~140dp) into a single streamlined row (~50dp) with tighter padding (`vertical = 8.dp`), positioning Title & Status badges on the left and Filter toggles & Action buttons on the right.
* **Full-Height Vertical Scrolling Anchor**:
  * Resolves vertical viewport constraints (~360dp–400dp on phones) by binding the horizontal-scrolling container with `Column(modifier = Modifier.fillMaxHeight())` and anchoring the table list with `LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), state = listState)` so records scroll smoothly from top to bottom.

---

## 🎨 3. World-Class UI/UX Design Standards

* **Typography Scale**: Pairing geometric display headers (`titleMedium`, `labelLarge`) with refined body fonts (`bodyMedium`, `bodySmall` in `#A0A0B0`). No amateur oversized fonts.
* **Card & Elevation Hierarchy**: Flat borders with mathematically calculated inner corner radii (`Inner Radius = Outer Radius - Padding`).
* **Visual Polish & Feedback**: Material 3 non-intrusive auto-dismissing toast notifications via `ToastManager.kt`, skeleton loaders during fetch, and pulsing status badges for active connections.

---

## 🛠️ 4. Technology Stack & Dependencies

* **Android Native**:
  * Language: Kotlin 1.9.24+
  * UI Framework: Jetpack Compose (Material 3)
  * Media: AndroidX Media3 (ExoPlayer 1.3.1)
  * Networking: OkHttp 4.12.0 + Coroutines
  * Build Tooling: Android Gradle Plugin (AGP) 8.4.0, Gradle 8.7, JDK 17 (Temurin)
* **Python Dashboard**:
  * Framework: Streamlit
  * Networking: HTTPX (Async client with evasion headers)
  * Data: Pandas, JSON, Asyncio

---

## ☁️ 5. CI/CD & Automated APK Build Pipeline

* **GitHub Action Workflow (`.github/workflows/android-build.yml`)**:
  * Triggers on every `push` to `main` modifying `project-strong/android/**` or the workflow itself.
  * Environment: `ubuntu-latest`, JDK 17, Gradle 8.7 (`gradle/actions/setup-gradle@v3`).
  * Action: Runs `./gradlew assembleDebug` and uploads `app-debug.apk` as the `project-strong-debug-apk` artifact.
* **Target Repository**: `https://github.com/Fragger7/personal-repo`
* **Target Branch**: `main`

---

## 🤖 6. AI Agent Git Operations Lifecycle (Crucial for Autonomous LLMs)

To keep the repository clean, prevent mono-repo collisions with sister projects at the root of `personal-repo`, and avoid pushing unrequested files, always follow these workflows:

### A. AI Studio Automated Script (`git_push.cjs`)
When running within AI Studio or serverless container environments:
1. Ensure `GITHUB_TOKEN` is present in Settings -> Secrets.
2. Execute:
   ```bash
   node git_push.cjs
   ```
3. The script:
   - Clones `Fragger7/personal-repo` into an isolated temporary folder.
   - Automatically bumps Android `versionCode` and `versionName` in `build.gradle.kts`.
   - Copies modified project files strictly into `project-strong/` (and `.github/` to root).
   - Commits and pushes to `origin main`.
   - Cleans up the temporary clone completely.

### B. Manual PowerShell Lifecycle (Windows Development)
```powershell
# Step 1: Clone remote repository to temporary folder
& "C:\Program Files\Git\cmd\git.exe" clone https://github.com/Fragger7/personal-repo.git "C:\Development\Apps\Project Strong\personal-repo-temp"

# Step 2: Copy updated workspace files to personal-repo-temp/project-strong/
Copy-Item "C:\Development\Apps\Project Strong\app.py" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\app.py" -Force
Copy-Item "C:\Development\Apps\Project Strong\.gitignore" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\.gitignore" -Force
Copy-Item "C:\Development\Apps\Project Strong\requirements.txt" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\requirements.txt" -Force
Copy-Item "C:\Development\Apps\Project Strong\GEMINI.md" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\GEMINI.md" -Force
Copy-Item "C:\Development\Apps\Project Strong\run.bat" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\run.bat" -Force
Copy-Item "C:\Development\Apps\Project Strong\committed.json" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\committed.json" -Force -ErrorAction SilentlyContinue
Copy-Item "C:\Development\Apps\Project Strong\provider_intelligence.json" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\provider_intelligence.json" -Force -ErrorAction SilentlyContinue
Copy-Item "C:\Development\Apps\Project Strong\sources" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\sources" -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item "C:\Development\Apps\Project Strong\android" "C:\Development\Apps\Project Strong\personal-repo-temp\project-strong\android" -Recurse -Force

# Step 3: Stage, commit, and push from personal-repo-temp
cd "C:\Development\Apps\Project Strong\personal-repo-temp"
& "C:\Program Files\Git\cmd\git.exe" add project-strong/
& "C:\Program Files\Git\cmd\git.exe" commit -m "Update: Sherlock Streams enhancements"
& "C:\Program Files\Git\cmd\git.exe" push origin main

# Step 4: Clean up temporary folder
Remove-Item -Recurse -Force "C:\Development\Apps\Project Strong\personal-repo-temp"
```

---

## 🎯 7. Master Status & Completed Milestones

| Milestone | Subsystems Involved | Status |
| :--- | :--- | :--- |
| **"Forever Source" Snapshot Archive** | `SourceArchiveManager.kt`, `SourceArchiveViewerDialog.kt`, `committed.json`, `app.py` | 🟢 **Verified & Live** |
| **Media3 / ExoPlayer In-App Stream Inspector** | `StreamPreviewDialog.kt`, ExoPlayer 1.3.1, OkHttp data source | 🟢 **Verified & Live** |
| **Provider Intelligence & Brand Forensics** | `ProviderIntelligenceCard.kt`, `provider_intelligence.json`, consensus scoring | 🟢 **Verified & Live** |
| **16-Column Enterprise Data Grids** | `CommittedTab.kt`, `XtreamTab.kt`, `StalkerTab.kt`, `JsonReader` streaming | 🟢 **Verified & Live** |
| **Hierarchical Collapsible Channel Explorer** | `FullScreenCatalogExplorer.kt`, Category group badges, search filter | 🟢 **Verified & Live** |
| **Hardware VPN & Geolocation Shield** | `NetworkMonitor.kt`, `ConnectionStateDialog.kt`, `ip-api.com` caching | 🟢 **Verified & Live** |
| **Multi-Palette Dynamic Theme Switcher** | `Theme.kt`, `SettingsTab.kt`, `SharedPreferences` persistence | 🟢 **Verified & Live** |
| **Source Link Traceability** | Discovered URL cards, discrete copy, source link columns across all tabs | 🟢 **Verified & Live** |
| **Permanent Deletion & Git Synchronization** | `CommittedManager.kt`, SHA-aware GitHub API commit/delete pipeline | 🟢 **Verified & Live** |
| **Sherlock Streams Visual Branding & Mascot** | `ic_launcher_foreground.xml`, `ic_sherlock_brand.xml`, top bar emblem | 🟢 **Verified & Live** |
| **CI/CD Automated APK Compilation** | `.github/workflows/android-build.yml`, Gradle 8.7, AGP 8.4.0 | 🟢 **Verified & Live** |
| **Paste.sh Decryption & Large-Buffer Layout Shield** | `IPTVClient.kt`, `ScannerTab.kt`, `app.py`, AES-256-CBC multi-line/single-line payload parser & bounded Compose text field | 🟢 **Verified & Live** |
| **Dual Traceability (Source & Origin) & Domain Shield** | `CommittedManager.kt`, `CommitDialog.kt`, `XtreamTab.kt`, `StalkerTab.kt`, `CommittedTab.kt`, `app.py` | 🟢 **Verified & Live** |
| **Base64 De-obfuscation & Auto-Traceability Extraction** | `Base64Tab.kt`, `ScannerTab.kt`, `Parser.kt`, Regex auto-link capture & 1-tap clipboard paste | 🟢 **Verified & Live** |
| **Stream Egress & Ghost Line Verification Engine** | `IPTVClient.kt`, `CommittedManager.kt`, `XtreamTab.kt`, `CommittedTab.kt`, `SettingsTab.kt`, `SettingsDialog.kt`, `StreamPreviewDialog.kt`, `app.py`, HTTP 456/884 consensus probing | 🟢 **Verified & Live** |
| **Landscape Full-Width Data Grids & Viewport Optimization** | `CommittedTab.kt`, `XtreamTab.kt`, `StalkerTab.kt`, `weight(1f)` scroll binding, single-row compact action headers | 🟢 **Verified & Live** |

---

## 🚀 8. Upcoming Active Backlog & Next Implementation Tasks

1. **Foldable / Large-Screen Dual-Pane Adaptation**:
   * For tablets and foldables with screen width exceeding 840dp (`WindowWidthSizeClass.Expanded`), provide an optional layout toggle to enable side-by-side master-detail inspector layout, while preserving full-width mode for standard phone landscape displays.
2. **Automated Weekly Provider Intelligence Sync (GitHub Actions Cron)**:
   * Scheduled workflow (`.github/workflows/scrape-provider-intel.yml`) to scrape new upstream provider delimiters and sync `provider_intelligence.json`.
3. **Advanced Stream Output Formats & TLS Evasion**:
   * Add options for custom TLS cipher suites and alternative stream format switching (`/live/{u}/{p}/{id}.ts` vs `.m3u8` vs `/play/`).


## ☠️ 9. Buried Skeletons & Deep Engineering Constraints (AI Context)

**CRITICAL**: Any AI agent or developer modifying this codebase MUST read this section to prevent introducing regressions into highly tuned, fragile subsystems.

*   **Landscape Grid Viewport & LazyColumn Weighting Trap (`CommittedTab.kt`, `XtreamTab.kt`, `StalkerTab.kt`)**:
    *   *The Skeleton*: Placing an unweighted `LazyColumn` or a 45%/55% split-pane row on mobile landscape screens cuts off rows or leaves zero vertical scroll space (phone landscape height is typically only ~360-400dp, with the app bar, top card, and navigation bar leaving very little height). A 45% width split on phone landscape leaves only ~350dp width, making a 16-column horizontal table virtually unreadable.
    *   *The Fix*: Render data grids at full screen width on mobile landscape, collapse header action cards into a single compact horizontal line (`padding(vertical = 8.dp)`), and anchor the table with `Column(modifier = Modifier.fillMaxHeight())` and `LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), state = listState)` so it dynamically consumes all remaining vertical height.
*   **The UI Engine "Glassmorphism" Trap (Jetpack Compose `RenderEffect`)**:
    *   *The Skeleton*: Do NOT attempt to build global "glassmorphism" or heavy `RenderEffect.createBlurEffect` UI wrappers (e.g., overriding standard `Card` or `Button` components). Applying a `RenderEffect` blur to a Compose `Modifier.graphicsLayer` on a `Card` blurs the *entire content* of the card (text, buttons, layout), rendering it into an illegible blob. Furthermore, injecting `.scale()` animations globally on buttons breaks hitboxes and layout constraints.
    *   *The Fix*: Stick strictly to the standard Material 3 components (`Card`, `Button`, `OutlinedTextField`) and the crisp, highly functional fallback themes defined in `Theme.kt`. Avoid massive global UI framework overrides. True systemic theming in this app relies on Material's `colorScheme`, not overriding the foundational composables.
*   **The OOM (Out Of Memory) JSON Trap (`IPTVClient.kt`)**: 
    *   *The Skeleton*: Calling `get_live_streams` or `get_vod_streams` on an Xtream API can return a 50MB - 100MB+ JSON array containing 100,000+ media objects. Using standard `Gson`, `Moshi`, or `kotlinx.serialization` to parse this into memory will instantly crash Android devices with an OOM (Out of Memory) exception.
    *   *The Fix*: We use Android's low-level `android.util.JsonReader` to stream-parse the response token-by-token directly from the OkHttp network socket buffer, maintaining a near-zero memory footprint. Do **NOT** attempt to refactor the catalog explorer back to standard object mapping.
*   **Garbage Collection (GC) Thrashing (`Parser.kt`)**:
    *   *The Skeleton*: Scanning a 10,000-line paste dump by dynamically instantiating Regex objects inside loops causes massive JVM GC thrashing, completely freezing the Android UI thread for 10+ seconds.
    *   *The Fix*: All 15+ complex regex patterns (Xtream, MAC, Tabs, URLs, Timezones) are pre-compiled as top-level singletons in `Parser.kt`. Do not declare new Regex objects inside the `parse()` loop.
*   **Anti-Bot Network Evasion**: 
    *   *The Skeleton*: Many providers actively block default HTTP clients (like OkHttp default UA or Python's `requests`).
    *   *The Fix*: `IPTVClient.kt` hardcodes `User-Agent: IPTVSmartersPro/1.1.1` for Xtream endpoints. Stalker portal endpoints are even stricter—they require injecting the MAC address into cookies (`mac=00:1A:79...`) and using older Set-Top Box user-agents (e.g., MAG250/200). 
*   **Thread-Safe Git Syncing (`CommittedManager.kt`)**: 
    *   *The Skeleton*: The database (`committed.json`) is essentially a flat JSON file. Rapid consecutive UI clicks (like deleting 5 records fast) used to cause race conditions, resulting in empty/corrupted JSON file writes.
    *   *The Fix*: The file is now backed by a Kotlin `Mutex` lock during local `save()` operations on `Dispatchers.IO`. Cloud pushes (`pushToCloud()`) happen asynchronously and gracefully fail without interrupting the UI. Never write to `committed.json` outside of this Mutex.
*   **Mono-Repo Git Constraint (`git_push.cjs`)**:
    *   *The Skeleton*: The target GitHub repository contains other sister projects at its root. 
    *   *The Fix*: You must only push using the `node git_push.cjs` script. It strictly isolates modifications to the `project-strong/` sub-directory. Running standard `git push` manually from the AI Studio root will overwrite or delete the user's other repository contents.
