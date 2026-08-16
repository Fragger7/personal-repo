# Agent Directives: Universal Lease Engine - Kia EV9 Proof of Concept

## Core App Philosophy & Goals
1. **World's Best Digital Lease Broker**: This application's ultimate purpose is to serve as the absolute best car lease broker in North America. Its #1 priority is unmatched access to data, market knowledge, and computational/financial logic—proactively sourcing real-time data, incentives, and inventory so the user doesn't have to lift a finger.
2. **AI as the Broker, Not the User**: The system must do the heavy lifting. It sources the car listings, dealer incentives, latest MSRP, and runs the lease math autonomously. The user is the client; the AI is the broker.
3. **Phase 1 Proof of Concept (Kia EV9)**: The immediate focus is entirely on the **Kia EV9**, specifically evaluating the **GT-Line** trim. The concept of "value" is paramount: the tool must be intelligent enough to flag alternatives (e.g., Land AWD or Wind AWD) if changing trims or factoring in the age of inventory yields significantly lower costs and higher overall lease value. 
4. **Relentless Deep Research**: Market intelligence is the pillar of this application. The tool must constantly rely on deep research—leveraging Leasehackr, Reddit, Edmunds, dealer websites, and captive lender data—to leave no stone unturned.
5. **Elite UI/UX Standards**: While data and brainpower are the top tier priority, UI/UX remains extremely critical to the professional experience. The app will utilize high-fidelity, polished, and intuitive user journeys (inventory discovery -> deal structuring -> dealer outreach).
6. **Future-Proof Scalability & CRM**: The architecture must support expansion to other vehicles after the EV9 proof-of-concept is complete. In the background, design decisions should gracefully leave room for full-featured CRM and lead tracking capabilities as the application scales.

## Phase 1 Proof of Concept Constraints (Locked)
- **Target Customer Profile**: ZIP 78665 (Round Rock, TX region - a known "tax trap" state), 300-mile search radius, top-tier credit score (Tier 1). No conditional rebates (military/grad) for this PoC.
- **Target Vehicle**: Kia EV9. Target Trim: **GT-Line** (Primary Goal). The engine MUST autonomously evaluate all AWD variants (e.g. Land AWD, Wind AWD) and pivot ONLY if the alternative trims yield a significantly disproportionate lease value. Wind is not gospel; GT-Line remains the objective.
- **Tax Trap States & Bottom Line**: The system will flag "tax trap" states (where tax is levied on the entire vehicle purchase price, not just the leased portion). The engine must actively research and apply manufacturer tax credits (e.g. from Kia Finance) to combat this and prioritize the ultimate bottom-line monthly number.
- **Value Metric (The Secret Sauce)**: We do not use generic boilerplate rules (e.g., "1% of MSRP"). The ultimate metric of a good deal is the **Leasehackr Score** (years of lease value) paired with qualitative current market momentum scraped from forums.
- **Human Intelligence & Advanced Strategies (Vetted)**:
  - **Calculator Back-Solving**: Users should be able to input a target "% off MSRP" into the deal calculator to instantly back-calculate their expected monthly payments. This is the primary lever for deal structuring.
  - **Aggregator Supremacy & Multi-Node Architecture**:
    - **Cars.com via CDP (Primary Workhorse)**: Provides automated unblocked regional search, Days on Lot, and massive dealer discounts ($13k+ off).
    - **Dealer-Direct Network Scrapers**: Scrapes local franchised dealerships (Group 1 Kia South Austin, Round Rock Kia) for direct showroom stock, Monroney labels, and internet sales contacts.
    - **CarGurus (Outreach & Intel Channel)**: Optimal platform for initial contact and sending the "Golden Outreach Template" via dealer messaging.
  - **Trim-Level Agility**: Recent human intelligence confirmed a massive payment drop (e.g., ~$741 down to ~$471) by pivoting from GT-Line to Wind trim. The engine MUST actively model these trim step-downs and surface them if the value delta is this extreme.
  - **Aged Inventory Targeting**: Dealer websites notoriously hide intake dates. To reliably scrape "Days on Lot" (targeting 180+ days), the engine queries aggregators (Cars.com, CarEdge) via CDP and tracks local intake history.
  - **True Cost Baseline**: Knowing the exact Buy Rate Money Factor (MF), Residual Value (RV), and exact manufacturer rebates for the current month is the source of all negotiating power. (e.g., via Leasehackr Rate Findr). We will not blindly trust human anecdotes; the AI must mathematically reconstruct and verify deals using live baselines.
  - **Anti-Padding Negotiation**: The outreach and deal structuring must negotiate a "reasonable % off MSRP" *before* rebates are applied, and explicitly demand the "buy rate MF" to ensure the dealer is not padding the numbers.
  - **Baseline Validation & Confidence Scoring**: Cross-references multiple sources (Edmunds vs. Leasehackr vs. Reddit). The engine outputs a dynamic `Confidence Score` (0-100) and explicitly cites whether data points agree across platforms.
  - **The "Golden" Outreach Template**: Initial contact should be made via CarGurus or Dealer Website Chat using this exact data-driven structure: *"I just paid my last payment on a [Previous Vehicle] lease. Looking for another lease on an [Target Vehicle] by the end of the month. [Term]mo/[Mileage]k mi/yr lease. Base money factor ([MF]). RV [RV]% [Tax Credit Details if applicable]. 1st month payment down. Reasonable % off MSRP, after $[Lease Cash] manufacturer lease cash."*

