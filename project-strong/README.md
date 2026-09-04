# 🕵️‍♂️ Sherlock Streams (IPTV Playlist Analytics & Forensic Dashboard)

A dual-platform forensic analytics engine and IPTV stream management dashboard designed to parse, validate, categorize, hardware stream-inspect, and archive IPTV credentials (Xtream Codes API & Stalker Portals) from unstructured raw text pastes.

---

## 📱 Android Native Application (Kotlin + Jetpack Compose)

The native Android app (`/android`) runs directly on mobile/residential IP connections to completely bypass cloud hosting IP blocks (HTTP 403) encountered on web deployments.

### ✨ Key Features
* **🏛️ "Forever Source" Git Snapshot & Archive Engine**:
  * Automatically captures raw pastebin/pastetext dumps and saves them as versioned text files directly in the Git repository (`project-strong/sources/{filename}.txt`).
  * In-app monospace source reader with line numbering, text search, raw copy, upstream URL launcher, and 1-click **"⚡ Send to Scanner"** recall pipeline.
* **Git-Native OTA Update Engine**:
  * Fully autonomous background polling via the GitHub REST API to detect new releases (`AppUpdater.kt`).
  * Direct raw `.apk` binary streaming using OkHttp with secure `FileProvider` handoff to the Android OS Package Installer.
  * In-place seamless upgrades using a permanent stable cryptographic keystore to preserve all local `DataStore` and offline records across updates.
* **Media3 / ExoPlayer In-App Stream Inspector**:
  * Hardware-accelerated direct stream playback with custom evasion user-agent (`IPTVSmartersPro/1.1.1`) and software decoder fallback.
  * True full-screen mode linked with sensor landscape orientation (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`).
  * Live forensic telemetry HUD: Bitrate throughput (`kbps` / `Mbps`), buffer health cushion (`seconds ahead`), video resolution, video/audio codecs, and socket latency.
  * Clear labeled controls: Play/Pause, Mute/Unmute, Aspect Ratio (Fit/Fill/Zoom), Live Re-Sync, Copy URL, and Open in External Player (VLC / MX Player).
  * Scrub slider with dynamic time formatting (`mm:ss` / `hh:mm:ss`) for non-live and catchup streams.
* **16-Column Enterprise Master Grid & Detail Snapping**:
  * High-density horizontal `LazyColumn` grids with multi-column sorting (Date Added, Status, Type, Sync Status, Host, Provider, Username, Password, MAC, Channels, VODs, Days Left, Expires, Connections, Timezone, Notes).
  * Master-Detail navigation with auto-scrolling to the deep-dive inspector drawer.
  * Discrete login credential copy widgets (Host, Username, Password, M3U URL).
* **Dual Traceability (Source & Origin) & Forensic Domain Shield**:
  * Separates payload URLs (`sourceLink`) from discussion/forum URLs (`originLink`).
  * Hardened blacklist filtering prevents Reddit, Telegram, Discord, and Pastebin URLs from being mistaken for IPTV host endpoints.
  * Dedicated Traceability cards with 1-tap clipboard paste and automatic regex extraction across input texts.
* **Base64 De-obfuscation & Deep Payload Extraction**:
  * Multi-layer recursive Base64 decoder with URL unwrapping and Unicode normalization.
  * 1-click batch routing to Multi-Payload Scanner with attached source/origin context.
* **Paste.sh AES-256-CBC Decryption & Remote Playlist Retrieval**:
  * On-the-fly decryption of encrypted Paste.sh URLs and hashes.
* **Provider Intelligence & Brand Forensics**:
  * Bundles 2,127+ offline provider forensic profiles for instant brand detection.
  * Delimiter scraper (Telegram, Discord, WhatsApp, banner channels) with 99% consensus verification.
* **Hierarchical Channel & VOD Catalog Explorer**:
  * Collapsible categorized channel lists with fast search filtering, individual category counters, and 1-tap stream testing.
* **Stream Egress & Ghost Line Verification Engine (`probeStreamEgress`)**:
  * Probes raw `.ts` MPEG-TS chunks and `.m3u8` HLS playlist headers with verified byte egress confirmation and round-trip socket latency benchmarking.
  * Detects stealth **HTTP 456** (Ghost Line / Stream Disabled) and **HTTP 884** (Anti-Dump Lockout) consensus blocks.
  * Live visual progress container with step descriptions, sample counter (`current / total`), and progress indicator.
  * Tunable settings: Auto-probe on deep scan, socket timeout slider (2-15s), and channel sample size (1-5 streams).
* **Fast-Fail Hedging & Tail-Latency Elimination**:
  * Skips redundant User-Agent retries on socket timeouts and connection refusals to prevent stalling the worker pool at the end of batch scans.
  * Thread-safe coroutine timeouts wrapping large channel queries.
* **Settings & Intelligence Hub**:
  * Real-time zero-latency hardware VPN detector (`NetworkCapabilities.TRANSPORT_VPN`).
  * Outbound IP geolocation shield and public cloud firewall warning indicator.
  * Live concurrency & timeout sliders with instant preference auto-save.
  * Multi-theme switcher (*Cyber Sherlock Amber/Navy*, *Midnight Purple*, *Ocean Blue*, *Crimson Dark*, *System Monet*).
  * Safe GitHub Personal Access Token (PAT) cloud synchronization and cache clearing tools.
* **Universal Toast System & Multi-Orientation Layouts**:
  * Non-intrusive auto-dismissing visual toasts across foreground and background coroutine events.
  * Full vertical scrolling across all detail views in portrait and landscape orientations.
* **Landscape Full-Width Master Grids & Viewport Optimization**:
  * Full-width display (`1.0f`) across `CommittedTab`, `XtreamTab`, and `StalkerTab` to maximize horizontal column space for 16-column enterprise tables.
  * Dynamic compact single-row header action toolbars (`padding(vertical = 8.dp)`) that conserve precious vertical viewport space on landscape screens.
  * Rigid vertical scrolling anchor with `Column(modifier = Modifier.fillMaxHeight())` and `LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), state = listState)` enabling smooth top-to-bottom scrolling without truncation.

---

## 🐍 Python Streamlit Web Dashboard (`app.py`)

A lightweight web application featuring multi-tiered async validation, automated provider intelligence fingerprinting, and GitHub cloud persistence.

* **Multi-Tier Handshake Scanner**: Fast async evaluation of Xtream Codes and Stalker portals using HTTPX with custom evasion headers.
* **Dynamic Multi-Theming**: Visual themes (*Midnight Purple*, *Ocean Blue*, *Crimson Dark*, *Clean Light*).
* **Committed Vault**: Bidirectional GitHub REST API sync with conflict avoidance and SHA validation.

---

## 🚀 CI/CD & Automated APK Build Pipeline

* **Workflow (`.github/workflows/android-build.yml`)**:
  * Automatically compiles a signed **Release APK** on every push to `main` modifying Android files.
  * Dynamically bumps versioning based on `GITHUB_RUN_NUMBER`.
  * Instantly publishes formal GitHub Releases containing the raw unzipped binary (`app-release.apk`) to feed the in-app OTA Update Engine.


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

