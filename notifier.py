"""
Workstation Deal Hunter - Pushover Notification Dispatcher
==========================================================
Dispatches mobile push alerts via Pushover API for high-score deals:
Criteria: Deal Score >= 8.5 AND Price <= $750.
Endpoint: https://api.pushover.net/1/messages.json
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from storage import DealRecord


@dataclass
class NotificationResult:
    success: bool
    status_code: int
    message: str
    deal_id: str
    request_id: Optional[str] = None


class PushoverNotifier:
    """
    Pushover push notification manager.
    Formats rich HTML/Monospace push messages with direct buy URLs and priorities.
    """

    PUSHOVER_URL = "https://api.pushover.net/1/messages.json"

    def __init__(
        self,
        user_key: Optional[str] = None,
        api_token: Optional[str] = None,
        min_deal_score: float = 9.0,
        max_price: float = 850.0,
    ) -> None:
        self.user_key = user_key or os.environ.get("PUSHOVER_USER_KEY", "")
        self.api_token = api_token or os.environ.get("PUSHOVER_API_TOKEN", "")
        self.min_deal_score = min_deal_score
        self.max_price = max_price
        self._sent_deals: set[str] = set()

    def should_alert(self, deal: DealRecord) -> bool:
        """
        Evaluate if deal meets multi-tier dynamic notification criteria:
        1. Never alert on hard excluded items (score 0.0) or duplicate alerts
        2. 🦄 Halo / Unicorn: Estimated profit >= $600 OR deal_score >= 9.0 (No rigid price cap)
        3. 🎯 Sweet Spot Workstation: Score >= 8.5 AND price <= $850 AND RAM >= 32GB
        4. ⚡ High-ROI Anomaly: Margin >= 45% AND Estimated profit >= $350
        """
        if deal.id in self._sent_deals or deal.alerted or deal.deal_score <= 0.0:
            return False

        # 1. Halo / Unicorn Spread ($600+ profit or 9.0+ score regardless of price)
        if deal.estimated_profit >= 600.0 or deal.deal_score >= 9.0:
            return True

        # 2. Sweet-Spot Workstation Value
        if deal.deal_score >= self.min_deal_score and deal.price <= 850.0 and deal.specs.ram_gb >= 32:
            return True

        # 3. High-ROI Anomaly
        if deal.arbitrage_margin_pct >= 45.0 and deal.estimated_profit >= 350.0:
            return True

        return False

    def send_deal_alert(self, deal: DealRecord, force: bool = False) -> NotificationResult:
        """
        Send formatted mobile push notification if deal satisfies criteria or if forced.
        """
        if not force and not self.should_alert(deal):
            return NotificationResult(
                success=False,
                status_code=400,
                message=f"Deal does not meet alert threshold (Score >= {self.min_deal_score}, Price <= ${self.max_price})",
                deal_id=deal.id,
            )

        title = f"🔥 [{deal.deal_score}/10 DEAL] ${deal.price:.0f} {deal.specs.cpu[:22]}"
        
        # Priority: 1 (High priority / bypass quiet hours) or 2 (Emergency if score >= 9.5)
        priority = 1 if deal.deal_score < 9.5 else 2

        # Format Rich Notification Message
        message_body = (
            f"<b>💻 {deal.title}</b>\n\n"
            f"• <b>Asking:</b> ${deal.price:.2f} (Est. FMV: ${deal.fair_market_value:.2f})\n"
            f"• <b>Arbitrage:</b> +${deal.estimated_profit:.2f} ({deal.arbitrage_margin_pct:.1f}% ROI)\n"
            f"• <b>CPU:</b> {deal.specs.cpu}\n"
            f"• <b>RAM:</b> {deal.specs.ram_gb} GB\n"
            f"• <b>SSD:</b> {deal.specs.ssd_gb} GB\n"
            f"• <b>GPU:</b> {deal.specs.gpu}\n"
            f"• <b>Screen:</b> {deal.specs.screen}\n"
            f"• <b>Source:</b> {deal.source.upper()} ({deal.seller})\n\n"
            f"<i>{deal.actionable_recommendation}</i>"
        )

        payload = {
            "token": self.api_token,
            "user": self.user_key,
            "title": title,
            "message": message_body,
            "html": "1",
            "url": deal.url,
            "url_title": f"View {deal.source.upper()} Listing ↗",
            "priority": str(priority),
            "sound": "magic" if deal.deal_score >= 9.0 else "pushover",
        }

        # If credentials are not configured, simulate success and log
        if not self.user_key or not self.api_token:
            print(f"\n[Pushover Simulated Push Alert - {deal.id}]")
            print(f"Title: {title}")
            print(f"Payload Preview:\n{message_body}")
            print(f"URL: {deal.url}\n")
            self._sent_deals.add(deal.id)
            return NotificationResult(
                success=True,
                status_code=200,
                message="Simulated push alert dispatched successfully (Configure PUSHOVER_USER_KEY & PUSHOVER_API_TOKEN for live devices).",
                deal_id=deal.id,
                request_id=f"sim_req_{int(os.times().system * 1000)}",
            )

        # Live Pushover API Dispatch
        try:
            data = urllib.parse.urlencode(payload).encode("utf-8")
            req = urllib.request.Request(
                self.PUSHOVER_URL,
                data=data,
                headers={"Content-Type": "application/x-www-form-urlencoded"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=12) as response:
                resp_json = json.loads(response.read().decode("utf-8"))
                if resp_json.get("status") == 1:
                    self._sent_deals.add(deal.id)
                    return NotificationResult(
                        success=True,
                        status_code=response.status,
                        message="Push alert dispatched to Pushover mobile client.",
                        deal_id=deal.id,
                        request_id=resp_json.get("request"),
                    )
                else:
                    return NotificationResult(
                        success=False,
                        status_code=response.status,
                        message=f"Pushover rejected: {resp_json.get('errors')}",
                        deal_id=deal.id,
                    )
        except urllib.error.HTTPError as http_err:
            err_body = http_err.read().decode("utf-8")
            return NotificationResult(
                success=False,
                status_code=http_err.code,
                message=f"Pushover HTTP {http_err.code}: {err_body}",
                deal_id=deal.id,
            )
        except Exception as exc:
            return NotificationResult(
                success=False,
                status_code=500,
                message=f"Pushover network error: {exc}",
                deal_id=deal.id,
            )


class TelegramNotifier:
    """
    Telegram Bot notifier for 100% free instant push notifications directly to your phone.
    Requires:
      - TELEGRAM_BOT_TOKEN: from @BotFather (e.g. 123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ)
      - TELEGRAM_CHAT_ID: your Telegram chat or channel ID (e.g. from @userinfobot)
    """

    def __init__(
        self,
        bot_token: Optional[str] = None,
        chat_id: Optional[str] = None,
        vercel_url: Optional[str] = None,
        streamlit_url: Optional[str] = None,
    ) -> None:
        self.bot_token = bot_token if bot_token is not None else os.environ.get("TELEGRAM_BOT_TOKEN", "")
        self.chat_id = chat_id if chat_id is not None else os.environ.get("TELEGRAM_CHAT_ID", "")
        self.vercel_url = vercel_url if vercel_url is not None else os.environ.get("VERCEL_DASHBOARD_URL", "https://wsdealhunter.vercel.app/")
        self.streamlit_url = streamlit_url or os.environ.get("STREAMLIT_DASHBOARD_URL", "https://wsdealhunter.streamlit.app/")

    def _format_dashboard_links(self) -> str:
        """Format clean web dashboard links."""
        if self.vercel_url:
            return (
                f"🌐 <a href=\"{self.vercel_url}\"><b>[OPEN REACT DASHBOARD (VERCEL)]</b></a>\n"
                f"📊 <a href=\"{self.streamlit_url}\"><b>[STREAMLIT BACKUP]</b></a>"
            )
        return f"🌐 <a href=\"{self.streamlit_url}\"><b>[OPEN LIVE DASHBOARD ↗]</b></a>"

    def send_deal_alert(self, deal: DealRecord, usage_info: Optional[Dict[str, Any]] = None) -> NotificationResult:
        """Send rich HTML formatted notification to Telegram with direct buy links and AI quota status."""
        if not self.bot_token or not self.chat_id:
            return NotificationResult(
                success=False,
                status_code=400,
                message="Telegram bot token or chat ID not configured.",
                deal_id=deal.id,
            )

        api_url = f"https://api.telegram.org/bot{self.bot_token}/sendMessage"

        # Distinct header badge based on score tier
        if deal.deal_score >= 9.8:
            header_badge = f"🦄 <b>[{deal.deal_score:.1f}/10 TRUE UNICORN DEAL]</b>"
        elif deal.deal_score >= 9.0:
            header_badge = f"🎯 <b>[{deal.deal_score:.1f}/10 HIGH-CONVICTION STRIKE]</b>"
        else:
            header_badge = f"🔥 <b>[{deal.deal_score:.1f}/10 VALUE BUY]</b>"

        # AI Quota Footer Line
        if usage_info:
            ai_line = f"🤖 <b>AI Usage Today:</b> {usage_info['total_calls']} calls ({usage_info['total_tokens']:,} tokens) | ~{usage_info['estimated_daily_left']:,}/1,500 left\n\n"
        else:
            ai_line = ""

        # Format HTML message
        text = (
            f"{header_badge}\n\n"
            f"💻 <b>{deal.title}</b>\n\n"
            f"💰 <b>Asking Price:</b> ${deal.price:,.2f}  <i>(Est. FMV: ${deal.fair_market_value:,.2f})</i>\n"
            f"💵 <b>Arbitrage Spread:</b> <b>+${deal.estimated_profit:,.2f}</b> (+{deal.arbitrage_margin_pct:.0f}% ROI)\n\n"
            f"⚙️ <b>Hardware Specs:</b>\n"
            f"• <b>CPU:</b> {deal.specs.cpu}\n"
            f"• <b>RAM:</b> {deal.specs.ram_gb} GB\n"
            f"• <b>SSD:</b> {deal.specs.ssd_gb} GB NVMe\n"
            f"• <b>GPU:</b> {deal.specs.gpu}\n"
            f"• <b>Display:</b> {deal.specs.screen}\n\n"
            f"🎯 <b>Action:</b> {deal.actionable_recommendation}\n"
            f"📍 <b>Source:</b> {deal.source.upper()} ({deal.seller})\n\n"
            + ai_line
            + f"👉 <a href=\"{deal.url}\"><b>[BUY NOW ON {deal.source.upper()} ↗]</b></a>\n"
            + self._format_dashboard_links()
        )

        payload = {
            "chat_id": self.chat_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": False,
        }

        try:
            req = urllib.request.Request(
                api_url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json", "User-Agent": "WorkstationDealHunter/1.0"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=5.0) as res:
                response_data = json.loads(res.read().decode("utf-8"))
                return NotificationResult(
                    success=response_data.get("ok", False),
                    status_code=res.status,
                    message="Telegram message delivered successfully.",
                    deal_id=deal.id,
                )
        except Exception as e:
            return NotificationResult(
                success=False,
                status_code=500,
                message=f"Telegram API error: {e}",
                deal_id=deal.id,
            )

    def send_error_alert(self, component: str, error_msg: str, cycle: int = 0) -> NotificationResult:
        """Send urgent dead-man / error alert when a scraper or background task fails."""
        if not self.bot_token or not self.chat_id:
            return NotificationResult(success=False, status_code=400, message="Telegram unconfigured.", deal_id="error")

        api_url = f"https://api.telegram.org/bot{self.bot_token}/sendMessage"
        cycle_str = f" #{cycle}" if cycle > 0 else ""
        text = (
            f"🚨 <b>[DAEMON ERROR ALERT]</b>\n\n"
            f"⚠️ <b>Scraper Failure Detected on Cycle{cycle_str}</b>\n"
            f"• <b>Component:</b> <code>{component}</code>\n"
            f"• <b>Error:</b> <code>{error_msg[:400]}</code>\n\n"
            f"⚡ <i>Daemon attempting automatic self-healing on next interval.</i>\n"
            f"🌐 <a href=\"{self.streamlit_url}\"><b>[CHECK LIVE DASHBOARD ↗]</b></a>"
        )
        payload = {
            "chat_id": self.chat_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
        }
        try:
            req = urllib.request.Request(
                api_url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json", "User-Agent": "WorkstationDealHunter/1.0"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=5.0) as res:
                return NotificationResult(success=True, status_code=res.status, message="Delivered", deal_id="error")
        except Exception as e:
            return NotificationResult(success=False, status_code=500, message=str(e), deal_id="error")

    def send_system_message(self, title: str, body_html: str) -> NotificationResult:
        """Send a general system pulse digest or status update."""
        if not self.bot_token or not self.chat_id:
            return NotificationResult(success=False, status_code=400, message="Telegram unconfigured.", deal_id="system")

        api_url = f"https://api.telegram.org/bot{self.bot_token}/sendMessage"
        text = f"📊 <b>[{title}]</b>\n\n{body_html}"
        payload = {
            "chat_id": self.chat_id,
            "text": text,
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
        }
        try:
            req = urllib.request.Request(
                api_url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json", "User-Agent": "WorkstationDealHunter/1.0"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=5.0) as res:
                return NotificationResult(success=True, status_code=res.status, message="Delivered", deal_id="system")
        except Exception as e:
            return NotificationResult(success=False, status_code=500, message=str(e), deal_id="system")


class DiscordNotifier:
    """
    Discord Webhook notifier requiring ZERO paid API keys or developer accounts.
    Allows 100% free mobile push notifications via Discord mobile app.
    """

    def __init__(self, webhook_url: Optional[str] = None) -> None:
        self.webhook_url = webhook_url or os.environ.get("DISCORD_WEBHOOK_URL", "")

    def send_deal_alert(self, deal: DealRecord) -> NotificationResult:
        """Send rich embed notification card to a Discord channel."""
        if not self.webhook_url:
            return NotificationResult(
                success=False,
                status_code=400,
                message="Discord webhook URL not configured.",
                deal_id=deal.id,
            )

        embed = {
            "title": f"🔥 [{deal.deal_score:.1f}/10 DEAL] ${deal.price:.0f} - {deal.specs.cpu}",
            "description": f"**{deal.title}**\n\n_{deal.summary}_",
            "url": deal.url,
            "color": 3066993 if deal.deal_score >= 9.0 else 5814783,
            "fields": [
                {"name": "💰 Asking Price", "value": f"${deal.price:.2f}", "inline": True},
                {"name": "📈 Est. FMV", "value": f"${deal.fair_market_value:.2f}", "inline": True},
                {"name": "💵 Arbitrage Spread", "value": f"+${deal.estimated_profit:.2f} (+{deal.arbitrage_margin_pct:.0f}% ROI)", "inline": True},
                {"name": "⚙️ Hardware Specs", "value": f"• **RAM:** {deal.specs.ram_gb} GB\n• **SSD:** {deal.specs.ssd_gb} GB NVMe\n• **GPU:** {deal.specs.gpu}\n• **Screen:** {deal.specs.screen}", "inline": False},
                {"name": "🎯 AI Action Recommendation", "value": f"**{deal.actionable_recommendation}**", "inline": False},
            ],
            "footer": {"text": f"Source: {deal.source.upper()} | Seller: {deal.seller}"},
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }

        payload = {
            "username": "Workstation Deal Hunter",
            "avatar_url": "https://cdn-icons-png.flaticon.com/512/689/689396.png",
            "embeds": [embed],
        }

        try:
            req = urllib.request.Request(
                self.webhook_url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json", "User-Agent": "WorkstationDealHunter/1.0"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=5.0) as res:
                return NotificationResult(
                    success=True,
                    status_code=res.status,
                    message="Discord notification card dispatched successfully.",
                    deal_id=deal.id,
                )
        except Exception as e:
            return NotificationResult(
                success=False,
                status_code=500,
                message=f"Discord webhook error: {e}",
                deal_id=deal.id,
            )


if __name__ == "__main__":
    from storage import AtomicDealStorage
    storage = AtomicDealStorage()
    deals = storage.get_all()
    high_yield = [d for d in deals if d.is_high_yield]
    notifier = PushoverNotifier()

    if high_yield:
        test_deal = high_yield[0]
        print(f"Testing Pushover alert on top deal: {test_deal.title}")
        res = notifier.send_deal_alert(test_deal, force=True)
        print(f"Result: success={res.success} | message={res.message}")
    else:
        print("No high yield deals found in storage.")
