# TVMime Task List 📝

### Phase 0: Planning & Architecture
- [x] Analyze IPTV constraints and domain knowledge.
- [x] Finalize KMP "Shared Core, Native UI" architecture.
- [x] Generate and select UI/UX design language (`tv_livetv_red_black.jpg`).
- [x] Initialize Git repository inside Monorepo.
### Phase 1: Cloud Presence & Admin Foundation
- [x] Initialize Project Structure (`adminWeb` scaffolded).
- [x] Setup Firebase Project via Web Console (`tvmime-65909`).
- [x] Create `adminWeb` React (Vite + TypeScript + Tailwind CSS) project.
- [x] Implement Firebase Auth & Firestore schema (`UserPortals`).
- [ ] Deploy `adminWeb` to Firebase Hosting (optional / on-demand).


### Phase 2: Shared Core & Data Layer (Xtream Codes)
- [ ] Implement Ktor Network Client (with User-Agent spoofing).
- [ ] Implement robust streaming JSON Parser to prevent OOM.
- [ ] Implement KMP Room Database for caching channels/EPG.
- [ ] Sync User Portals from Firebase to local Room DB.

### Phase 3: Android Mobile App & Chromecast
- [ ] Implement Mobile Jetpack Compose Dashboard (Red & Black).
- [ ] Integrate `androidx.media3:media3-cast` for Chromecast support.

### Phase 4: Android TV UI (Compose for TV)
- [ ] Implement Left-Side Navigation Drawer.
- [ ] Implement Live TV EPG Timeline (Translucent Strips).
- [ ] Implement VOD Section (Netflix style, TMDB metadata).
- [ ] Implement ExoPlayer hardware-accelerated playback and overlay.

### Phase 5: CI/CD Pipeline
- [ ] Create `.github/workflows/android.yml`.
- [ ] Configure automatic `assembleDebug` for TV and Mobile.
