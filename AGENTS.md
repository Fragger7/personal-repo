# AGENTS.md - Workspace Master Directives & Context

> **Scope**: Automatically loaded by Antigravity on every turn and session startup across this workspace.

---

## 🏛️ Monorepo Projects & Canonical Paths
- **Canonical Root**: `/Users/admin/Development/personal-repo` (Git remote: `https://github.com/Fragger7/personal-repo.git`)
- **Projects**:
  1. **`ws-deal-hunter/`**: Autonomous Workstation Deal Hunter & Arbitrage Engine (eBay, Swappa, Reddit, DFS Refurbished, Micro Center, B&H). Dashboards: [Vercel](https://wsdealhunter.vercel.app/) & [Streamlit](https://wsdealhunter.streamlit.app/).
  2. **`lease-hunter/`**: EV & Automotive Lease Valuation Engine.
  3. **`daily-push/`**: Automated Daily Push & Notification Pipelines.
  4. **`project-strong/`**: Core Fitness & Analytics Platform.
  5. **`tvmime/`**: Next-Gen Android TV & Web IPTV Streaming Platform. (Shortcut: `/Users/admin/Development/TVMime`).

---

## 🧹 Scheduled Legacy Path Cleanup Directive
> [!IMPORTANT]
> The legacy path `/Users/admin/Development/Antigravity/` is **deprecated** and preserved only as a temporary backward-compatibility bridge during the migration session.
> **Action for Next Session**:
> When an agent is launched directly from the canonical root (`/Users/admin/Development/personal-repo`) or its subfolders, safely delete `/Users/admin/Development/Antigravity/` and all its deprecated artifacts:
> - `WS Deal Hunter.archive/`
> - Deprecated loose `Project Strong/` and `Lease Hunter/`
> - Legacy August archives (`Lease/`, `Migration Project/`, `WS Restore/`)
> - The backward-compatibility symlink `WS Deal Hunter`

---

## 🛡️ Non-Negotiable Engineering Rules (Loaded On Session Startup)

### 1. Data Feasibility & Empirical Probing First
* **Never guess or theorize**: Before proposing scraper enhancements, query expansions, or new endpoints, run a quick terminal probe to verify what the live endpoint actually returns.
* **Respect Marketplace Specializations**:
  - **Swappa**: Predominantly consumer & Apple Silicon MacBooks (M1–M4 Pro/Max). Has virtually zero PC workstation inventory (no Dell Precision / ThinkPad P-Series).
  - **eBay & Dell Financial Services (DFS)**: Enterprise off-lease workstations (Dell Precision, ThinkPad P-Series, HP ZBook).
  - **Reddit (`r/hardwareswap`, `r/homelabsales`)**: Bulk liquidator lots parsed via Markdown tables.

### 2. Check Memory & Codebase Before Proposing
* **Always search the codebase (`grep_search`) before proposing features**:
  - Never propose adding features that are already built (e.g., `DellRefurbishedCollector` for DFS is already implemented and running in `ws-deal-hunter/collector.py`).
  - Check `AGENTS.md` in subprojects for past architectural decisions and constraints.

### 3. Actionable Live Deals Over Vanity Counts
* **Never keep dead/sold listings to inflate numbers**:
  - Exceptional >= 9.0 deals are scarce and fleeting; they sell out within hours.
  - A catalog of **2–4 genuinely buyable, live units** is infinitely better than 11 listings where 9 are sold out.
  - Sold-out and expired items must be purged immediately. Dead listings destroy user trust.
  - Liveness reaper checks Schema.org JSON-LD `offers.availability == https://schema.org/OutOfStock` and visible DOM banners (`This listing sold on...`) directly on item URLs.

### 4. Proactive Token Burn & Rate Limit Warnings
* **Warn before burning quota**:
  - If a task risks significant LLM token burn (e.g., evaluating 25+ un-cached listings via Gemini API) or approaching daily/RPM quotas, **warn the user upfront** before running.
  - State the estimated call count, token footprint, and propose heuristic pre-filtering alternatives to preserve rate limit allowances for core session goals.

### 5. Monorepo Directory Boundary Invariance
* **All Deal Hunter code belongs strictly in `ws-deal-hunter/`**:
  - Never generate loose script files, debug outputs, or test data at the workspace root.
  - Keep peer project directories (`lease-hunter/`, `daily-push/`, `project-strong/`, `tvmime/`) isolated and untouched.

### 6. Zero Dummy Data in Production
* Under NO circumstances should mock data, synthetic seed listings, or fallback generators be committed to `deals.json` or served to production dashboards. If a live scraper returns 0 items, it must return `[]`.
