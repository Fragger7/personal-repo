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
- **Target Vehicle**: Kia EV9. Target Trim: **GT-Line** (but actively open to **Wind AWD** based on human intelligence showing recent strong deals in TX for $461/mo). The engine MUST autonomously evaluate all AWD variants (e.g. Land AWD, Wind AWD) and pivot if the alternative trims yield a significantly higher overall deal score.
- **Tax Trap States & Bottom Line**: The system will flag "tax trap" states (where tax is levied on the entire vehicle purchase price, not just the leased portion). The engine must actively research and apply manufacturer tax credits (e.g. from Kia Finance) to combat this and prioritize the ultimate bottom-line monthly number.
- **Value Metric (The Secret Sauce)**: We do not use generic boilerplate rules (e.g., "1% of MSRP"). The ultimate metric of a good deal is the **Leasehackr Score** (years of lease value) paired with qualitative current market momentum scraped from forums.
- **Human Intelligence & Advanced Strategies**:
  - **Aged Inventory Targeting**: The scraping engine MUST explicitly seek out vehicles that have been on the lot for **longer than 6 months (180+ days)**. Sources like CarGurus.com should be targeted for this specific metric.
  - **True Cost Baseline**: Knowing the exact Buy Rate Money Factor (MF), Residual Value (RV), and exact manufacturer rebates for the current month is the source of all negotiating power. (e.g., via Leasehackr Rate Findr).
  - **Anti-Padding Negotiation**: The outreach and deal structuring must negotiate a "reasonable % off MSRP" *before* rebates are applied, and explicitly demand the "buy rate MF" to ensure the dealer is not padding the numbers.
- **Autonomous Outreach**: The system will not simply crunch numbers. Once a top-tier deal is identified, the application will generate a highly intelligent, precise, data-driven first-contact email to the dealer to initiate the negotiation on behalf of the user.

## Architecture & Tech Stack Decisions (Locked)
- **Frontend Core**: React, Tailwind CSS, Vite. Focus is on dark-mode, high-fidelity, polished, desktop-first data dashboards.
- **Backend & AI**: Node.js / Express backend routing. We will leverage the explicit use of the `@google/genai` SDK to parse and structure chaotic unstructured data (like Leasehackr forum threads or complex dealer JSONs) into standardized JSON.
- **Data Aggregation (High-ROI Cost Strategy)**: In lieu of defaulting to expensive enterprise APIs, the engine will rely heavily on custom backend scraping architectures paired with AI data extraction. However, we are willing to introduce economical, low-latency API costs (e.g., specialized dealer APIs, reliable proxy networks) if they provide a major product win and drastically improve reliability. Any introduced costs must be strictly manageable for a single individual on an average salary (focusing on micro-transactions and pay-as-you-go, avoiding hefty enterprise subscriptions). This is the **highest priority development task**: proving we can pull real, accurate inventory and programs. The scraping pipeline will run sequentially: 1) Extract baselines (MSRP, Residuals, Money Factor) from Edmunds/forums via Gemini search grounding (`/api/scrape/extract-baselines`), 2) Search regional dealership endpoints for matching inventory, 3) Qualify targets using market momentum and AI reasoning derived from Leasehackr/Reddit chatter. (Phase 1 UI Dashboard constructed).
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