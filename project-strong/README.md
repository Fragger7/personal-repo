# 🕵️‍♂️ Sherlock Streams (IPTV Playlist Analytics & Forensic Dashboard)

A dual-platform forensic analytics engine and IPTV stream management dashboard designed to parse, validate, categorize, and stream-inspect IPTV credentials (Xtream Codes API & Stalker Portals) from unstructured raw text pastes.

---

## 📱 Android Native Application (Kotlin + Jetpack Compose)

The native Android app (`/android`) runs directly on mobile/residential IP connections to completely bypass cloud hosting IP blocks (HTTP 403) encountered on web deployments.

### ✨ Key Features
* **Media3 / ExoPlayer In-App Stream Inspector**:
  * Hardware-accelerated direct stream playback with software fallback decoders.
  * True full-screen mode linked with sensor landscape orientation (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`).
  * Live forensic telemetry HUD: Bitrate throughput (`kbps` / `Mbps`), buffer health cushion (`seconds ahead`), video resolution, video/audio codecs, and socket latency.
  * Clear labeled controls: Play/Pause, Mute/Unmute, Aspect Ratio (Fit/Fill/Zoom), Live Re-Sync, Copy URL, and Open in External Player (VLC / MX Player).
  * Scrub slider with dynamic time formatting (`mm:ss` / `hh:mm:ss`) for non-live and catchup streams.
* **16-Column Enterprise Master Grid & Detail Snapping**:
  * High-density horizontal `LazyColumn` grids with multi-column sorting (Date Added, Status, Type, Host, Provider, Channels, VODs, Days Left, Conns, Timezone, Notes).
  * Master-Detail navigation with auto-scrolling to the deep-dive inspector drawer.
* **Hierarchical Channel & VOD Catalog Explorer**:
  * Collapsible categorized channel lists with fast search filtering, individual category counters, and 1-tap stream testing.
* **Settings & Intelligence Hub**:
  * Real-time zero-latency hardware VPN detector (`NetworkCapabilities.TRANSPORT_VPN`).
  * Outbound IP geolocation shield and public cloud firewall warning indicator.
  * Live concurrency & timeout sliders with instant preference auto-save.
  * Safe GitHub Personal Access Token (PAT) cloud synchronization and cache clearing tools.
* **Universal Toast System & Multi-Orientation Layouts**:
  * Non-intrusive auto-dismissing visual toasts across foreground and background coroutine events.
  * Full vertical scrolling across all detail views in portrait and landscape orientations.

---

## 🐍 Python Streamlit Web Dashboard (`app.py`)

A lightweight web application featuring multi-tiered async validation, automated provider intelligence fingerprinting, and GitHub cloud persistence.

* **Multi-Tier Handshake Scanner**: Fast async evaluation of Xtream Codes and Stalker portals using HTTPX with custom evasion headers.
* **Dynamic Multi-Theming**: 4 visual themes (*Midnight Purple*, *Ocean Blue*, *Crimson Dark*, *Clean Light*).
* **Committed Vault**: Bidirectional GitHub REST API sync with conflict avoidance and SHA validation.

---

## 🎯 Active Backlog & Next Session Implementation Plan

1. **Ultra-Scale Performance Tuning & ANR Prevention (3,000+ Node Payloads)**:
   * Throttled 250ms batch state emits to decouple background HTTP coroutines from the Compose render thread.
   * Concurrency limiting with `Dispatchers.IO.limitedParallelism(24..32)`.
2. **Base64 Tab Power Actions & Ingestion Pipeline**:
   * Rich URL action chips and batch external browser/M3U launch.
   * 1-click "Send Decoded URLs to Scanner" direct routing.
3. **Provider Intelligence Engine (Android Port)**:
   * Port regex brand-fingerprinting, community link detector (Telegram, Discord, WhatsApp), and dummy banner stream detector to Android.
4. **Dynamic Theme Engine (Multi-Palette Switcher)**:
   * Dynamic theme selector supporting *Cyber Sherlock Amber/Navy (Default)*, *Midnight Purple*, *Ocean Blue*, *Crimson Dark*, and *System Monet*.
5. **Landscape Split-Pane Master-Detail Tablet/Foldable View**:
   * Side-by-side master list + live detail inspector pane on wide screens.
