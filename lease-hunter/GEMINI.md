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
  - **Aggregator Supremacy**: Human success explicitly validates **CarGurus** as the optimal platform for finding comprehensive dealer inventory and executing initial contact.
  - **Trim-Level Agility**: Recent human intelligence confirmed a massive payment drop (e.g., ~$741 down to ~$471) by pivoting from GT-Line to Wind trim. The engine MUST actively model these trim step-downs and surface them if the value delta is this extreme.
  - **Aged Inventory Targeting**: Dealer websites notoriously hide intake dates. To reliably scrape "Days on Lot" (targeting 180+ days), the engine MUST target aggregators (like CarGurus, CarEdge) that track cross-web VIN history, rather than individual dealer sites.
  - **True Cost Baseline**: Knowing the exact Buy Rate Money Factor (MF), Residual Value (RV), and exact manufacturer rebates for the current month is the source of all negotiating power. (e.g., via Leasehackr Rate Findr). We will not blindly trust human anecdotes; the AI must mathematically reconstruct and verify deals using live baselines.
  - **Anti-Padding Negotiation**: The outreach and deal structuring must negotiate a "reasonable % off MSRP" *before* rebates are applied, and explicitly demand the "buy rate MF" to ensure the dealer is not padding the numbers.
  - **Baseline Validation & Confidence Scoring**: Because forums (Leasehackr/Edmunds) can be noisy or contain outdated information, the scraping engine must *cross-reference* multiple sources (e.g. Edmunds vs. Leasehackr vs. Reddit). The engine will output a dynamic `Confidence Score` (0-100) and explicitly cite whether the data points agree across platforms. We will NOT rely on single unverified anecdotes.
  - **Transparent Negotiation Leverage**: Bringing structural deal knowledge (e.g., showing the app's deal calculator) directly to dealerships, either in person or via chat, bypasses traditional sales tactics. Demonstrating that you have the baseline numbers mathematically reconstructed forces them to compete on the actual "% off MSRP" rather than hiding margin in the MF or accessories.
  - **The "Golden" Outreach Template**: Initial contact should be made via CarGurus or Dealer Website Chat using this exact data-driven structure: *"I just paid my last payment on a [Previous Vehicle] lease. Looking for another lease on an [Target Vehicle] by the end of the month. [Term]mo/[Mileage]k mi/yr lease. Base money factor ([MF]). RV [RV]% [Tax Credit Details if applicable]. 1st month payment down. Reasonable % off MSRP, after $[Lease Cash] manufacturer lease cash."*
- **Autonomous Outreach**: The system will not simply crunch numbers. Once a top-tier deal is identified, the application will generate a highly intelligent, precise, data-driven first-contact email to the dealer to initiate the negotiation on behalf of the user.

## Architecture & Tech Stack Decisions (Locked)
- **Frontend Core**: React, Tailwind CSS, Vite. Focus is on dark-mode, high-fidelity, polished, desktop-first data dashboards.
- **Backend & AI**: Node.js / Express backend routing. We will leverage the explicit use of the `@google/genai` SDK to parse and structure chaotic unstructured data (like Leasehackr forum threads or complex dealer JSONs) into standardized JSON.
- **Data Aggregation & Quota Strategy**: 
  - **Gemini Quota Management (Strict limit)**: There are NO backup accounts for Gemini Pro. Gemini Search Grounding is brilliant but quota-heavy. It will be strictly reserved for "Brain" tasks: parsing unstructured Leasehackr forums for baseline MF/RV and market momentum (`/api/scrape/extract-baselines`). It will NOT be used for bulk inventory scraping.
  - **Option A - Dealer Backend Reverse Engineering**: Building custom scrapers to hit individual dealer inventory APIs directly. This is free and requires zero setup, but data can be skewed (dealers doctor/remove listings, hiding true "Days on Lot") and it is brittle to API changes.
  - **Option B - Dedicated Third-Party Scrapers (e.g., Apify) with Dummy Accounts**: Using dedicated scraping platforms to hit aggregators like CarGurus. This provides highly reliable cross-web VIN tracking and true "Days on Lot". To keep costs at absolute zero, we will operate a round-robin strategy utilizing ~3 fallback dummy accounts to bypass free tier rate limits.
  - **Workflow**: 1) Extract baselines via Gemini, 2) Search regional inventory via Option A or B, 3) Qualify targets using AI reasoning.
