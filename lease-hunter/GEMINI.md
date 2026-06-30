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
  - **Aged Inventory Targeting**: Dealer websites notoriously hide intake dates. To reliably scrape "Days on Lot" (targeting 180+ days), the engine MUST target aggregators (like CarGurus, CarEdge) that track cross-web VIN history, rather than individual dealer sites.
  - **True Cost Baseline**: Knowing the exact Buy Rate Money Factor (MF), Residual Value (RV), and exact manufacturer rebates for the current month is the source of all negotiating power. (e.g., via Leasehackr Rate Findr). We will not blindly trust human anecdotes; the AI must mathematically reconstruct and verify deals using live baselines.
  - **Anti-Padding Negotiation**: The outreach and deal structuring must negotiate a "reasonable % off MSRP" *before* rebates are applied, and explicitly demand the "buy rate MF" to ensure the dealer is not padding the numbers.
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
- **UI Progress**: The `IntelDashboard` has been constructed and wired up to the scraping engine APIs (`/api/scrape/*`). Loading states and error handling for rate limits have been added.
  - *Note*: Currently, clicking on the fetched inventory results does nothing. The deal calculator changes monthly payments upon initialization but lacks transparency regarding the underlying math and reasoning.
- **Backend/Scraping Engine (`server/scraping.ts`)**: 
  - Integrated with the `@google/genai` API using the `gemini-2.5-flash` model for search grounding to pull high-quality baseline MF/RV and market momentum data.
  - Implemented an in-memory server cache (12-hour expiry) to prevent spamming the Gemini API on repeated searches.
  - Implemented local JSON file snapshotting (`data/snapshots/`) to preserve historical pulls for long-term learning.
  - **Option A Deployed**: Transitioned the inventory search from Gemini Search Grounding to a Direct Dealer API fetch structure. It dynamically bypasses the quota limitations. Currently simulating data points due to backend firewalls (Cloudflare/WAFs) in the container environment, but structurally generates the exact UI state needed to test the application.

## Next Steps for the AI Assistant upon Resuming
1. **Explain and Expose Calculator Logic**: Add transparency to the Deal Calculator UI so the user clearly understands how the monthly payment is derived from the MSRP, Money Factor, Residual Value, and taxes.
2. **Wire Up Inventory Clicks**: Connect the UI so that clicking on a specific vehicle result populates the Deal Calculator with that vehicle's exact parameters to structure a deal.
3. **Implement Option B (Apify Integration)**: Begin building out the robust aggregator logic using Apify. This will fetch real-world cross-web VIN histories and highly accurate "Days on Lot" data from aggregators like CarGurus, which will solve the WAF/Cloudflare blocking issues we see hitting dealers directly from the container.
4. **Review Apify Key setup with User**: Guide the user on testing a single Apify account API key before setting up the round-robin 3-account fallback array to measure rate limits.