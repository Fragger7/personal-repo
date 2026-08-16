# Workstation Deal Hunter

> **Autonomous Hardware Arbitrage & Live Deal Monitoring System**  
> Discovers, values, and alerts on high-margin enterprise workstations, mobile laptops, and creator PCs across secondary marketplaces.

---

## 🌐 Live Application & Dashboards

- **Production Streamlit App**: [https://wsdealhunter.streamlit.app/](https://wsdealhunter.streamlit.app/)
- **GitHub Repository**: [https://github.com/Fragger7/personal-repo](https://github.com/Fragger7/personal-repo)

---

## 🏗️ Architecture & Component Overview

- **24/7 Cloud Automation ([`.github/workflows/hunt_deals.yml`](file:///C:/Development/Apps/WS%20Deal%20Hunter/.github/workflows/hunt_deals.yml))**:
  - Autonomous scheduled GitHub Actions cron (`0 * * * *`) running hourly scans, committing updated deals, and pushing mobile alerts 24/7 for $0 cost.
- **Telegram Bot & Push Dispatcher ([`notifier.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/notifier.py))**:
  - Rich HTML deal alerts, hourly Inventory Update Pulse digests, and automated self-healing diagnostic health warnings.
- **Multi-Source Data Collector Hub ([`collector.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/collector.py))**:
  - Real-time scrapers for Reddit `r/hardwareswap`, `r/appleswap`, and `r/homelabsales`.
  - Targeted syndicated clearance deal streams (ThinkPad, Precision, ZBook, MacBook, OLED, Mini-PCs).
  - eBay Browse API integration with canonical fallback.
- **AI Hardware Valuation Engine ([`evaluator.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/evaluator.py))**:
  - Gemini 2.5 Flash spec extractor & valuation engine with transparent token and daily quota tracking (`GeminiUsageTracker`).
  - Strict exclusion rules aligned with [`AGENT_KNOWLEDGE_BASE.md`](file:///C:/Development/Apps/WS%20Deal%20Hunter/AGENT_KNOWLEDGE_BASE.md) (auto-drops $\le 16\text{GB}$ Apple Silicon, Intel Macs, damaged units).
- **Thread-Safe Storage ([`storage.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/storage.py))**:
  - Atomic temporary file replacement for [`deals.json`](file:///C:/Development/Apps/WS%20Deal%20Hunter/deals.json).
- **Visual Dashboards**:
  - Streamlit Dashboard ([`app.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/app.py)) with faceted filters (Brands, RAM, SSD, GPU, Sorting).
  - Full-stack Express + React 19 web app ([`server.ts`](file:///C:/Development/Apps/WS%20Deal%20Hunter/server.ts), [`src/App.tsx`](file:///C:/Development/Apps/WS%20Deal%20Hunter/src/App.tsx)).

---

## 📖 Developer & AI Agent Documentation

- **[`AGENTS.md`](file:///C:/Development/Apps/WS%20Deal%20Hunter/AGENTS.md)**: Architectural manual, design decisions log (Decisions 1–13), and system state.
- **[`AGENT_KNOWLEDGE_BASE.md`](file:///C:/Development/Apps/WS%20Deal%20Hunter/AGENT_KNOWLEDGE_BASE.md)**: Hardware tier scoping, developer use case baselines, and arbitrage heuristics.
- **[`BACKLOG.md`](file:///C:/Development/Apps/WS%20Deal%20Hunter/BACKLOG.md)**: Complete prioritized technical backlog, world-class UI/UX redesign plan, and scraper expansion roadmap.
