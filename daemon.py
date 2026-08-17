"""
Workstation Deal Hunter - Autonomous Polling Daemon
===================================================
Continuous background pipeline orchestrator:
1. Gathers listings from eBay, Reddit r/hardwareswap, and Swappa.
2. Evaluates new listings via Gemini 2.5 Flash.
3. Dispatches Pushover mobile alerts for deals >= 8.5 Score & <= $750 Price.
4. Persists records atomically to deals.json.
"""

from __future__ import annotations

import argparse
import signal
import subprocess
import sys
import threading
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from collector import HardwareCollectorHub, RawListing
from evaluator import GeminiHardwareEvaluator
from notifier import DiscordNotifier, PushoverNotifier, TelegramNotifier
from storage import AtomicDealStorage, DealRecord


@dataclass
class DaemonStatus:
    is_running: bool = False
    cycle_count: int = 0
    total_collected: int = 0
    total_evaluated: int = 0
    total_alerts_sent: int = 0
    last_poll_time: Optional[str] = None
    last_error: Optional[str] = None
    recent_activity: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


class DealHunterDaemon:
    """
    Autonomous background monitoring daemon.
    """

    def __init__(
        self,
        poll_interval: int = 180,
        min_deal_score: float = 8.5,
        max_alert_price: float = 750.0,
        storage_path: str = "deals.json",
        auto_push: bool = False,
        discord_webhook: Optional[str] = None,
    ) -> None:
        self.poll_interval = poll_interval
        self.min_deal_score = min_deal_score
        self.max_alert_price = max_alert_price
        self.auto_push = auto_push
        
        self.storage = AtomicDealStorage(filepath=storage_path)
        self.collector = HardwareCollectorHub()
        self.evaluator = GeminiHardwareEvaluator()
        self.notifier = PushoverNotifier(
            min_deal_score=min_deal_score,
            max_price=max_alert_price,
        )
        self.telegram_notifier = TelegramNotifier()
        self.discord_notifier = DiscordNotifier(webhook_url=discord_webhook)

        self._running = False
        self._thread: Optional[threading.Thread] = None
        self.status = DaemonStatus()
        self._lock = threading.Lock()

    def log(self, message: str) -> None:
        """Log message with timestamp and append to recent activity queue."""
        timestamp = datetime.now(timezone.utc).strftime("%H:%M:%S")
        formatted = f"[{timestamp}] {message}"
        print(f"[Daemon] {formatted}")
        with self._lock:
            self.status.recent_activity.insert(0, formatted)
            if len(self.status.recent_activity) > 50:
                self.status.recent_activity.pop()

    def run_cycle(self) -> Dict[str, Any]:
        """Execute a single end-to-end collection, evaluation, notification cycle."""
        cycle_start = time.time()
        self.log("Starting hardware sync cycle across eBay, Reddit, and Swappa...")
        
        # 1. Collect
        try:
            raw_listings = self.collector.collect_all()
            self.status.total_collected += len(raw_listings)
            self.log(f"Syndicated {len(raw_listings)} listings from all active endpoints.")
        except Exception as e:
            err_msg = f"Collection failed: {e}"
            self.log(err_msg)
            self.status.last_error = err_msg
            return {"error": err_msg}

        # 2. Filter out already known listings
        existing_deals = self.storage.get_all()
        known_ids = {d.id for d in existing_deals}
        known_urls = {d.url for d in existing_deals if d.url and d.url != "#"}

        new_listings: List[RawListing] = []
        for raw in raw_listings:
            if raw.id not in known_ids and (not raw.url or raw.url not in known_urls):
                new_listings.append(raw)

        self.log(f"Found {len(new_listings)} new unanalyzed candidate hardware listings.")

        evaluated_deals: List[DealRecord] = []
        alerts_in_cycle = 0

        # 3. Evaluate new listings
        for raw in new_listings:
            try:
                self.log(f"Evaluating [{raw.source.upper()}] ${raw.price:.0f} - {raw.title[:60]}...")
                deal = self.evaluator.evaluate_listing(raw)
                
                # Strict Quality Gate: Reject low-grade consumer noise and hard-excluded models
                if deal.deal_score < 6.0 or "hard excluded" in deal.summary.lower() or "reject" in deal.actionable_recommendation.lower():
                    self.log(f"⏩ Filtered Out: {raw.title[:45]} (Score {deal.deal_score}/10 | {deal.actionable_recommendation})")
                    continue

                evaluated_deals.append(deal)
                self.status.total_evaluated += 1

                # 4. Check for high-yield alert trigger
                if self.notifier.should_alert(deal):
                    self.log(f"🚨 HIGH-YIELD OPPORTUNITY DETECTED: Score {deal.deal_score}/10 | Profit +${deal.estimated_profit:.2f}")
                    alert_res = self.notifier.send_deal_alert(deal)
                    if alert_res.success:
                        deal.alerted = True
                        alerts_in_cycle += 1
                        self.status.total_alerts_sent += 1
                        self.log(f"📱 Pushover alert dispatched for deal: {deal.id}")

                    # Also send to Telegram if configured
                    if self.telegram_notifier.bot_token and self.telegram_notifier.chat_id:
                        tg_res = self.telegram_notifier.send_deal_alert(deal)
                        if tg_res.success:
                            self.log(f"✈️ Telegram alert dispatched for deal: {deal.id}")

                    # Also send to Discord if configured
                    if self.discord_notifier.webhook_url:
                        discord_res = self.discord_notifier.send_deal_alert(deal)
                        if discord_res.success:
                            self.log(f"💬 Discord embed alert dispatched for deal: {deal.id}")

            except Exception as eval_err:
                self.log(f"Error evaluating listing {raw.id}: {eval_err}")

        # 5. Persist evaluated records atomically
        if evaluated_deals:
            upserted = self.storage.upsert_many(evaluated_deals)
            self.log(f"Atomically committed {upserted} evaluated deals to {self.storage.filepath.name}.")

        # 6. Always-On Telegram Pulse Digest & Heartbeat (Dispatches every cycle)
        if self.telegram_notifier.bot_token and self.telegram_notifier.chat_id:
            total_active = len(self.storage.get_all())
            usage = self.evaluator.usage_tracker.get_summary()

            if evaluated_deals:
                summary_lines = []
                for d in evaluated_deals[:5]:
                    summary_lines.append(f"• <b>${d.price:,.0f}</b> | {d.deal_score}/10 | {d.title[:45]}...")
                digest_html = (
                    f"📥 <b>Sync Cycle #{self.status.cycle_count + 1} Complete</b>\n"
                    f"✨ Discovered <b>{len(evaluated_deals)} new listings</b> (Total Active: {total_active})\n\n"
                    + "\n".join(summary_lines)
                    + f"\n\n🤖 <b>AI Usage:</b> {usage['cycle_calls']} calls ({usage['total_tokens']:,} tokens) | ~{usage['estimated_daily_left']:,}/1,500 daily requests left\n"
                    + f"\n👉 <a href=\"https://wsdealhunter.streamlit.app/\"><b>[OPEN WEB DASHBOARD]</b></a>"
                )
                self.telegram_notifier.send_system_message("Inventory Updated", digest_html)
            else:
                heartbeat_html = (
                    f"💓 <b>Sync Cycle #{self.status.cycle_count + 1} Heartbeat</b>\n"
                    f"🔍 Scanned <b>{len(raw_listings)} listings</b> across eBay, Reddit & Syndicated Feeds.\n"
                    f"📊 <b>0 new unanalyzed items</b> this hour (<b>{total_active} active deals</b> monitored in store).\n"
                    f"🤖 <b>AI Usage:</b> {usage['total_calls']} total calls ({usage['total_tokens']:,} tokens) | ~{usage['estimated_daily_left']:,}/1,500 daily free requests left\n\n"
                    f"⚡ <i>Autonomous Scraper Online & Standing By</i>\n"
                    f"👉 <a href=\"https://wsdealhunter.streamlit.app/\"><b>[OPEN WEB DASHBOARD]</b></a>"
                )
                self.telegram_notifier.send_system_message("Bot Heartbeat", heartbeat_html)

        # 7. Optional auto-push to GitHub repo
        if self.auto_push and evaluated_deals:
            self.log("Pushing updated deals.json to GitHub repository...")
            try:
                subprocess.run(
                    ["python3", "git_sync.py", "--push", f"chore(deals): sync {len(evaluated_deals)} new deals"],
                    check=False,
                    capture_output=True,
                )
            except Exception as push_err:
                self.log(f"Auto-push error: {push_err}")

        elapsed = round(time.time() - cycle_start, 2)
        self.status.cycle_count += 1
        self.status.last_poll_time = datetime.now(timezone.utc).isoformat()
        self.log(f"Cycle completed in {elapsed}s: {len(evaluated_deals)} evaluated, {alerts_in_cycle} alerts sent.")

        return {
            "cycle": self.status.cycle_count,
            "collected": len(raw_listings),
            "new_evaluated": len(evaluated_deals),
            "alerts_sent": alerts_in_cycle,
            "duration_seconds": elapsed,
        }

    def start(self) -> None:
        """Start daemon loop in background thread."""
        if self._running:
            return
        self._running = True
        self.status.is_running = True
        self._thread = threading.Thread(target=self._loop, daemon=True, name="DealHunterDaemon")
        self._thread.start()
        self.log(f"Autonomous daemon started with poll interval of {self.poll_interval}s.")

    def stop(self) -> None:
        """Stop daemon loop gracefully."""
        self._running = False
        self.status.is_running = False
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=3.0)
        self.log("Daemon stopped.")

    def _loop(self) -> None:
        """Internal continuous loop."""
        while self._running:
            try:
                self.run_cycle()
            except Exception as loop_err:
                self.log(f"Unexpected error in daemon loop: {loop_err}")
                self.status.last_error = str(loop_err)

            # Sleep in small increments for responsive stop
            sleep_ticks = max(1, self.poll_interval)
            for _ in range(sleep_ticks):
                if not self._running:
                    break
                time.sleep(1.0)


