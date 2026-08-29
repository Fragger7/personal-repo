# Universal Lease Hunter Engine - System Architecture & Developer Guide

This document contains the complete system architecture, operational decisions, cloud deployment triggers, and step-by-step Git lifecycle workflows for the **Universal Lease Hunter Engine**. It is designed to serve as a comprehensive knowledge source for both human developers and autonomous AI agents (LLMs).

---

## 📌 Project Overview
The Universal Lease Hunter Engine is an autonomous AI lease broker protocol currently in its **Phase 1 Proof of Concept** targeting the **Kia EV9 GT-Line**. We have fully transitioned from a legacy Streamlit prototype to a **modern full-stack React and Express architecture**. It is designed to find active vehicle inventory, retrieve live lease terms (MSRPs, residuals, money factors, incentives) via custom backend scraping architectures and Gemini AI extraction, and compute precise lease payments across different jurisdictions with complex, multi-state tax rules.

---

## 🏗️ Phase 1 Architecture & Core Components

### 1. Market Intel & Sourcing Pipeline (Multi-Layer Architecture)
The core aggregation engine relies on backend Node.js fetching logic combined with `@google/genai` and Chrome Remote Debugging Protocol (CDP) attachment:
*   **CarEdge Aggregator Node (`server/scraping.ts`)**: Bypasses Cloudflare using direct REST API requests. It dynamically loops and paginates through results (up to 5 pages / 250 vehicles) to gather wide regional coverage in seconds.
*   **Dealer-Direct Headless Node (`server/crawler/scrape-local-dealers-headless.ts`)**: Bypasses strict dealer firewalls (Akamai EdgeSuite) via stealth Playwright automation. It intercepts raw `DDC.dataLayer` and DI internal JSON payloads to extract pure ground-truth MSRPs, exact internet selling prices (discounts), and hidden `inventoryDate` metrics for true Days on Lot calculations.
*   **Sequential Baseline & Captive Rate Verification Engine (`server/scraping.ts`)**: 
    - Extracts live Tier 1 Buy Rate Money Factors (MF), Residual Values (RV%), and regional Lease Cash from Edmunds forums and captive lender bulletins (Kia Finance America).
    - Clusters regional zones (e.g. Austin / Round Rock Metro Zone 78665, 787xx, 782xx) and tracks monthly program freshness.
    - Features a **1-Click Forum Post Generator & Modal Alert**: Pre-formats exact inquiries for Edmunds moderators, links directly to the discussion thread, and allows 1-click push alerts to the user's Telegram.
    - Ingests Leasehackr Calculator and Rate Findr share links via `/api/scrape/parse-ratefindr` for instant high-confidence baseline overrides.
*   **CarGurus CDP Aggregator Node (`server/crawler/cargurus-cdp-master.ts`)**: Connects over Chrome Remote Debugging Protocol (CDP) on port 9222 to capture live CarGurus XHR streams and VDP URLs with zero Cloudflare/DataDome blocks.
*   **3-Node Triangulated Aggregation Engine**: The `IntelDashboard.tsx` simultaneously triggers all three channels (CarEdge + Dealer Direct + CarGurus), dynamically merging and deduplicating data by VIN, prioritizing ground-truth dealership data while preserving aggregator deal tags and direct links.
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
