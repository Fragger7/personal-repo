# AGENTS.md - Workstation Deal Hunter Developer & AI Agent Context Manual

> **Welcome AI Partner / Developer!**  
> This document serves as the authoritative architectural manual, system state tracker, decision log, and roadmap for **Workstation Deal Hunter**. Any AI agent or engineer working on this repository should read this document to understand the codebase structure, past architectural choices, current system state, and immediate backlog.

---

## 🚀 1. System Purpose & Core Utility

**Workstation Deal Hunter** is an autonomous hardware arbitrage and deal monitoring platform designed to identify, evaluate, and alert on high-margin enterprise workstation laptops and desktops (ThinkPad P-series, Dell Precision, HP ZBook, Apple Silicon MacBook Pro, and custom workstation PCs).

### Primary System Capabilities
1. **Multi-Source Syndicated Data Collectors** ([`collector.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/collector.py)): Real-time ingestion from eBay Browse REST API, Reddit `r/hardwareswap`, Swappa, and syndicated tech deal RSS feeds (Slickdeals / Woot / Refurb aggregators).
2. **AI & Heuristic Valuation Engine** ([`evaluator.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/evaluator.py)): Uses Gemini 2.5 Flash to extract hardware specifications (CPU, RAM, SSD, GPU, Display) and computes Fair Market Value (FMV), arbitrage margin %, and a calibrated **Deal Score ($0.0 - 10.0$)**.
3. **Thread-Safe Atomic Storage Engine** ([`storage.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/storage.py)): RLock-synchronized atomic file writes ([`deals.json`](file:///C:/Development/Apps/WS%20Deal%20Hunter/deals.json)) utilizing temporary file swaps to guarantee process-safe reads and writes without partial corruption.
4. **Push Alert Dispatchers** ([`notifier.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/notifier.py)): Dispatches instant mobile push notifications via Pushover API, Telegram Bot, or Discord Webhooks for high-yield deals ($\text{Deal Score} \ge 8.5$ and $\text{Price} \le \$750$).
5. **Autonomous Daemon Orchestrator** ([`daemon.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/daemon.py)): Background worker running continuous polling loops or single-shot execution (`python3 daemon.py --once`).
6. **Dual Visual Dashboards**:
   - Streamlit Interactive Dashboard ([`app.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/app.py)) deployed at [https://wsdealhunter.streamlit.app/](https://wsdealhunter.streamlit.app/).
   - Full-Stack Express + React 19 Dashboard ([`server.ts`](file:///C:/Development/Apps/WS%20Deal%20Hunter/server.ts), [`src/App.tsx`](file:///C:/Development/Apps/WS%20Deal%20Hunter/src/App.tsx)).

---

## 📊 2. Current System State & Verified Status

- **Unit Test Suite Status**: **12 / 12 Tests Passing** (`python test_system.py`).
- **Live Ingestion Verification**: Verified live collection of 29+ real-time listings per cycle.
- **Production Deployment**: Live on Streamlit Cloud at **[https://wsdealhunter.streamlit.app/](https://wsdealhunter.streamlit.app/)**.
- **Git Branch & Remote**: Tracked on `main` branch connected to `https://github.com/Fragger7/personal-repo.git`.

---

## 🧠 3. Architectural Evolution & Key Decisions Log

### Decision 1: Live Scraping Fallback for Reddit `r/hardwareswap`
- **Problem**: Reddit's standard API endpoint (`https://www.reddit.com/r/hardwareswap/new.json`) returns `HTTP 403 Forbidden` to generic Python requests due to Cloudflare anti-bot WAF rules.
- **Decision & Solution**: Implemented `_fetch_old_reddit_live()` in [`collector.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/collector.py) using `cloudscraper` + `BeautifulSoup` targeting `https://old.reddit.com/r/hardwareswap/new/`.
- **URL Normalization**: All scraped URLs are transformed to standard canonical links (`https://www.reddit.com/r/hardwareswap/comments/...`) so users open the modern Reddit interface directly when clicking **Open Listing ↗**.

### Decision 2: eBay Ingestion Dual-Mode (Browse REST API vs Canonical Live Search)
- **Problem**: eBay blocks direct unauthenticated web scraping of individual item pages (`/itm/<id>`) with `403 Forbidden` or CAPTCHA, and static old item URLs expire quickly leading to `404 Not Found` pages.
- **Decision & Solution**:
  - **Authenticated Mode**: When `EBAY_CLIENT_ID` and `EBAY_CLIENT_SECRET` are provided in [`.env`](file:///C:/Development/Apps/WS%20Deal%20Hunter/.env), `EBayCollector` uses the official OAuth2 Browse REST API (`https://api.ebay.com/buy/browse/v1/item_summary/search`) for direct item URLs.
  - **Fallback Mode**: Without keys, `EBayCollector` generates canonical live search URLs (`https://www.ebay.com/sch/i.html?_nkw=Exact+Hardware+Model&_sop=12`), guaranteeing users land on active, available inventory for that exact model.

### Decision 3: Regex XML Link Parsing for Tech Deal RSS Feeds
- **Problem**: Parsing RSS XML documents with BeautifulSoup using `html.parser` treats `<link>URL</link>` XML elements as self-closing HTML head tags, producing empty `""` string URLs.
- **Decision & Solution**: Replaced BeautifulSoup XML link parsing in `_fetch_live_rss_deals()` with regex block extraction (`re.search(r'<link>(.*?)</link>')`) and HTML entity unescaping (`html.unescape`). 100% of RSS listings now have valid HTTP links.

### Decision 4: Windows UTF-8 Terminal Stream Reconfiguration
- **Problem**: Windows standard `cp1252` encoding threw `UnicodeEncodeError` when printing emoji logs (🔥, 🚨) to console.
- **Decision & Solution**: Added `sys.stdout.reconfigure(encoding="utf-8", errors="replace")` and `sys.stderr.reconfigure(...)` at the entry point of [`test_system.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/test_system.py), [`notifier.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/notifier.py), and [`daemon.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/daemon.py).

### Decision 5: Streamlit Cloud Deployment Path Resolution
- **Problem**: Streamlit Cloud runs `app.py` from repository root (`/app/personal-repo/`), causing `AtomicDealStorage("deals.json")` to look at root instead of `ws-deal-hunter/deals.json`, falling back to 4 initial seed items.
- **Decision & Solution**: Updated [`app.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/app.py) to dynamically locate `deals.json` using `Path(__file__).parent / "deals.json"`, and synchronized `deals.json` at both root and subfolder levels in Git.

---

## 📁 4. Codebase Sitemap & Directory Map

```text
C:\Development\Apps\WS Deal Hunter\
├── .env.example          # Environment variable template & documentation
├── .gitignore            # Git exclusion rules (keeps deals.json tracked)
├── AGENTS.md             # This AI Agent Developer Manual & Memory File
├── README.md             # Technical & User-Facing Project Documentation
├── app.py                # Streamlit Dashboard (Streamlit Cloud Entrypoint)
├── collector.py          # Data Collector Hub (eBay API, Reddit Scraper, RSS)
├── daemon.py             # Polling Daemon CLI & Background Worker (--once)
├── deals.json            # Thread-safe persistent JSON database of deals
├── evaluator.py          # Gemini 2.5 Flash + Rule-based Heuristic Valuation
├── index.html            # Vite/React HTML template
├── notifier.py           # Pushover, Telegram, and Discord push alert engine
├── package.json          # Node.js dependencies & scripts (Express/React)
├── requirements.txt      # Python dependencies (Streamlit, cloudscraper, bs4)
├── server.ts             # Express.js backend server for React dashboard
├── storage.py            # Atomic file storage manager with RLock
├── test_system.py        # 12-test comprehensive unit and integration suite
├── tsconfig.json         # TypeScript compiler configuration
├── vite.config.ts        # Vite bundle builder configuration
└── src/                  # React 19 Frontend Components
    ├── App.tsx           # Main React Dashboard layout
    ├── index.css         # Styling design system
    ├── main.tsx          # React DOM mounting entrypoint
    ├── types.ts          # TypeScript interfaces for DealRecord & Specs
    └── components/       # UI Components (DealCard, DealTable, FilterBar, etc.)
```

---

## 📌 5. Project Backlog & Future Roadmap

### 🔴 Immediate Backlog (Short-Term)
- [ ] **Plug in eBay Production Credentials**: When eBay Developer approval arrives, enter `EBAY_CLIENT_ID` and `EBAY_CLIENT_SECRET` into `.env` to enable direct item URLs (`/itm/<id>`).
- [ ] **Streamlit Pagination & Sorting**: Add dropdown for sorting deals by `Deal Score`, `Asking Price`, `Arbitrage Profit`, or `Date Discovered`.
- [ ] **Pushover / Webhook Test Trigger UI**: Add test button in Streamlit sidebar to verify mobile notifications interactively.

### 🟡 Medium-Term Backlog
- [ ] **Historical Price Trend Tracking**: Extend [`storage.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/storage.py) to store price history per model and render historical price charts in Streamlit.
- [ ] **Auto-Git Sync in Daemon**: Add optional `--auto-push` flag in [`daemon.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/daemon.py) to automatically commit and push [`deals.json`](file:///C:/Development/Apps/WS%20Deal%20Hunter/deals.json) to GitHub when high-yield deals arrive.
- [ ] **Expand Collector Endpoints**: Add scrapers for **BackMarket**, **Mercari**, and **MicroCenter Refurb Deals**.

### 🟢 Long-Term Roadmap
- [ ] **ML Price Prediction Model**: Train a custom scikit-learn regression model on historical `deals.json` data to augment Gemini 2.5 Flash valuation.
- [ ] **Auto-Buy / Auto-Bid Integration**: Provide optional webhook endpoints for automated purchase execution where APIs permit.

---

## 🛠️ 6. How to Test and Run

### Run Unit Test Suite
```bash
python test_system.py
```

### Run Single Live Deal Hunting Cycle
```bash
python3 daemon.py --once
```

### Run Local Streamlit Dashboard
```bash
streamlit run app.py
```

### Run Full-Stack Express + React Dashboard
```bash
npm run dev
```

---

## 🛡️ Guidelines for AI Agents Working on this Repo

1. **Always Verify Edits with Tests**: Run `python test_system.py` after modifying any Python module.
2. **Preserve Atomic File Writes**: Do not bypass `AtomicDealStorage._write_atomic()` in `storage.py` when writing to `deals.json`.
3. **Dual Path Sync for GitHub Pushes**: When pushing to `Fragger7/personal-repo`, make sure updated files are synced in both the root and `ws-deal-hunter/` subfolder.
4. **UTF-8 Compatibility**: Maintain `sys.stdout.reconfigure(encoding="utf-8", errors="replace")` on entrypoints to prevent Windows console encoding errors.
