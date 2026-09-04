# 📋 WORKSTATION DEAL HUNTER: TECHNICAL BACKLOG & UI/UX REDESIGN

This document details the active prioritized backlog for **Workstation Deal Hunter**, including UI/UX enhancements, new scraper channels, and self-learning valuation algorithms.

---

## 🎨 EPIC 1: World-Class UI/UX & Dashboard Redesign (Top Priority)

### Problem Statement:
- The current sidebar takes up too much horizontal screen real estate.
- Rendering deal cards one-by-one vertically causes excessive scrolling on large feeds.
- Information density can be significantly improved with polished, executive data grids and compact, high-contrast hardware cards.

### UI/UX Implementation Plan:
1. **Collapsible Top Filter Toolbar / Floating Action Drawer**:
   - Move main hardware filters (Brand pills, Min RAM, Min SSD, GPU Tier, Sort By) into a sleek, horizontal sticky top bar or an expandable modern drawer (`st.expander` / custom CSS ribbon).
   - Collapse the heavy sidebar into a slim status rail (System Health, 24/7 Daemon Status, Gemini Token Quota tracker).
2. **High-Density "Executive Workstation" Deal Cards**:
   - Redesign deal cards with a compact, modern grid (2 or 3 cards per row on desktop).
   - Display clear visual badges:
     - 🦄 **`UNICORN`** (Gold badge for Score $\ge 9.5$)
     - 🔥 **`HIGH YIELD`** (Emerald badge for Score $8.8 - 9.4$)
     - 🧠 **`64GB DDR5`** (Cyan pill)
     - ⚡ **`+$850 SPREAD`** (Green arbitrage pill)
   - Include direct, high-contrast action buttons: **`Open Listing ↗`** and **`Copy Deal Link`**.
3. **Dual View Mode Switcher (`Grid Cards` vs. `High-Density Data Table`)**:
   - Provide an instant toggle:
     - **Card View:** Rich visual inspection with spec pills and AI reasoning.
     - **Table View:** Compact, sorting-enabled financial grid (Model | Price | FMV | Profit | RAM | GPU | Source | Action).
4. **Performance & Caching Optimization**:
   - Implement `@st.cache_data(ttl=60)` for fast instant re-renders when switching filters or sorting options.
   - Zero layout shifts during filtering.

---

## 🏭 EPIC 2: Enterprise Refurbished Suite (✅ COMPLETED)

1. **`DellRefurbishedCollector` (DFS Certified Workstations)**:
   - ✅ **Completed**: Live scraper for Dell Precision (`model_family=266`) and XPS (`model_family=268`) with automated 40%–50% sitewide coupon deduction.
2. **`LenovoOutletCollector` (ThinkPad P-Series Refurb)**:
   - ✅ **Completed**: Live scraper for certified ThinkPad P1, P16, P14s, and X1 Extreme.
3. **`Strict Workstation Whitelisting`**:
   - ✅ **Completed**: Hard-excludes all Latitude 3000/5000 budget laptops, IdeaPads, Yogas, and 15W U-series CPUs.
4. **`EBayBrowseAPICollector` (Production REST API Activation)**:
   - Built with dual-mode OAuth2 Browse API + canonical marketplace query fallback.

---

## 🧠 EPIC 3: Adaptive "Self-Learning" FMV Price Index

1. **Dynamic Rolling Exponential Moving Average (EMA)**:
   - Maintain `price_benchmarks.json` locally.
   - Automatically recalibrate component baselines (CPU, RAM, GPU) based on real clearing prices over time.
   - Zero additional AI cost ($0.00 / purely mathematical local adjustment).

---

## 📱 EPIC 4: Mobile & Notification Refinements

1. **Daily Morning / Evening High-Yield Recap**:
   - Send an optional 8:00 AM summary of top 3 overnight deals.
2. **Discord Embed Card Integration**:
   - Activate rich Discord webhooks alongside Telegram.

---

## 🕷️ EPIC 5: Advanced Ingestion & Direct Scraping (✅ COMPLETED)

1. **`Swappa Direct TLS Ingestion`**:
   - ✅ **Completed**: Implemented direct model directory scraping via `curl_cffi` (`chrome124`), syndicating 100+ live units in 4.8s without headless browser overhead.
2. **`Warmed Chromium Session Engine for eBay`**:
   - ✅ **Completed**: Built cookie-jar warmup against `https://www.ebay.com/` with browser client hints (`Sec-Ch-Ua`), yielding 1,000+ targeted listings with zero CAPTCHAs.
3. **`Unicorn Hunter Quality Gatekeeper`**:
   - ✅ **Completed**: Enforced hard 15"–16" display minimum, 32GB RAM minimum, sub-$1,700 budget ceiling, and legacy model blacklisting.
4. **`Multi-Item Reddit Liquidation Table Parser`**:
   - ✅ **Completed**: Built `parse_markdown_tables` in `RedditCollector` to parse multi-row HTML and Markdown tables in bulk lots on `r/hardwareswap` and `r/homelabsales` with automatic strikethrough, sold, and pending exclusion.
5. **`30-Day Persistent Tombstone Registry`**:
   - ✅ **Completed**: Built atomic tombstone tracking (`tombstones.json`, `storage.py`, `daemon.py`) to permanently prevent sold/reaped listings (e.g. phantom $610 Lenovo) from entering infinite re-alert and ingestion loops.
6. **`Multi-Tier Auction Watchlist & Valuation Engine`**:
   - ✅ **Completed**: Added auction metadata parsing (`is_auction`, `bid_count`, `time_left`), strike ceiling valuation ($0.82 \times \text{FMV}$), score capping ($\le 8.2$), instant alert suppression, and dedicated Executive Briefing & Dashboard Watchlist display.