## Architecture & Tech Stack Decisions (Locked)
- **Frontend Core**: React, Tailwind CSS, Vite. Focus is on dark-mode, high-fidelity, polished, desktop-first data dashboards.
- **Backend & AI**: Node.js / Express backend routing (`@google/genai` SDK for forum unstructured data parsing).
- **Crawler & Anti-Bot Architecture (Vetted from `vehicle-tracker-main`)**:
  - **Chrome CDP Attachment (`start-chrome-debug.bat` + `chromium.connectOverCDP('http://127.0.0.1:9222')`)**: Connects to a user-launched Chrome instance carrying genuine OS window tokens, real residential cookies, and clean TLS fingerprints, achieving 100% HTTP 200 responses with zero DataDome/Cloudflare 403 blocks.
  - **Embedded JSON Parsing**: Extracts raw server state and XHR streams directly, bypassing brittle DOM scrapers.
  - **Telegram Push Alert Engine (`server/services/telegram.ts`)**: Delivers real-time deal cards with working direct vehicle detail URLs straight to the user's phone.
- **Database / CRM Persistence**: Firebase Firestore & local persistent JSON (`data/inventory.json` & `data/crm.json`).

## Current Project State (Verified & Working)
- **Breakthrough Live Inventory Sourcing**:
  - **Cars.com CDP Node (`server/crawler/scrape-cars-com-new-ev9.ts` & `extract-cars-com-vdp-details.ts`)**: Successfully extracted 22 brand new 2026 Kia EV9s (GT-Line, Land, Wind) within 50 miles of ZIP 78665.
  - **Verified Live Car Extracted**: 2026 Kia EV9 GT-Line AWD (VIN `5XYAEFS58TG019993`), MSRP $77,245, **Sale Price $64,002 ($13,243 off MSRP / 17.1% discount)**, **115 Days on Lot**, with direct working hyperlink [`https://www.cars.com/vehicledetail/940466ec-7560-455e-bbd3-ded328c62ff0/`](https://www.cars.com/vehicledetail/940466ec-7560-455e-bbd3-ded328c62ff0/).
  - **Live Mobile Telegram Delivery**: Confirmed working deal notification cards delivered directly to the user's phone.
- **Git Sync & Tooling**:
  - `git_push.ps1` updated with clean recursive workspace export excluding `node_modules`, `personal-repo-temp`, `chrome-debug-profile`, and `scratch`. Commits push cleanly to GitHub `main`.

## Next Steps for the Next Session
1. **Wire Cars.com CDP Node to UI**: Connect the live 22-car Cars.com crawler to the React `IntelDashboard` "🔄 Scan Regional Inventory" button and persist to `data/inventory.json`.
2. **Deal Calculator Auto-Seeding**: Take the live $13,243 discounted 2026 EV9 GT-Line and auto-calculate the exact Texas monthly payment (ZIP 78665) with Kia Finance lease cash and buy-rate MF.
3. **UI/UX Workflow Refactor**: Clean up the `IntelDashboard` user journey between regional discovery, deal structuring, and dealer outreach.