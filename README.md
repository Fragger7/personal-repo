# Workstation Deal Hunter

> **Autonomous Hardware Arbitrage & Live Deal Monitoring System**  
> Discovers, values, and alerts on high-margin enterprise workstations, mobile laptops, and creator PCs across secondary marketplaces.

---

## 🌐 Live Application & Dashboards

- **Production Streamlit App**: [https://wsdealhunter.streamlit.app/](https://wsdealhunter.streamlit.app/)
- **GitHub Repository**: [https://github.com/Fragger7/personal-repo](https://github.com/Fragger7/personal-repo)

---

## 🏗️ Architecture & Component Overview

- **Multi-Source Data Collector Hub ([`collector.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/collector.py))**:
  - **Reddit `r/hardwareswap`**: Live scraper extracting real-time user submissions, asking prices, authors, and canonical links (`https://www.reddit.com/r/hardwareswap/comments/...`).
  - **Syndicated Tech Deal Streams**: Live RSS ingestion for merchant laptop deals (Slickdeals, Refurb aggregators, Woot).
  - **eBay Browse REST API**: OAuth2 Client Credentials authentication with item summary search and search fallback.
- **AI Hardware Valuation Engine ([`evaluator.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/evaluator.py))**:
  - Gemini 2.5 Flash structured JSON spec extractor (CPU, RAM, SSD, GPU, Display).
  - Valuation model computing Fair Market Value (FMV), dollar spread, ROI %, and Deal Score ($0.0 - 10.0$).
  - Resilient rule-based heuristic pricing engine fallback.
- **Push Alert Dispatchers ([`notifier.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/notifier.py))**:
  - Instant mobile push alerts via Pushover API, Telegram Bot, or Discord Webhook when $\text{Deal Score} \ge 8.5$ and $\text{Price} \le \$750$.
- **Thread-Safe Storage ([`storage.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/storage.py))**:
  - RLock-synchronized atomic file writes to [`deals.json`](file:///C:/Development/Apps/WS%20Deal%20Hunter/deals.json) using POSIX temporary file swaps.
- **Autonomous Polling Daemon ([`daemon.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/daemon.py))**:
  - Background polling worker supporting single-shot (`--once`) and continuous loops.
- **Visual Dashboards**:
  - Streamlit Dashboard ([`app.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/app.py))
  - Full-stack Express + React 19 web app ([`server.ts`](file:///C:/Development/Apps/WS%20Deal%20Hunter/server.ts), [`src/App.tsx`](file:///C:/Development/Apps/WS%20Deal%20Hunter/src/App.tsx))
- **Unit Test Suite ([`test_system.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/test_system.py))**:
  - 12 comprehensive unit and integration tests.

---

## ⚙️ Getting Started

### 1. Installation

```bash
# Install Python dependencies
pip install -r requirements.txt

# Install Node.js dependencies (for React/Express app)
npm install
```

### 2. Verification & Testing

```bash
# Run unit test suite (12/12 passing)
python test_system.py

# Run a single live deal hunting scan
python3 daemon.py --once
```

### 3. Launch Dashboards

```bash
# Launch Streamlit dashboard locally
streamlit run app.py

# Launch Express + React dev server
npm run dev
```

---

## 📖 Developer & AI Context

For full architectural decision logs, component maps, state tracking, and future roadmap, consult [**`AGENTS.md`**](file:///C:/Development/Apps/WS%20Deal%20Hunter/AGENTS.md).