- **Ultrathinking Architecture & Future Traps**: The architecture must anticipate severe scraping blocks, rapid state changes, and dealership inventory API shifts. We will design the backend with modular abstraction layers so data sources can be swapped or upgraded without breaking the core deal engine.
- **Database / CRM Persistence**: Firebase Firestore. The platform provides a highly scalable NoSQL document architecture with a robust free tier. It will serve as both our session cache for the heavy data computations and our foundational CRM for tracking dealer outreach.

## Current Project State (As of Last Session)
- **UI Progress**: 
  - The `IntelDashboard` provides dynamic input parameters for Make, Model, Trim, Year, and Target ZIP.
  - Added "Manual Intel Dump" feature to accept raw Ctrl+A text pastes from CarGurus.
  - **Needs Refactor**: The UI workflow is getting messy and the user journey between searching, pasting, and analyzing is disjointed. A structural UX refactor is needed to make the flow intuitive.
  - Clicking an acquired target from the dashboard successfully wires the deal parameters (MSRP, Target Discount, Baseline MF, Baseline RV, Dealer Name, and VIN) directly into the `TaxSimulator` (Deal Structuring) tab.
  - **Calculator Logic Exposed**: The `TaxSimulator` now explicitly displays the mathematical breakdown (Net Cap Cost, Depreciation, Rent Charge, Base Payment) to empower the user during negotiations.
- **Backend/Scraping Engine (`server/scraping.ts`)**: 
  - Gemini Search Grounding accurately extracts baseline numbers, utilizing `gemini-3.5-flash` with JSON schema enforcement. It includes a `Confidence Score` and a `Reasonable Discount %`.
  - **Apify Integration Note**: The live Apify call to `scraper-engine/cargurus-com-scraper` threw access errors due to cost/tier limits.
  - **Option C (Copy-Paste Intelligence)**: Implemented an endpoint to parse unstructured text dumps from CarGurus using Gemini. It yields some results but struggles to accurately capture "Days on Lot", "Dealer", and "VIN" from a standard frontend text selection due to aggregator DOM structures hiding data.
- **Database / CRM Foundation**: 
  - Firebase setup was declined. The `OutreachCRM` currently relies on local JSON file storage (`data/crm.json`) via the Express backend.
  - `localStorage` was implemented as an interim solution to persist manually scraped inventory on the frontend across reloads.

## Next Steps for the AI Assistant upon Resuming
1. **Inventory Collection Brainstorming & Fixes**: Option C's dirty parsing needs improvement. Investigate alternative smart options: 
   - Option 1 Refined: A manual entry form or a bookmarklet that cleanly captures specific URLs, dates, and vehicle details into our storage, with a background check for dead links.
   - Option 2: Explore parsing daily automated email alerts from CarGurus.
   - Option 3: Improve the Gemini parsing prompt/strategy to handle the dirty CarGurus DOM text better.
2. **UI/UX Workflow Refactor**: Clean up the `IntelDashboard` and the transition to the Deal Calculator. The flow must be pristine, logical, and less messy.
3. **Refine OutreachCRM & The "Golden" Template**: Ensure the UI gracefully displays saved leads, allows the user to copy the "Golden" template, and track the status of dealer responses.
4. **Handle Tax Scenarios**: Implement robust logic in the Tax Simulator to handle "tax trap" states (like TX, ZIP 78665) where tax is levied on the entire vehicle purchase price.