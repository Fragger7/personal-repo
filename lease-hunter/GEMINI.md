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
- **Data Aggregation (High-ROI Cost Strategy)**: 
  - **Gemini Quota Management**: Gemini Search Grounding is brilliant but quota-heavy. It will be strictly reserved for "Brain" tasks: parsing unstructured forums for baseline MF/RV and market momentum (`/api/scrape/extract-baselines`).
  - **Inventory Scraping**: To avoid `429 RESOURCE_EXHAUSTED` limits, heavy inventory scraping will transition toward dedicated, low-cost DOM parsers or proxy APIs (e.g., Apify, Firecrawl) rather than burning Gemini tokens. Any introduced costs must be strictly manageable (micro-transactions).
  - **Workflow**: 1) Extract baselines via Gemini, 2) Search regional inventory via dedicated parsers, 3) Qualify targets using AI reasoning.
- **Ultrathinking Architecture & Future Traps**: The architecture must anticipate severe scraping blocks, rapid state changes, and dealership inventory API shifts. We will design the backend with modular abstraction layers so data sources can be swapped or upgraded without breaking the core deal engine.
- **Database / CRM Persistence**: Firebase Firestore. The platform provides a highly scalable NoSQL document architecture with a robust free tier. It will serve as both our session cache for the heavy data computations and our foundational CRM for tracking dealer outreach.

## Current Project State (As of Last Session)
- **UI Progress**: The `IntelDashboard` has been constructed and wired up to the scraping engine APIs (`/api/scrape/*`). Loading states and error handling for rate limits have been added.
- **Backend/Scraping Engine (`server/scraping.ts`)**: 
  - Integrated with the `@google/genai` API using the `gemini-2.5-flash` model for search grounding and parsing car lease baselines, inventory, and market momentum.
  - Successfully handles API rate limit errors (429 RESOURCE_EXHAUSTED) gracefully by sending clean error messages directly to the frontend UI so users are aware of quota issues.
- **Bugs/Issues to Resolve**:
  - The application gives a "Page not found" error when accessed directly. Need to investigate the Express Vite middleware or route handling for the client (checking `server.ts` SPA routing vs server API routing).
  - Rate limiting issues with the Gemini API quota.

## Next Steps for the AI Assistant upon Resuming
1. **Fix routing issues**: Resolve the "Page not found" / "404" errors so the React SPA loads correctly on the public preview URL. Ensure `server.ts` correctly falls back to Vite middleware or static `index.html` depending on the environment.
2. **Continue Scraping Refinement**: Look into alternative caching mechanisms, mock data placeholders, or fallback APIs if the Gemini API quota continues to be a blocker for testing the full `baselines -> inventory -> analysis` pipeline.