# TVMime 📺

TVMime is a high-performance, modern IPTV player designed for Android TV and Android Mobile. 
It utilizes a "Shared Core, Native UI" architecture built on Kotlin Multiplatform (KMP), enabling future expansion to Apple TV (tvOS).

## Architecture
- **Shared Core (KMP):** Pure Kotlin engine for business logic.
  - Networking: Ktor
  - Local Caching: Room (SQLite) / SQLDelight
  - Design System: Centralized Theme Tokens
- **Native UI Layer:**
  - Android TV / Mobile: Jetpack Compose & Compose for TV
  - Web Admin: React / Compose Web (Hosted on Firebase)
- **Media Player:** AndroidX Media3 (ExoPlayer) with Hardware Acceleration.

## Key Features
- **Xtream Codes API Support:** Pull massive channel catalogs (Phase 2).
- **Zero-Cost Cloud Admin:** Firebase Authentication and Firestore to manage portals across devices.
- **TMDB Integration:** Rich metadata, jaw-dropping cinematic VOD UI.
- **Extreme Performance:** Bypasses standard JSON mapping in favor of low-level stream parsing to prevent Out-Of-Memory (OOM) crashes on low-end TV hardware.

## UI / Design System
The canonical design aesthetic for TVMime is **Deep Black with vibrant Crimson Red accents**, flat subtle translucency, and sleek typography. 
See `mockups/tv_livetv_red_black.jpg` for the North Star design reference.

## CI/CD Pipeline
APK compilation is handled via GitHub Actions to avoid local SDK overhead.
Every push to the `main` branch automatically compiles `mobile-debug.apk` and `tv-debug.apk`.
