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
3. **Cloud Layer: Remote File Synchronization (`/api/sync`)**
   * The application automatically synchronizes logs with the user's GitHub repository.
   * Data is stored inside your repository at: `/daily-push/data/workout_data.json`.
   * On application startup, the system pulls the latest array from GitHub so you instantly resume on any device.

---

## 2. Core Frontend Interactivity Modules
All code lives inside a modular, responsive client built with **React (v19)**, **Tailwind CSS**, and **Motion**:

* **Sequential Timeline Date Stepper Vector:**
  * Custom `◀` and `▶` buttons step the active tracking date forward/back by exactly 1 day.
  * Triggers database lookups automatically to map saved workout sets into inputs.
* **Aesthetic Data Graphing:**
  * Displays a rolling 14-day or 30-day chronological chart of workout achievements using `Recharts`.
* **Zero-Configuration Backup Utilities:**
  * `EXPORT` button triggers an instant local file download (`dailypush_backup_[date].json`).
  * `SEED` processes raw imported files, sanitizes coordinates, and writes back into the OPFS layer.

---

## 3. Secure Backend-Secured Deployments
Instead of entering user credentials on a public website, Daily Push operates as a robust full-stack application:

* **Sever-Side Environment Encapsulation:**
  * Your GitHub Personal Access Token (PAT) and repository info stay securely in an offline `.env` file in our Node workspace.
  * Credentials are never transferred to or visible in the web browser console or source inspector.
* **Serverless Production Portal Deployment (`/api/deploy`):**
  * Clicking the deployment button builds the React application and pushes the final compressed bundle directly into the `/daily-push/` subdirectory of your repository.
  * Your app becomes immediately live on your personal GitHub Pages domain at `https://[USERNAME].github.io/[REPO]/daily-push/`.
* **Cross-Device Shared Branch:**
  * Everything is maintained on a single branch (`main`) in independent folders, allowing multiple completely modular applications to share the same domain and repository workspace without conflicts or messy branch maintenance.
