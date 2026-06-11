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
* **Zero-Configuration Backup Utilities:**
  * `EXPORT` button triggers an instant local file download (`dailypush_backup_[date].json`).
  * `SEED` processes raw imported files, sanitizes coordinates, and writes back into the OPFS layer.

---

## 3. Secure Backend-Secured Deployments & Automated Git Pipelines
Instead of entering user credentials on a public website or manually configuring Git on command lines, Daily Push operates as a robust full-stack application with automated server-side synchronizations:

* **Server-Side Environment Encapsulation:**
  * Your GitHub Personal Access Token (PAT), user details (`Fragger7`), and repository info stay securely in an offline `.env` file in our Node workspace.
  * Credentials are never transferred to or visible in the web browser console, local caches, or source files in production.
* **Automated Production Deployment Pipeline (`npm run deploy`):**
  * We engineered a custom automation script (`scripts/git_deploy.ts`) accessible in the package structure via `npm run deploy`.
  * **Build Step:** Runs `vite build` to compile minimized index page resources and optimized assets.
  * **Git Isolation:** Clones the user's remote repository to `/tmp/` securely, verifying valid connection parameters using the server-side PAT credentials.
  * **Pruning and Syncing:** Automatically clears out previous assets in `/daily-push/assets/` to prevent cumulative bundle clutter. Copies built compiled paths into corresponding subfolder hierarchies.
  * **Backup Control Integration:** Programmatically copies all active workspace assets (including source code folders `/src/`, config files, scripts, and markdowns) straight to the repo's `/daily-push/` subdirectory. This ensures your repository maintains a complete backup of both production-ready bundles and their exact underlying source files!
  * **Commit and Push:** Performs automated diff detection, commits using the user's identity details (`Fragger7`, `faraze46m3@gmail.com`), and instantly pushes directly to the `main` branch.
* **Cross-Device Shared Branch:**
  * Everything is maintained on a single branch (`main`) in independent folders, allowing multiple completely modular applications (like `daily-push` and `quantum-nexus`) to share the same domain and repository workspace without conflicts or messy branch maintenance.