def main() -> None:
    parser = argparse.ArgumentParser(description="Workstation Deal Hunter Autonomous Monitoring Daemon")
    parser.add_argument("--interval", type=int, default=180, help="Polling interval in seconds (default: 180)")
    parser.add_argument("--min-score", type=float, default=8.5, help="Minimum Deal Score for mobile push alerts (default: 8.5)")
    parser.add_argument("--max-price", type=float, default=750.0, help="Maximum Asking Price for mobile push alerts (default: 750.0)")
    parser.add_argument("--once", action="store_true", help="Run a single evaluation cycle and exit")
    parser.add_argument("--auto-push", action="store_true", help="Auto-push deals.json to GitHub when new deals arrive")
    parser.add_argument("--discord-webhook", type=str, default="", help="Discord webhook URL for 100% free mobile push alerts")
    parser.add_argument("--storage", type=str, default="deals.json", help="Path to atomic storage JSON file")
    
    args = parser.parse_args()

    daemon = DealHunterDaemon(
        poll_interval=args.interval,
        min_deal_score=args.min_score,
        max_alert_price=args.max_price,
        storage_path=args.storage,
        auto_push=args.auto_push,
        discord_webhook=args.discord_webhook or None,
    )

    if args.once:
        print("\n=== Running Single Hardware Arbitrage Scan ===")
        res = daemon.run_cycle()
        print(f"\nScan Summary: {res}")
        stats = daemon.storage.get_statistics()
        print(f"Total Deals in Store: {stats['total_deals']} | High-Yield (>=8.5): {stats['high_yield_deals']}")
        return

    # Handle interrupt signals
    def handle_exit(signum: Any, frame: Any) -> None:
        print("\nShutting down daemon...")
        daemon.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT, handle_exit)
    signal.signal(signal.SIGTERM, handle_exit)

    daemon.start()
    print("Daemon running. Press Ctrl+C to stop.")
    try:
        while True:
            time.sleep(1.0)
    except KeyboardInterrupt:
        daemon.stop()


if __name__ == "__main__":
    main()
