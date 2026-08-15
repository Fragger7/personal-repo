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

    storage = AtomicDealStorage(filepath="deals.json")
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
    st.sidebar.header("🔍 Filter Controls")
    
    min_score = st.sidebar.slider(
        "Min Deal Score (0 - 10)",
        min_value=0.0,
        max_value=10.0,
        value=7.0,
        step=0.1,
        help="Scores >= 8.5 qualify for autonomous mobile push alerts.",
    )

    max_price = st.sidebar.slider(
        "Max Asking Price ($)",
        min_value=100,
        max_value=2500,
        value=850,
        step=25,
    )

    sources = st.sidebar.multiselect(
        "Syndicated Endpoints",
        options=["ebay", "reddit", "swappa"],
        default=["ebay", "reddit", "swappa"],
        format_func=lambda s: {
            "ebay": "eBay Browse API",
            "reddit": "Reddit r/hardwareswap",
            "swappa": "Swappa RSS",
        }.get(s, s.upper()),
    )

    search_query = st.sidebar.text_input("Search specs or model", placeholder="e.g. P16, RTX, 64GB, Ada, i9")

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
        search_query=search_query,
        only_high_yield=only_high_yield,
    )
    stats = storage.get_statistics()

    # Top KPI Metrics Banner
    col1, col2, col3, col4 = st.columns(4)
    with col1:
        st.metric("Total Analyzed Deals", f"{stats['total_deals']} units", delta=f"{stats['source_breakdown'].get('reddit', 0)} Reddit")
    with col2:
        st.metric("🔥 High-Yield (>=8.5)", f"{stats['high_yield_deals']} opportunities", delta="Pushover Ready")
    with col3:
        st.metric("Avg Arbitrage Margin", f"{stats['avg_margin_pct']}%", delta=f"+${stats['avg_profit']:.0f} Profit")
    with col4:
        st.metric("Top Deal Score", f"{stats['top_score']:.1f} / 10.0", delta="Gemini 2.5 Flash")

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
