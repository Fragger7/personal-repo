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

## 🏭 EPIC 2: New Enterprise Refurbished & Auction Ingestion

1. **`DellRefurbishedCollector` (Official Corporate Lease Returns)**:
   - Target: `dellrefurbished.com`.
   - Features: Automatic scraping of active 40%–50% sitewide coupons from `dellrefurbished.com/coupons` with post-coupon price recalculation.
2. **`LenovoOutletCollector` (Certified Scratch & Dent)**:
   - Target: `lenovo.com/us/en/outletus/`.
   - Features: Direct ingestion of ThinkPad P1, P16, and T16 certified refurb stock.
3. **`ShopGoodwillCollector` (Government / Corporate Donation Auctions)**:
   - Target: `shopgoodwill.com`.
   - Features: Sub-$300 auction sniping for Dell Precision and ThinkPad laptops ending within 24 hours.
4. **`EBayBrowseAPICollector` (Production REST API Activation)**:
   - Switch to live OAuth2 Buy-It-Now queries when client credentials are provided.

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
