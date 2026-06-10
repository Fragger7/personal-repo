# Project History and Core Goals
**Daily Push (v2.0)**

---

## 1. How We Got Here
Daily Push originated from a desire to build a hyper-focused, privacy-first physical activity logging vault. The application tracks two specific exercises: **Pushups** and **Crunches**, split over standard progressive three-set records. 

Through ongoing refinement, the app avoids bloated telemetry systems or expensive subscription tracking. It serves as a personal ritual of physical improvement.

---

## 2. Strategic Constraints and Exclusions
To maintain simplicity, speed, and absolute user sovereignty, several core decisions define Daily Push:

* **Clean Client Nodes with Automated Cloud Backup:** The application runs as a fast, responsive Client-Side App combined with a client-authenticated bidirectional synchronization to the user's personal Google Drive.
* **Origin Private File System (OPFS):** Rather than standard cookies or unstable browser caches that can be scrubbed by Android background cleaners, the app locks data directly into the device's physical storage using browser-level OPFS write pipelines.
* **Target Environment:** Specifically styled for **Android Chrome Mobile viewports** (safe margin limits, touch target sizing, custom color pairings, and standard safe controls against system auto-zooming on numeric inputs).
* **Deterministic Portability (Export/Seed):** If the device database is cleared, data is insured by programmatic client-side compilation of chronological `.json` files saved directly under a simple naming metric: `dailypush_backup_[YYYY-MM-DD].json`.

---

## 3. Product Vision
A visual tracker that feels like a clean, elegant daily companion: high-contrast dark palette, subtle transition indicators, and fluid charts. It represents a physical asset you fully own, with application code stored safely on GitHub, and your personal workout records stored in a fully visible, portable, and accessible fashion inside your private Google Drive (`/Daily Push/workout_data.json`).
