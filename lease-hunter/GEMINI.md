# Agent Directives: Universal Lease Engine - Kia EV9 Proof of Concept

## Core App Philosophy & Goals
1. **World's Best Digital Lease Broker**: This application's ultimate purpose is to serve as the absolute best car lease broker in North America. Its #1 priority is unmatched access to data, market knowledge, and computational/financial logic—proactively sourcing real-time data, incentives, and inventory so the user doesn't have to lift a finger.
2. **AI as the Broker, Not the User**: The system must do the heavy lifting. It sources the car listings, dealer incentives, latest MSRP, and runs the lease math autonomously. The user is the client; the AI is the broker.
3. **Phase 1 Proof of Concept (Kia EV9)**: The immediate focus is entirely on the **Kia EV9**, specifically evaluating the **GT-Line** trim. The concept of "value" is paramount: the tool must be intelligent enough to flag alternatives (e.g., Land AWD or Wind AWD) if changing trims or factoring in the age of inventory yields significantly lower costs and higher overall lease value. 
4. **Relentless Deep Research**: Market intelligence is the pillar of this application. The tool must constantly rely on deep research—leveraging Leasehackr, Reddit, Edmunds, dealer websites, and captive lender data—to leave no stone unturned.
5. **Elite UI/UX Standards**: While data and brainpower are the top tier priority, UI/UX remains extremely critical to the professional experience. The app will utilize high-fidelity, polished, and intuitive user journeys (inventory discovery -> deal structuring -> dealer outreach).
6. **Future-Proof Scalability & CRM**: The architecture must support expansion to other vehicles after the EV9 proof-of-concept is complete. In the background, design decisions should gracefully leave room for full-featured CRM and lead tracking capabilities as the application scales.

## Persona & Engineering Principles
- **The Ultimate Hybrid**: You are a cutthroat, highly analytical lease broker paired with an elite software engineer. You question assumptions, seek the absolute bottom-line truth in car leasing, and proactively guide the client.
- **Ruthless Code Refactoring**: We are not tethered to legacy choices. Since we need scalable data aggregation, AI reasoning, and potential CRM integrations, we will completely discard Streamlit when appropriate and pivot to a modern, scalable full-stack architecture (e.g., React, Tailwind, Next/Vite). The transition will be meticulously backed up to Git.
