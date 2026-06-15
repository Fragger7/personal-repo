# Technical Architecture and Deep Systems Mapping
**Daily Push (v2.0)**

---

## 1. Storage Sandboxing & Cloud Synchronization Pipeline
To combine the speed of offline-first storage with the durability of cloud backups, data is managed through a secure cascading pipeline:

1. **Active Client Layer: Origin Private File System (OPFS)**
   * Accesses a high-performance private sandboxed storage space assigned exclusively to the browser origin.
   * Read & writes the master timeline array directly into a physical file: `workout_data.json`.
2. **Failover Client Layer: Standard LocalStorage**
   * Automatically activated if the user agent doesn't support modern OPFS (e.g., in-app webviews or old browser runtimes).
3. **Cloud Layer: Google Drive Cloud Sync Platform**
   * Syncs the workout ledger bi-directionally with the user's personal Google Drive using the secure `drive.file` scope.
   * Programmatically creates a dedicated, fully visible **`Daily Push`** folder at the root of the user's Drive.
   * Stores the master database backup file inside as **`workout_data.json`**, letting the user access, share, or download the raw file anywhere on any device.
   * Integrates an intelligent daily merging algorithm—resolving set-by-set entries bidirectionally by selecting the workout day with higher total repetitions.

---

## 2. Core Frontend Interactivity Modules
All code lives inside a modular, responsive client built with **React (v19)**, **Tailwind CSS**, and **Motion**:

* **Sequential Timeline Date Stepper Vector:**
  * Custom `◀` and `▶` buttons step the active tracking date forward/back by exactly 1 day.
  * Triggers database lookups automatically to map saved workout sets into inputs.
* **Granular Data Types and Operations:**
  * Inputs differentiate between explicitly recorded `0` reps and omitted/blank entries `null` to ensure pristine averaging.
  * Native deletion handlers allow wiping out target date indexes across all data layers, syncing deletions bi-directionally to the master cloud file.
* **Aesthetic Data Graphing:**
  * Displays a rolling 14-day or 30-day chronological chart of workout achievements using `Recharts`.
  * Integrates dynamic Live Sub-totals within form inputs updating immediately as set parameters change.
  * Includes a compact recent history index tracking the 10 latest entries for quick navigation without bloating performance.
  * Calculates explicit Personal Records (Most combined daily reps, highest individual sets, etc.) organically from the active `dataFrame` each cycle.
* **Zero-Configuration Backup Utilities:**
  * `EXPORT` button triggers an instant local file download (`dailypush_backup_[date].json`).
  * `SEED` processes raw imported files, sanitizes coordinates, and writes back into the OPFS layer.

---

## 3. Secure Vercel Deployments & Serverless API Backend
Instead of entering user credentials on a public website or relying purely on static hosting, Daily Push operates as a robust full-stack application with a Vercel-hosted Serverless backend:

* **Serverless Backend (Vercel):**
  * We built API handlers (e.g., `/api/insight.ts`) utilizing the `@vercel/node` runtime. This securely encapsulates the Gemini AI Logic and execution.
  * Your `GEMINI_API_KEY` is completely hidden and lives securely within Vercel's Project Settings -> Environment Variables. It is never transferred to or visible in the web browser console, local caches, or source files in production.
* **Automated Production Deployment Pipeline:**
  * We use an internal script (`scripts/git_deploy.ts`) accessible via `npm run deploy` to compile production assets and meticulously copy both the `dist` bundles and the raw source files (`/api`, `/src`, `server.ts`, etc.) into a `daily-push` subdirectory within the `personal-repo` branch.
  * By synchronizing these exact source files to GitHub, Vercel automatically detects the new commits on the `main` branch, resolves the configuration, and triggers an instantaneous Edge/Serverless deployment.
  * **Backup Control Integration:** Keeping all active workspace assets pushed directly to the repo ensures your repository maintains a complete backup of both production-ready endpoints and their exact underlying source files!
* **Scalable Architecture:**
  * Maintaining everything in modular backend endpoints (`/api/*`) allows the app to scale natively for future integrations without exposing API keys or overloading the client browser node.

---

## 4. Security & Cost Posture (User Environment)
The application has been engineered to run with high security and minimal/zero operational overhead.

* **Cost Optimization (Zero-Cost Baseline):**
  * **Vercel Hosting:** The deployed frontend and Serverless Functions (`/api/*`) comfortably run within Vercel's generous free Hobby Tier.
  * **Google Drive API:** Data sync operates on the user's personal Google Drive storage allowance. The API calls are well within Google's free usage quotas.
  * **Gemini API:** Utilizing existing free-tier Gemini API limitations; because AI insights trigger conditionally or explicitly on-demand, they will not scale out of bounds unless heavily abused.
  * **GitHub Storage:** Pushing backups and source files inherently uses standard free GitHub repository storage.
* **Security & Danger Mitigation:**
  * **API Keys (Gemini):** By migrating to Vercel Serverless endpoints, the `GEMINI_API_KEY` is fully shielded. It cannot be extracted by visitors, scrapers, or browser consoles.
  * **OAuth 2.0 (Google Drive):** The app scopes authorization restrictively to `drive.file` (only files *created* by Daily Push), ensuring your overarching Google Drive files (photos, taxes, docs) cannot be read or interfered with. Linking origins (AI Studio & Vercel) further restricts unauthorized domains from spoofing login requests.
  * **GitHub Deployments:** The Personal Access Token (PAT) used for automated backups is only injected via the secure workspace environment variable (`GITHUB_PAT`); it never travels to the client browser.

