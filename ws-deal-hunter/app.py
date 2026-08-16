"""
Workstation Deal Hunter - Interactive Streamlit Dashboard
=========================================================
Standalone Streamlit application rendering KPI metrics, score sliders,
filter controls, spec badges, and direct buy links.

Usage:
    streamlit run app.py
"""

from __future__ import annotations

import json
import os
from datetime import datetime
from typing import List

try:
    import streamlit as st
except ImportError:
    st = None  # Fallback if executed without streamlit package

from collector import RawListing
from evaluator import GeminiHardwareEvaluator
from notifier import PushoverNotifier
from storage import AtomicDealStorage, DealRecord


def main() -> None:
    if st is None:
        print("[Workstation Deal Hunter] Streamlit not installed. Install via `pip install streamlit` and run `streamlit run app.py`.")
        return

    st.set_page_config(
        page_title="Workstation Deal Hunter",
        page_icon="⚡",
        layout="wide",
        initial_sidebar_state="expanded",
    )

    from pathlib import Path

    db_path = Path(__file__).parent / "deals.json"
    if not db_path.exists() and Path("deals.json").exists():
        db_path = Path("deals.json")

    storage = AtomicDealStorage(filepath=db_path)
    evaluator = GeminiHardwareEvaluator()
    notifier = PushoverNotifier()

    # Custom CSS for dark glass aesthetic and high-contrast typography
    st.markdown(
        """
        <style>
        .stMetric {
            background: #111827;
            padding: 14px;
            border-radius: 8px;
            border: 1px solid #1f2937;
        }
        .deal-card {
            background: #111827;
            border: 1px solid #374151;
            border-radius: 10px;
            padding: 16px;
            margin-bottom: 16px;
        }
        .badge-high {
            background-color: #065f46;
            color: #34d399;
            padding: 4px 10px;
            border-radius: 6px;
            font-weight: 700;
            font-size: 0.85rem;
        }
        .badge-good {
            background-color: #78350f;
            color: #fbbf24;
            padding: 4px 10px;
            border-radius: 6px;
            font-weight: 700;
            font-size: 0.85rem;
        }
        .badge-normal {
            background-color: #1f2937;
            color: #9ca3af;
            padding: 4px 10px;
            border-radius: 6px;
            font-weight: 600;
            font-size: 0.85rem;
        }
        </style>
        """,
        unsafe_allow_html=True,
    )

    # Header
    st.title("⚡ Workstation Deal Hunter")
    st.caption("Autonomous hardware arbitrage monitor syndicating eBay Browse API, Reddit r/hardwareswap, and Swappa RSS.")

    # Sidebar Filters
    st.sidebar.header("🔍 Faceted Search & Filters")
    
    search_query = st.sidebar.text_input("🔎 Search Keywords", placeholder="e.g. P16, M3 Max, 64GB, Ada, i9")

    sort_by = st.sidebar.selectbox(
        "📊 Sort Listings By",
        options=[
            "Deal Score (High to Low)",
            "Asking Price (Low to High)",
            "Asking Price (High to Low)",
            "Arbitrage Profit ($ High to Low)",
            "Arbitrage Margin (% High to Low)",
            "Date Discovered (Newest First)",
        ],
        index=0,
    )

    st.sidebar.subheader("🏷️ Hardware Specifications")
    
    selected_brands = st.sidebar.multiselect(
        "Target Brands",
        options=["ThinkPad", "Precision", "ZBook", "MacBook", "Apple", "Alienware", "MSI", "ASUS", "Lenovo", "Dell", "HP"],
        default=[],
        help="Leave empty to search all brands.",
    )

    col_ram, col_ssd = st.sidebar.columns(2)
    with col_ram:
        min_ram_choice = st.selectbox(
            "Min RAM",
            options=["Any", "16 GB+", "32 GB+", "64 GB+", "128 GB+"],
            index=0,
        )
        min_ram_val = {
            "Any": 0,
            "16 GB+": 16,
            "32 GB+": 32,
            "64 GB+": 64,
            "128 GB+": 128,
        }.get(min_ram_choice, 0)

    with col_ssd:
        min_ssd_choice = st.selectbox(
            "Min SSD",
            options=["Any", "512 GB+", "1 TB+", "2 TB+", "4 TB+"],
            index=0,
        )
        min_ssd_val = {
            "Any": 0,
            "512 GB+": 512,
            "1 TB+": 1024,
            "2 TB+": 2048,
            "4 TB+": 4096,
        }.get(min_ssd_choice, 0)

    gpu_choice = st.sidebar.selectbox(
        "GPU Category",
        options=[
            "All",
            "Dedicated GPU Only",
            "Workstation / Ada GPU",
            "High-End Gaming (RTX 4080/5080+)",
            "Apple Silicon GPU",
        ],
        index=0,
    )

    st.sidebar.subheader("💰 Deal & Price Thresholds")
    min_score = st.sidebar.slider(
        "Min Deal Score (0 - 10)",
        min_value=0.0,
        max_value=10.0,
        value=0.0,
        step=0.1,
        help="Set to >= 8.5 to see high-yield mobile alert candidates only.",
    )

    max_price = st.sidebar.slider(
        "Max Asking Price ($)",
        min_value=100,
        max_value=5000,
        value=3500,
        step=25,
    )

    sources = st.sidebar.multiselect(
        "Data Sources",
        options=["dell_refurbished", "lenovo_outlet", "ebay", "reddit", "goodwill", "syndicated", "swappa"],
        default=["dell_refurbished", "lenovo_outlet", "ebay", "reddit", "goodwill", "syndicated", "swappa"],
        format_func=lambda s: {
            "dell_refurbished": "Dell Refurbished (DFS 50% Coupon)",
            "lenovo_outlet": "Lenovo Outlet (Certified ThinkPads)",
            "ebay": "eBay Live Marketplace",
            "reddit": "Reddit Hardware Hub (r/appleswap, r/hardwareswap, r/homelabsales)",
            "goodwill": "ShopGoodwill (Estate Liquidation)",
            "syndicated": "Syndicated Streams (Woot / Slickdeals Refurbs)",
            "swappa": "Swappa Market",
        }.get(s, s.upper()),
    )

    only_high_yield = st.sidebar.checkbox(
        "Show only High-Yield Alerts (Score >= 8.5 & Price <= $750)",
        value=False,
    )

    st.sidebar.divider()
    if st.sidebar.button("🔄 Sync Live Endpoints Now", use_container_width=True):
        from daemon import DealHunterDaemon
        daemon = DealHunterDaemon()
        with st.spinner("Syndicating eBay, Reddit, and Swappa feeds..."):
            res = daemon.run_cycle()
            st.sidebar.success(f"Sync complete! {res.get('new_evaluated', 0)} new deals evaluated.")

    # Load & Aggregate Data
    all_deals = storage.get_all()
    filtered_deals = storage.filter_deals(
        min_score=min_score,
        max_price=float(max_price),
        sources=sources,
        brands=selected_brands if selected_brands else None,
        min_ram=min_ram_val if min_ram_val > 0 else None,
        min_ssd=min_ssd_val if min_ssd_val > 0 else None,
        gpu_type=gpu_choice,
        search_query=search_query,
        only_high_yield=only_high_yield,
        sort_by=sort_by,
    )
    stats = storage.get_statistics()
    filtered_count = len(filtered_deals)
    filtered_high_yield = len([d for d in filtered_deals if d.deal_score >= 8.5])
    filtered_avg_margin = (
        round(sum(d.arbitrage_margin_pct for d in filtered_deals) / filtered_count, 1)
        if filtered_count > 0
        else 0.0
    )
    filtered_avg_profit = (
        round(sum(d.estimated_profit for d in filtered_deals) / filtered_count, 2)
        if filtered_count > 0
        else 0.0
    )
    filtered_top_score = max([d.deal_score for d in filtered_deals], default=0.0)

    # Top KPI Metrics Banner (Dynamic to Slider Filters)
    col1, col2, col3, col4 = st.columns(4)
    with col1:
        st.metric(
            "Visible Filtered Deals",
            f"{filtered_count} units",
            delta=f"Pool: {stats['total_deals']} total in DB",
        )
    with col2:
        st.metric(
            "🔥 High-Yield (>=8.5)",
            f"{filtered_high_yield} opportunities",
            delta="Pushover Ready",
        )
    with col3:
        st.metric(
            "Avg Arbitrage Margin",
            f"{filtered_avg_margin}%",
            delta=f"+${filtered_avg_profit:.0f} Profit",
        )
    with col4:
        st.metric(
            "Top Deal Score",
            f"{filtered_top_score:.1f} / 10.0",
            delta="Calibrated Valuation",
        )

    st.divider()

    # Main Tabs
    tab_feed, tab_eval, tab_notify, tab_json = st.tabs([
        "📋 Live Deal Feed",
        "🧠 AI Spec & Deal Evaluator",
        "📱 Pushover Alert Dispatcher",
        "💾 Atomic Storage (deals.json)",
    ])

    with tab_feed:
        st.subheader(f"Filtered Results ({len(filtered_deals)} listings)")

        if not filtered_deals:
            st.info("No workstation listings match your filter parameters. Adjust sliders in the sidebar.")
        else:
            for deal in filtered_deals:
                badge_class = "badge-high" if deal.deal_score >= 8.5 else ("badge-good" if deal.deal_score >= 7.5 else "badge-normal")
                score_label = f"⭐ {deal.deal_score}/10"

                with st.container():
                    st.markdown(
                        f"""
                        <div class="deal-card">
                            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                                <span style="font-size: 1.15rem; font-weight: 700;">{deal.title}</span>
                                <span class="{badge_class}">{score_label}</span>
                            </div>
                        </div>
                        """,
                        unsafe_allow_html=True,
                    )

                    c1, c2, c3 = st.columns([2, 2, 1])
                    with c1:
                        st.markdown(f"**Asking Price:** `${deal.price:.2f}`")
                        st.markdown(f"**Fair Market Value:** `${deal.fair_market_value:.2f}`")
                        st.markdown(f"**Est. Arbitrage Profit:** `+${deal.estimated_profit:.2f}` (`+{deal.arbitrage_margin_pct:.1f}%`)")
                        st.markdown(f"**Source:** `{deal.source.upper()}` ({deal.seller})")

                    with c2:
                        st.markdown(f"• **CPU:** {deal.specs.cpu}")
                        st.markdown(f"• **RAM:** {deal.specs.ram_gb} GB DDR")
                        st.markdown(f"• **SSD:** {deal.specs.ssd_gb} GB NVMe")
                        st.markdown(f"• **GPU:** {deal.specs.gpu}")
                        st.markdown(f"• **Screen:** {deal.specs.screen}")

                    with c3:
                        st.link_button("Open Listing ↗", deal.url, use_container_width=True)
                        if st.button("📱 Test Push", key=f"btn_push_{deal.id}", use_container_width=True):
                            res = notifier.send_deal_alert(deal, force=True)
                            if res.success:
                                st.toast(f"Pushover alert dispatched for {deal.id}!")
                            else:
                                st.error(res.message)

                    st.caption(f"**AI Valuation Summary:** {deal.summary} | *{deal.actionable_recommendation}*")
                    st.markdown("---")

    with tab_eval:
        st.subheader("Evaluate Custom Hardware Listing")
        st.write("Paste raw text from Reddit, eBay, Craigslist, or forum posts to extract structured specs and calculate real-time Deal Score.")
        
        sample_text = st.text_area(
            "Listing Title & Description",
            value="[H] Lenovo ThinkPad P16 Gen 1 (Core i9-12950HX, 64GB DDR5 ECC, 2TB SSD, RTX A4500 16GB, 4K UHD+ Screen) [W] $680 PayPal",
            height=120,
        )
        sample_price = st.number_input("Asking Price ($)", min_value=1.0, max_value=10000.0, value=680.0, step=10.0)
        sample_source = st.selectbox("Source", ["reddit", "ebay", "swappa", "manual"])

        if st.button("🚀 Evaluate with Gemini 2.5 Flash", type="primary"):
            with st.spinner("Extracting specs and calculating market arbitrage spread..."):
                raw_input = RawListing(
                    id=f"manual_{int(datetime.now().timestamp())}",
                    source=sample_source,
                    title=sample_text.split("\n")[0][:100],
                    description=sample_text,
                    price=sample_price,
                    url="#",
                )
                evaluated = evaluator.evaluate_listing(raw_input)
                
                st.success(f"Evaluation Complete! Deal Score: {evaluated.deal_score} / 10.0")
                
                e_col1, e_col2 = st.columns(2)
                with e_col1:
                    st.json({
                        "deal_score": evaluated.deal_score,
                        "fair_market_value": evaluated.fair_market_value,
                        "estimated_profit": evaluated.estimated_profit,
                        "margin_pct": evaluated.arbitrage_margin_pct,
                        "recommendation": evaluated.actionable_recommendation,
                    })
                with e_col2:
                    st.json(evaluated.specs.to_dict())

                if st.button("💾 Save Evaluated Deal to deals.json"):
                    storage.upsert_deal(evaluated)
                    st.toast("Deal saved to atomic store!")

    with tab_notify:
        st.subheader("🔔 Real-Time Mobile Push Notification Settings")
        st.info("Deals with **Deal Score >= 8.5** and **Asking Price <= $750** automatically trigger instant push alerts.")
        
        notify_col1, notify_col2 = st.columns(2)
        with notify_col1:
            st.markdown("#### ✈️ Telegram Bot (Free & Instant)")
            st.caption("1. Message @BotFather on Telegram $\\rightarrow$ `/newbot` to get Token.\n2. Message @userinfobot to get your numeric Chat ID.")
            tg_token = st.text_input("TELEGRAM_BOT_TOKEN", value=os.environ.get("TELEGRAM_BOT_TOKEN", ""), type="password", placeholder="123456789:ABCdefGhIJK...")
            tg_chat = st.text_input("TELEGRAM_CHAT_ID", value=os.environ.get("TELEGRAM_CHAT_ID", ""), placeholder="e.g. 987654321")
            if st.button("Send Test Telegram Alert", type="primary", use_container_width=True):
                from notifier import TelegramNotifier
                tg_notifier = TelegramNotifier(bot_token=tg_token, chat_id=tg_chat)
                test_deal = filtered_deals[0] if filtered_deals else storage.get_all()[0]
                with st.spinner("Dispatching Telegram message..."):
                    res = tg_notifier.send_deal_alert(test_deal)
                    if res.success:
                        st.success("✅ Deal alert delivered to your Telegram app!")
                    else:
                        st.error(f"Failed: {res.message}")

        with notify_col2:
            st.markdown("#### 💬 Discord Webhook (Alternative)")
            st.caption("Channel Settings $\\rightarrow$ Integrations $\\rightarrow$ New Webhook.")
            discord_url = st.text_input("DISCORD_WEBHOOK_URL", value=os.environ.get("DISCORD_WEBHOOK_URL", ""), type="password", placeholder="https://discord.com/api/webhooks/...")
            if st.button("Send Test Discord Notification Card", use_container_width=True):
                from notifier import DiscordNotifier
                d_notifier = DiscordNotifier(webhook_url=discord_url)
                test_deal = filtered_deals[0] if filtered_deals else storage.get_all()[0]
                with st.spinner("Dispatching Discord card..."):
                    res = d_notifier.send_deal_alert(test_deal)
                    if res.success:
                        st.success("✅ Embed card delivered to your Discord channel!")
                    else:
                        st.error(f"Failed: {res.message}")

    with tab_json:
        st.subheader("Atomic Storage Inspector")
        st.write(f"File path: `{storage.filepath}` ({len(all_deals)} records)")
        st.download_button(
            "⬇️ Download deals.json",
            data=json.dumps([d.to_dict() for d in all_deals], indent=2),
            file_name="deals.json",
            mime="application/json",
        )
        st.json([d.to_dict() for d in all_deals[:5]])


if __name__ == "__main__":
    main()
