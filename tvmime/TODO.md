# TVMime Task List 📝

### Phase 0: Planning & Architecture
- [x] Analyze IPTV constraints and domain knowledge.
- [x] Finalize KMP "Shared Core, Native UI" architecture.
- [x] Generate and select UI/UX design language (`tv_livetv_red_black.jpg`).
- [x] Initialize Git repository inside Monorepo.
### Phase 1: Cloud Presence & Admin Foundation
- [x] Initialize Project Structure (`adminWeb` scaffolded with Vite 6 + React 19 + Tailwind CSS v4).
- [x] Setup Firebase Project via Web Console (`tvmime-65909`).
- [x] Create `adminWeb` React project styled with Deep Black & Crimson Red design tokens.
- [x] Implement Firebase Auth & Firestore schema (`UserPortals`).
- [x] Deploy `adminWeb` to Vercel production ([tivimime.vercel.app](https://tivimime.vercel.app)).
- [x] Build serverless proxy (`/api/test-portal`) for zero-CORS / mixed-content IPTV testing.
- [x] Whitelist `tivimime.vercel.app` in Firebase Authentication Authorized Domains.



### Phase 2: Shared Core & Data Layer (Xtream Codes)
- [x] Scaffold KMP Multi-module architecture (`shared`, `tvApp`, `androidApp`, version catalog `libs.versions.toml`).
- [x] Implement centralized Design System Tokens in Kotlin (`DesignSystemTokens.kt`).
- [x] Implement Ktor Network Client with User-Agent spoofing (`IPTVSmartersPro/1.1.1`) and fast-fail hedging (`XtreamClient.kt`).
- [x] Implement zero-OOM token-by-token streaming catalog parser (`StreamingCatalogParser.kt`).
- [x] Configure hardware-accelerated Media3 player and evasion DataSource (`Media3PlayerConfig.kt`).
- [ ] Implement KMP Room Database for caching channels/EPG.
- [ ] Sync User Portals from Firebase to local Room DB.

### Phase 3: Android Mobile App & Chromecast
- [x] Scaffold Mobile Jetpack Compose baseline (`MainActivity.kt`).
- [ ] Implement full Mobile EPG and channel grid.
- [ ] Complete `androidx.media3:media3-cast` receiver discovery and remote playback.

### Phase 4: Android TV UI (Compose for TV)
- [x] Scaffold Android TV Leanback Compose baseline (`MainActivity.kt`).
- [ ] Implement Left-Side Navigation Drawer with D-Pad focus.
- [ ] Implement Live TV EPG Timeline (Translucent Strips).
- [ ] Implement VOD Section (Netflix style, TMDB metadata).
- [ ] Implement ExoPlayer hardware-accelerated playback overlay.

### Phase 5: CI/CD Pipeline
- [x] Create `.github/workflows/tvmime-build.yml`.
- [x] Configure automatic `assembleDebug` and artifact publishing for TV (`tvmime-tv-debug-apk`) and Mobile (`tvmime-mobile-debug-apk`).

