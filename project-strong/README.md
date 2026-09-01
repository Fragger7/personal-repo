# 🕵️‍♂️ Sherlock Streams (IPTV Playlist Analytics & Forensic Dashboard)

A dual-platform forensic analytics engine and IPTV stream management dashboard designed to parse, validate, categorize, hardware stream-inspect, and archive IPTV credentials (Xtream Codes API & Stalker Portals) from unstructured raw text pastes.

---

## 📱 Android Native Application (Kotlin + Jetpack Compose)

The native Android app (`/android`) runs directly on mobile/residential IP connections to completely bypass cloud hosting IP blocks (HTTP 403) encountered on web deployments.

### ✨ Key Features
* **🏛️ "Forever Source" Git Snapshot & Archive Engine**:
  * Automatically captures raw pastebin/pastetext dumps and saves them as versioned text files directly in the Git repository (`project-strong/sources/{filename}.txt`).
  * In-app monospace source reader with line numbering, text search, raw copy, upstream URL launcher, and 1-click **"⚡ Send to Scanner"** recall pipeline.
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
* **Settings & Intelligence Hub**:
  * Real-time zero-latency hardware VPN detector (`NetworkCapabilities.TRANSPORT_VPN`).
  * Outbound IP geolocation shield and public cloud firewall warning indicator.
  * Live concurrency & timeout sliders with instant preference auto-save.
  * Multi-theme switcher (*Cyber Sherlock Amber/Navy*, *Midnight Purple*, *Ocean Blue*, *Crimson Dark*, *System Monet*).
  * Safe GitHub Personal Access Token (PAT) cloud synchronization and cache clearing tools.
* **Universal Toast System & Multi-Orientation Layouts**:
  * Non-intrusive auto-dismissing visual toasts across foreground and background coroutine events.
  * Full vertical scrolling across all detail views in portrait and landscape orientations.

---

## 🐍 Python Streamlit Web Dashboard (`app.py`)

A lightweight web application featuring multi-tiered async validation, automated provider intelligence fingerprinting, and GitHub cloud persistence.

* **Multi-Tier Handshake Scanner**: Fast async evaluation of Xtream Codes and Stalker portals using HTTPX with custom evasion headers.
* **Dynamic Multi-Theming**: Visual themes (*Midnight Purple*, *Ocean Blue*, *Crimson Dark*, *Clean Light*).
* **Committed Vault**: Bidirectional GitHub REST API sync with conflict avoidance and SHA validation.

---

## 🚀 CI/CD & Automated APK Build Pipeline

* **Workflow (`.github/workflows/android-build.yml`)**:
  * Automatically compiles a debug APK (`app-debug.apk`) on every push to `main` modifying Android files.
  * Artifacts are published as `project-strong-debug-apk` on GitHub Actions.
