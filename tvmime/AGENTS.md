# AI Agent Context (TVMime) 🤖

**ATTENTION ALL AI AGENTS:** Read this document before modifying the TVMime codebase.

## 1. Project Paradigm
- **Goal:** Build the fastest, most beautiful IPTV Player for Android TV.
- **Architecture:** Kotlin Multiplatform (KMP). Do NOT write Android-specific dependencies in the `shared/` module (e.g., no `OkHttp` or `android.util.Log` in the common codebase). Use `Ktor` and Napier for cross-platform support.
- **UI Framework:** Jetpack Compose (Material 3) and `androidx.tv` (Compose for TV). Do NOT use legacy Leanback XML fragments.

## 2. Design System Constraints
- **Visuals:** The app uses a strict "Deep Black & Crimson Red" color palette with flat translucency. 
- **Reference:** Always refer to `mockups/tv_livetv_red_black.jpg` for the canonical look. Do NOT introduce heavy 3D geometries or real-time blurs that will kill 60fps performance on $30 Firesticks.
- **Shared Tokens:** Ensure all Compose UI components use the centralized `DesignSystemTokens` from the `shared` module.

## 3. Critical IPTV Domain Knowledge
- **OOM Prevention (CRITICAL):** IPTV provider JSONs can be 50MB+. You CANNOT use `Gson` or `Moshi` to deserialize the entire response into RAM. You MUST use low-level token-by-token streaming (e.g., `JsonReader`) and insert directly into the Room Database.
- **Network Evasion:** Providers block scraping. All Ktor requests to IPTV portals must spoof user-agents (e.g., `IPTVSmartersPro/1.1.1`).
- **Player Errors:** Handle HTTP 456/884 gracefully as "Stream Egress Disabled", not as a generic crash. ExoPlayer must use a custom DataSource Factory for the User-Agent.

## 4. Backlog
- If requested to implement radical 3D Spatial UI, reference `IMPLEMENTATION_PLAN.md` regarding the Unity/Godot native bridging backlog. Do NOT attempt to build volumetric smoke in Jetpack Compose.
