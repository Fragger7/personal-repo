# Universal Lease Hunter Engine - System Architecture & Developer Guide

This document contains the complete system architecture, operational decisions, cloud deployment triggers, and step-by-step Git lifecycle workflows for the **Universal Lease Hunter Engine**. It is designed to serve as a comprehensive knowledge source for both human developers and autonomous AI agents (LLMs).

---

## 📌 Project Overview
The Universal Lease Hunter Engine is an autonomous AI lease broker protocol currently in its **Phase 1 Proof of Concept** targeting the **Kia EV9 GT-Line**. We have fully transitioned from a legacy Streamlit prototype to a **modern full-stack React and Express architecture**. It is designed to find active vehicle inventory, retrieve live lease terms (MSRPs, residuals, money factors, incentives) via custom backend scraping architectures and Gemini AI extraction, and compute precise lease payments across different jurisdictions with complex, multi-state tax rules.

---

## 🏗️ Phase 1 Architecture & Core Components

### 1. Market Intel & Sourcing Pipeline (Multi-Layer Architecture)
The core aggregation engine relies on backend Node.js fetching logic combined with `@google/genai` and Chrome Remote Debugging Protocol (CDP) attachment:
*   **CDP-Attached Aggregator Node (`server/crawler/scrape-cars-com-new-ev9.ts`)**: Bypasses bot detection firewalls (DataDome/Cloudflare) at $0 cost by attaching Playwright (`chromium.connectOverCDP('http://127.0.0.1:9222')`) to a real, user-launched Chrome instance. Extracted 22 brand new 2026 Kia EV9 listings (GT-Line, Land, Wind) within 50 miles of Round Rock, TX (ZIP 78665) with live Days on Lot and up to **$13,243 off MSRP**.
*   **Dealer-Direct Network Scraper Node (`server/crawler/scrape-local-dealers-headless.ts`)**: Scrapes local franchised dealership platforms (Group 1 Kia South Austin, Round Rock Kia) to retrieve exact Monroney window stickers, West Point GA VINs (`5XY...`), and internet sales manager contacts.
*   **Sequential Baseline Extraction**: Extracts baselines (MSRP, Residuals, Money Factor) from Edmunds/Leasehackr forums via Gemini search grounding (`/api/scrape/extract-baselines`).
*   **Telegram Push Notification Alert Engine (`server/services/telegram.ts`)**: Dispatches instant formatted deal cards with direct, working vehicle hyperlinks directly to the user's phone.
*   **Outreach & Negotiation Node (CarGurus / Dealer Chat)**: Optimal platform for initial contact and executing the "Golden Outreach Template".

### 2. Deal Engine & Tax Trap Simulator
Computes depreciation, rent charges, and state-specific tax burdens (prioritizing the ultimate bottom-line monthly number).
*   **Tax Trap Focus (ZIP 78665, Texas)**: Evaluates scenarios where taxes are levied on the entire vehicle purchase price. The engine actively researches and applies manufacturer tax credits (e.g. from Kia Finance) to combat this.
*   **Value Metric**: Utilizes the **Leasehackr Score** combined with current market momentum.
*   **Intelligent Trim Pivots**: The engine autonomously evaluates alternative AWD variants (Land AWD, Wind AWD) and pivots if alternative trims yield significantly higher overall deal scores.

### 3. CRM & Autonomous Outreach
A robust module for tracking active leads and negotiating deals.
*   **Persistence**: Local JSON caching (`data/inventory.json` & `data/crm.json`) and Firebase Firestore.
*   **AI Broker Negotiation**: Generates highly intelligent, precise, data-driven first-contact messages to dealers requesting buy-rate Money Factors and negotiating pure dealer discounts off MSRP *before* manufacturer rebates.

---

## 🛠️ Technology Stack
*   **Frontend**: React, Tailwind CSS, Vite (Dark-mode, high-fidelity UI).
*   **Backend**: Node.js & Express (TypeScript).
*   **Browser Automation**: Playwright over Chrome Remote Debugging Protocol (CDP).
*   **Notifications**: Telegram Bot API.
*   **Database/CRM**: Local JSON Store & Firebase Firestore.
*   **AI Integration**: Google GenAI TypeScript SDK (`@google/genai`).

---

## ☁️ Cloud & Local Execution
*   **Start Chrome Debug Port**: Run `start-chrome-debug.bat` to launch Chrome on port 9222 for CDP attachment.
*   **Development**: Run `npm run dev` to start the local `vite` and `express` dev servers.
*   **Build**: Run `npm run build` to compile the React frontend and server.

---

## 🤖 AI Agent Git Operations Lifecycle (Important for LLMs)
Follow the rules defined in [.agents/AGENTS.md](file:///C:/Development/Apps/Lease%20Hunter/.agents/AGENTS.md) exactly:
1.  **Synchronize on startup** using a temporary clone to fetch updates from `lease-hunter/` in the remote repository.
2.  **Commit and push in isolation** using `git_push.ps1` (or `git_push.cjs` in cloud environments), ensuring `node_modules`, `personal-repo-temp`, `chrome-debug-profile`, and `scratch` remain excluded.
