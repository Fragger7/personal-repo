## Goal Description
Build a high-performance, modern IPTV player for Android TV (with full D-Pad support) and Android Mobile. The player will support Xtream Codes (Phase 2) and Stalker portals (Future Phase), providing a zippy experience similar to TiviMate and IMPlayer. 

Key features include Live TV, VOD (Movies & Series), EPG with logos, and standard TV watching controls. A critical addition is **Chromecast / Android TV Casting capability from the Mobile app**.

Instead of restraining our architecture to basic lessons, we will model our foundation after state-of-the-art open-source repositories like **StreamVault** and **OwnTV** (both built in Kotlin with Jetpack Compose for TV), adopting their best practices for handling massive M3U/Xtream lists and D-Pad focus management.

## User Review Required

> [!TIP]
> **Firebase Free Tier Viability**
> You asked if the Firebase free tier is enough. **Yes, it is more than enough, provided we use it correctly.**
> We will NOT store the massive 50MB+ channel lists in Firebase. Instead, Firebase Firestore will only store the user's **Portal Credentials** (Xtream URL, Username, Password) and lightweight data (like Favorite Channel IDs). 
> The Android TV / Mobile app will fetch these tiny credentials from Firebase, and then make the heavy API request *directly* to the IPTV provider, caching the massive channel list locally on the device using SQLite (Room). This ensures we stay well within the free 1GB storage and 50k reads/day limits.

## Proposed Changes

### Application Architecture (Future-Proofed for Apple TV)
To ensure we can seamlessly port this application to **Apple TV (tvOS)** and **iOS** in the future, we will build the foundation using **Kotlin Multiplatform (KMP)**. This is a critical strategic choice.

- **Shared Core (KMP):** All business logic, network requests, JSON parsing, and local database caching will be written in a shared Kotlin module. This means when you decide to build the Apple TV version, 90% of the app's code is already done.
  - **Networking:** Ktor (KMP compatible) instead of OkHttp.
  - **Database:** Room (which recently became KMP compatible) or SQLDelight for local caching.
  - **Design System Tokens:** A centralized Kotlin object defining exact Hex Colors, Typography scales, and padding/spacing values. Every platform (Android Mobile, TV, Web) will pull from this single source of truth to guarantee pixel-perfect brand consistency. The canonical design reference for the entire ecosystem (including the Web Admin Portal) is the `tv_livetv_red_black.jpg` mockup—featuring a Deep Black background, vibrant Crimson Red accents, sleek typography, and high-performance subtle translucency.
- **Native UI Layer:**
  - **Android TV / Mobile (Current):** Jetpack Compose (Material 3) & Compose for TV. We will stick with a **Left-side category menu** for the TV UI as it is intuitive and industry standard (TiviMate style).
  - **Apple TV (Future):** We will build a native SwiftUI interface that simply plugs into our shared KMP core.
- **Media Player Interface:** We will create a shared interface for the video player. On Android, it will use hardware-accelerated **AndroidX Media3 (ExoPlayer)**. In the future on Apple TV, it will map to **AVPlayer**.

---

### Phase 1: Cloud Presence & Admin Foundation (Completed)
As requested, we built the cloud presence first to make testing seamless across devices.
#### [COMPLETED] `adminWeb/` (Hosted on Vercel at https://tivimime.vercel.app)
- Setup a React 19 + Vite 6 + Tailwind CSS v4 admin UI matching the Deep Black & Crimson Red design tokens.
- Setup Firebase Authentication (Email/Password and Anonymous Quick Access) under project `tvmime-65909`.
- Setup Firestore database schema for `user_portals` (storing Xtream URLs, usernames, passwords, status).
- Deployed `/api/test-portal` serverless proxy function to eliminate CORS and browser Mixed Content errors.
- This allows us to load a list once in the cloud, and as we iterate on the TV app, it simply logs in and pulls the portals without re-typing.


---

### Phase 2: Network & Data Layer (Xtream Codes - Shared KMP)
#### [NEW] `shared/network/XtreamRepository.kt`
- Implementation of Ktor HTTP clients to fetch IPTV data directly from the provider using the credentials pulled from Firebase in Phase 1.
- Custom parsers to handle massive JSON arrays safely, syncing them into the local shared database.
- *Stalker portal support will be deferred to a later phase once Xtream is rock solid.*

---

### Phase 3: Mobile App & Chromecast
#### [NEW] `ui/mobile/MobileDashboardScreen.kt`
- Touch-optimized dashboard for Android Mobile.
#### [NEW] `player/CastManager.kt`
- Integration of `androidx.media3:media3-cast`.
- Adds the Google Cast button to the mobile app, allowing the user to cast the live stream or VOD directly to an Android TV or Chromecast.

---

### Phase 4: Android TV UI (Compose)
#### [NEW] `tvApp/ui/DashboardScreen.kt`
- Leanback-inspired left-side navigation menu using Compose for TV. D-Pad focus handling.
#### [NEW] `tvApp/ui/EPGScreen.kt`
- Custom drawn LazyGrid for the TV Guide timeline and logos.
#### [NEW] `shared/player/ExoPlayerManager.kt`
- Hardware-accelerated ExoPlayer with TV controls wrapper (Channel +/-, Volume, Last Channel).

---

### Phase 5: CI/CD & Project Structure
To avoid the headache of installing the massive Android SDK locally for compiling APKs, we will use a **GitHub Actions CI/CD Pipeline** (similar to Project Strong).
- **Git Strategy:** A single Monorepo hosted on GitHub.
- **Folder Structure:**
  - `/shared` (KMP Core Engine)
  - `/androidApp` (Mobile UI)
  - `/tvApp` (Android TV UI)
  - `/adminWeb` (Firebase Web UI)
- **CI/CD Pipeline:** Every push to the `main` branch will trigger a GitHub Action that automatically compiles `mobile-debug.apk` and `tv-debug.apk`. You can simply download these from the GitHub Artifacts page directly to your phone or TV.

## Verification Plan

### Automated Tests
- Unit tests for Firebase data mapping.
- Unit tests for Xtream JSON parsing to Room Entities.

### Manual Verification
- Add a portal credential via the Cloud/Mobile UI, launch the TV app, and verify it automatically syncs the portal and begins downloading the channels.
- Cast a Live TV stream from the Android Mobile app to a local Chromecast / Android TV device.
- Navigate the TV UI using only D-Pad arrows, ensuring no focus traps exist.

---

## Backlog & Future Explorations

> [!NOTE]
> **Radical 3D UI / Game Engine Integration**
> Once the core KMP engine and baseline Jetpack Compose UI are rock-solid, explore ripping off the Compose layer and plugging the shared Kotlin engine into a game engine (**Unity** or **Godot**) via native bridging. This would allow for the realization of the "Spatial Floating Glass" radical mockups—enabling real-time 3D Cover Flow, volumetric smoke, and physics-based UI rendering that standard app frameworks cannot handle, similar to modern automotive EV dashboards.
