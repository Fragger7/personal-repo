# Workstation Deal Hunter & Personal Workspace Monorepo

> **Autonomous Hardware Arbitrage & Live Deal Monitoring System**  
> Discovers, values, and alerts on high-margin enterprise workstations, mobile laptops, and creator PCs across secondary marketplaces.

---

## 🌐 Live Application & Dashboards

- **Production Vercel App (React Dashboard)**: [https://wsdealhunter.vercel.app/](https://wsdealhunter.vercel.app/)
- **Production Streamlit App (Python Engine)**: [https://wsdealhunter.streamlit.app/](https://wsdealhunter.streamlit.app/)
- **GitHub Repository**: [https://github.com/Fragger7/personal-repo](https://github.com/Fragger7/personal-repo)

---

## 📁 Repository Directory Structure

| Directory | Description | Live Deployment |
| :--- | :--- | :--- |
| [**`ws-deal-hunter/`**](./ws-deal-hunter/) | Autonomous Workstation Deal Hunter, Arbitrage Engine & Live Dashboard | [wsdealhunter.vercel.app](https://wsdealhunter.vercel.app/) |
| [**`daily-push/`**](./daily-push/) | Automated daily Git push & development activity logger | — |
| [**`lease-hunter/`**](./lease-hunter/) | Real estate & lease opportunity scraper and analysis tool | — |
| [**`project-strong/`**](./project-strong/) | Strength workout & fitness tracking companion | — |

---

## 🏗️ Architecture & Component Overview

- **24/7 Cloud Automation ([`.github/workflows/hunt_deals.yml`](file:///C:/Development/Apps/WS%20Deal%20Hunter/.github/workflows/hunt_deals.yml))**:
  - Autonomous scheduled GitHub Actions cron (`0 * * * *`) running hourly scans, committing updated deals, and pushing mobile alerts 24/7 for $0 cost.
- **Telegram Bot & Push Dispatcher ([`notifier.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/notifier.py))**:
  - Rich HTML deal alerts, hourly Inventory Update Pulse digests, and automated self-healing diagnostic health warnings.
- **Multi-Source Data Collector Hub ([`collector.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/collector.py))**:
  - Real-time TLS-impersonated scrapers for 11 concurrent sources (eBay, Reddit, Swappa, B&H, Best Buy, Dell DFS, Lenovo, ShopGoodwill, Apple Refurbished, Micro Center, Woot).
- **AI Hardware Valuation Engine ([`evaluator.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/evaluator.py))**:
  - Gemini 2.5 Flash spec extractor & valuation engine with transparent token and daily quota tracking (`GeminiUsageTracker`).
  - Strict exclusion rules aligned with [`AGENT_KNOWLEDGE_BASE.md`](file:///C:/Development/Apps/WS%20Deal%20Hunter/AGENT_KNOWLEDGE_BASE.md) (auto-drops $\le 16\text{GB}$ RAM laptops, Intel Macs, damaged units).
- **Thread-Safe Storage ([`storage.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/storage.py))**:
  - Atomic temporary file replacement for [`deals.json`](file:///C:/Development/Apps/WS%20Deal%20Hunter/deals.json).
- **Visual Dashboards**:
  - Full-stack Express + React 19 web app ([`server.ts`](file:///C:/Development/Apps/WS%20Deal%20Hunter/server.ts), [`src/App.tsx`](file:///C:/Development/Apps/WS%20Deal%20Hunter/src/App.tsx)).
  - Streamlit Dashboard ([`app.py`](file:///C:/Development/Apps/WS%20Deal%20Hunter/app.py)).

---

## 📖 Developer & AI Agent Documentation

- **[`AGENTS.md`](file:///C:/Development/Apps/WS%20Deal%20Hunter/AGENTS.md)**: Architectural manual, design decisions log (Decisions 1–36), and system state.
- **[`AGENT_KNOWLEDGE_BASE.md`](file:///C:/Development/Apps/WS%20Deal%20Hunter/AGENT_KNOWLEDGE_BASE.md)**: Hardware tier scoping, developer use case baselines, and arbitrage heuristics.
- **[`BACKLOG.md`](file:///C:/Development/Apps/WS%20Deal%20Hunter/BACKLOG.md)**: Complete prioritized technical backlog and scraper roadmap.
