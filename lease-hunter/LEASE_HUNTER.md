# Universal Lease Hunter Engine - System Architecture & Developer Guide

This document contains the complete system architecture, operational decisions, cloud deployment triggers, and step-by-step Git lifecycle workflows for the **Universal Lease Hunter Engine**. It is designed to serve as a comprehensive knowledge source for both human developers and autonomous AI agents (LLMs).

---

## 📌 Project Overview
The Universal Lease Hunter Engine is an autonomous AI lease broker protocol currently in its **Phase 1 Proof of Concept** targeting the **Kia EV9 GT-Line**. We have fully transitioned from a legacy Streamlit prototype to a **modern full-stack React and Express architecture**. It is designed to find active vehicle inventory, retrieve live lease terms (MSRPs, residuals, money factors, incentives) via custom backend scraping architectures and Gemini AI extraction, and compute precise lease payments across different jurisdictions with complex, multi-state tax rules.

---

## 🏗️ Phase 1 Architecture & Core Components

### 1. Market Intel & Scraping Pipeline (High-ROI Cost Strategy)
The core aggregation engine relies on backend Node.js fetching logic combined with `@google/genai`. 
*   **Sequential Scraping**: 1) Extract baselines (MSRP, Residuals, Money Factor) from Edmunds/forums via Gemini search grounding (`/api/scrape/extract-baselines`), 2) Search regional dealership endpoints for matching inventory (`/api/scrape/search-inventory`), 3) Qualify targets using market momentum and AI reasoning derived from Leasehackr/Reddit chatter. (PoC Dashboard UI complete).
*   **Zero-Cost Bias**: Prioritizes public domains (dealerships, Edmunds) to fetch live data at $0 cost. Minor, low-latency API costs are permissible strictly if they fall within micro-transaction budgets suitable for a solo developer.
*   **Aged Inventory Priority (Human Intelligence)**: The scraping engine must prioritize and explicitly identify vehicles that have been on the lot for **longer than 6 months (180+ days)**. Integrations with sources like CarGurus should be explored specifically for this metric.
*   **True Baseline Defense**: The engine must ensure it gets the absolute base Buy Rate MF and RV, utilizing sources like Leasehackr Rate Findr.

### 2. Deal Engine & Tax Trap Simulator
Computes depreciation, rent charges, and state-specific tax burdens (prioritizing the ultimate bottom-line monthly number). (Simulator UI complete).
*   **Tax Trap Focus (ZIP 78665, Texas)**: Evaluates scenarios where taxes are levied on the entire vehicle purchase price. The engine actively researches and applies manufacturer tax credits (e.g. from Kia Finance) to combat this.
*   **Value Metric**: Utilizes the **Leasehackr Score** combined with current market momentum, eschewing generic boilerplate rules.
*   **Intelligent Trim Pivots**: The engine autonomously evaluates alternative AWD variants (Land AWD, Wind AWD) and pivots if alternative trims yield significantly higher overall deal scores (e.g., acknowledging recent strong deals on the Wind trim in TX).

### 3. CRM & Autnomous Outreach
A robust module for tracking active leads and negotiating deals. (Tracker UI complete).
*   **Firebase Persistence**: Firestore serves as the session cache for heavy data computations and the foundational CRM for tracking dealer outreach.
*   **AI Broker Negotiation**: Once a top-tier deal is targeted, the system generates highly intelligent, precise, data-driven first-contact emails to dealers.
*   **Anti-Padding Strategy**: The outreach must explicitly request quotes based on the **buy-rate Money Factor** and negotiate a pure **dealer discount % off MSRP** *before* manufacturer rebates are applied, ensuring dealers do not pad the numbers.

---

## 🛠️ Technology Stack
*   **Frontend**: React, Tailwind CSS, Vite (Dark-mode, high-fidelity UI).
*   **Backend**: Node.js & Express (TypeScript).
*   **Database/CRM**: Firebase Firestore.
*   **AI Integration**: Google GenAI TypeScript SDK (`@google/genai`).

---

## ☁️ Cloud Deployment Pipeline
The application runs as a modern Vite+Express full-stack application.
*   **Development**: Run `npm run dev` to start the local `vite` and `express` dev servers in AI Studio or local node environments.
*   **Build**: Run `npm run build` to compile the React frontend into static assets and bundle the server into a standard CommonJS target.
*   **Production Start**: `npm run start` launches the node backend serving the API routes and static frontend.

---

## 🤖 AI Agent Git Operations Lifecycle (Important for LLMs)

Follow the rules defined in [.agents/AGENTS.md](file:///C:/Development/Apps/Lease%20Hunter/.agents/AGENTS.md) exactly:
1.  **Synchronize on startup** using a temporary clone to fetch updates from `lease-hunter/` in the remote repository.
2.  **Commit and push in isolation** by copy-staging modifications into a temporary clone of the mono-repo, staging only the `lease-hunter/` folder, and pushing back to `main`.
