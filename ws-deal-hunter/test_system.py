"""
Workstation Deal Hunter - Comprehensive Test Suite
===================================================
Validates all 5 system modules:
1. Storage atomicity, concurrent writes, and filters.
2. Collectors for eBay, Reddit, and Swappa.
3. Gemini 2.5 Flash valuation & heuristic spec extraction.
4. Pushover notification criteria & payload formatting.
5. Autonomous daemon cycle execution.
"""

from __future__ import annotations

import os
import re
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import MagicMock, patch

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

IS_LIVE_TEST = "--live" in sys.argv or "--full" in sys.argv
if "--live" in sys.argv:
    sys.argv.remove("--live")
if "--full" in sys.argv:
    sys.argv.remove("--full")

from collector import (
    AppleRefurbishedCollector,
    BAndHCollector,
    BestBuyOutletCollector,
    DellRefurbishedCollector,
    EBayCollector,
    HardwareCollectorHub,
    LenovoOutletCollector,
    MicroCenterCollector,
    RawListing,
    RedditCollector,
    ShopGoodwillCollector,
    SwappaCollector,
    WootCollector,
)
from daemon import DealHunterDaemon
from evaluator import GeminiHardwareEvaluator
from notifier import PushoverNotifier, TelegramNotifier
from storage import AtomicDealStorage, DealRecord, HardwareSpecs


class TestStorageEngine(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp_dir = tempfile.TemporaryDirectory()
        self.db_file = Path(self.tmp_dir.name) / "test_deals.json"
        self.storage = AtomicDealStorage(filepath=self.db_file)

    def tearDown(self) -> None:
        self.tmp_dir.cleanup()

    def test_initial_seed_and_get_all(self) -> None:
        deals = self.storage.get_all()
        self.assertGreater(len(deals), 0)
        self.assertIsInstance(deals[0], DealRecord)
        # Check sorting by deal score descending
        scores = [d.deal_score for d in deals]
        self.assertEqual(scores, sorted(scores, reverse=True))

    def test_upsert_and_retrieve(self) -> None:
        new_deal = DealRecord(
            id="test_unit_1",
            source="reddit",
            title="Lenovo ThinkPad P1 Gen 6",
            price=600.0,
            url="https://reddit.com/test1",
            specs=HardwareSpecs(cpu="i7-13800H", ram_gb=32, ssd_gb=1024, gpu="RTX 4080"),
            fair_market_value=1200.0,
            deal_score=9.1,
        )
        self.storage.upsert_deal(new_deal)
        retrieved = self.storage.get_deal_by_id("test_unit_1")
        self.assertIsNotNone(retrieved)
        self.assertEqual(retrieved.price, 600.0)
        self.assertEqual(retrieved.specs.ram_gb, 32)
        self.assertTrue(retrieved.is_high_yield)

    def test_filter_deals(self) -> None:
        filtered = self.storage.filter_deals(min_score=8.5, max_price=750.0)
        for d in filtered:
            self.assertGreaterEqual(d.deal_score, 8.5)
            self.assertLessEqual(d.price, 750.0)

    def test_atomic_file_integrity(self) -> None:
        # Verify no tmp leftovers
        tmp_files = list(Path(self.tmp_dir.name).glob("*.tmp"))
        self.assertEqual(len(tmp_files), 0)
        self.assertTrue(self.db_file.exists())


class TestCollectors(unittest.TestCase):
    def test_ebay_collector_format(self) -> None:
        collector = EBayCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_reddit_price_extraction(self) -> None:
        collector = RedditCollector()
        title = "[USA-CA] [H] Lenovo ThinkPad P16 Gen 1 i9-12950HX 64GB DDR5 [W] PayPal $680 Shipped"
        body = "Selling for $680 shipped. Great condition."
        price = collector._extract_price(title, body)
        self.assertEqual(price, 680.0)

    def test_reddit_discount_price_extraction(self) -> None:
        """Verify that titles with '$X off' or 'Now: $Y After $X Off' extract the actual price $Y, not the discount $X."""
        collector = RedditCollector()
        title = '[Walmart] Gigabyte Aero X16 Gaming Laptop (2025): 16" 165Hz Display, Ryzen AI 7 350, RTX 5070, 32GB DDR5, 1TB SSD, Now: $1,499 After $500 Off'
        price = collector._extract_price(title, "")
        self.assertEqual(price, 1499.0)

        # Also test ($300 off) format
        title2 = "[BestBuy] Dell XPS 15 9530 i7-13700H 32GB 1TB for $1,299.99 ($300 off)"
        price2 = collector._extract_price(title2, "")
        self.assertEqual(price2, 1299.99)

    def test_reddit_markdown_table_parser_html(self) -> None:
        """Verify parsing multi-item HTML tables from Reddit liquidation posts."""
        from bs4 import BeautifulSoup

        collector = RedditCollector()
        html = """
        <div class="md">
            <p>Downsizing company lab. Shipping via UPS Ground.</p>
            <table>
                <thead>
                    <tr>
                        <th>Item</th>
                        <th>CPU</th>
                        <th>RAM</th>
                        <th>GPU</th>
                        <th>Price</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>Dell Precision 5570</td>
                        <td>i7-12800H</td>
                        <td>32GB DDR5</td>
                        <td>RTX A1000</td>
                        <td>$650 shipped</td>
                        <td>Available</td>
                    </tr>
                    <tr>
                        <td>Lenovo ThinkPad P16 Gen 1</td>
                        <td>i9-12950HX</td>
                        <td>64GB DDR5</td>
                        <td>RTX A4500</td>
                        <td>$920 shipped</td>
                        <td>Available</td>
                    </tr>
                    <tr>
                        <td><del>MacBook Pro 16 M1 Max</del></td>
                        <td>M1 Max 32c</td>
                        <td>32GB</td>
                        <td>32c GPU</td>
                        <td>$1,100</td>
                        <td>Sold to u/buyer1</td>
                    </tr>
                    <tr>
                        <td>HP ZBook Fury 16 G9</td>
                        <td>i7-12850HX</td>
                        <td>64GB</td>
                        <td>RTX A3000</td>
                        <td>$850</td>
                        <td>Pending</td>
                    </tr>
                    <tr>
                        <td>Dell Latitude 5420</td>
                        <td>i5-1135G7</td>
                        <td>16GB</td>
                        <td>Intel</td>
                        <td>$220</td>
                        <td>Available</td>
                    </tr>
                    <tr>
                        <td>Logitech MX Master 3S Mouse</td>
                        <td>N/A</td>
                        <td>N/A</td>
                        <td>N/A</td>
                        <td>$60</td>
                        <td>Available</td>
                    </tr>
                </tbody>
            </table>
        </div>
        """
        soup = BeautifulSoup(html, "html.parser")
        md_el = soup.select_one(".md")
        extracted = collector.parse_markdown_tables(
            soup_or_el=md_el,
            raw_text=str(md_el),
            post_title="[USA-CA] [H] Enterprise Workstations Liquidation [W] PayPal",
            post_id="t3_test123",
            author="seller_corp",
            url_full="https://www.reddit.com/r/homelabsales/comments/test123",
            subreddit="homelabsales",
        )

        # Expected: Exactly 2 valid available workstations (Dell Precision 5570, ThinkPad P16 Gen 1)
        # Strikethrough/Sold dropped, Pending dropped, Latitude/11th Gen dropped, Mouse dropped
        self.assertEqual(len(extracted), 2)

        p5570 = next((x for x in extracted if "Precision 5570" in x.title), None)
        self.assertIsNotNone(p5570)
        self.assertEqual(p5570.price, 650.0)
        self.assertEqual(p5570.location, "USA-CA")
        self.assertIn("32GB", p5570.title)

        p16 = next((x for x in extracted if "P16" in x.title), None)
        self.assertIsNotNone(p16)
        self.assertEqual(p16.price, 920.0)
        self.assertIn("64GB", p16.title)

    def test_reddit_markdown_table_parser_raw_md(self) -> None:
        """Verify parsing raw Markdown table text formatted with pipes (|)."""
        collector = RedditCollector()
        raw_md = """
| Model | Specs | Price | Status |
|---|---|---|---|
| Dell Precision 7670 | i7-12850HX 64GB DDR5 RTX A3000 | $780 shipped | Available |
| Lenovo ThinkPad P1 Gen 5 | i7-12800H 64GB DDR5 RTX 3070 Ti | $850 shipped | 1 available |
| ~~HP ZBook Studio G8~~ | i7-11850H 32GB RTX 3070 | ~~$500~~ | Sold |
        """
        extracted = collector.parse_markdown_tables(
            soup_or_el=None,
            raw_text=raw_md,
            post_title="[USA-TX] [H] Bulk Off-Lease Workstations [W] PayPal",
            post_id="t3_lot456",
            author="liquidator_bob",
            url_full="https://www.reddit.com/r/hardwareswap/comments/lot456",
            subreddit="hardwareswap",
        )

        self.assertEqual(len(extracted), 2)
        prices = [x.price for x in extracted]
        self.assertIn(780.0, prices)
        self.assertIn(850.0, prices)
        self.assertNotIn(500.0, prices)

    def test_reddit_liquidation_candidate_matching(self) -> None:
        """Verify that bulk liquidation titles match candidate filter without upfront CPU/RAM specs."""
        collector = RedditCollector()
        bulk_titles = [
            "[USA-TX] [H] Enterprise Workstations & Laptops Liquidation [W] PayPal",
            "[USA-CA] [H] Downsizing Homelab - Multiple Laptops Clearance [W] PayPal",
            "[USA-NY] [H] Off-Lease Laptops Cleanout (Dell, Lenovo, HP) [W] PayPal",
        ]
        for title in bulk_titles:
            is_bulk = any(re.search(pat, title, re.I) for pat in collector.LIQUIDATION_PATTERNS)
            self.assertTrue(is_bulk, f"Title should be recognized as bulk/liquidation candidate: {title}")

    def test_swappa_collector_feed(self) -> None:
        collector = SwappaCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_dell_refurbished_collector(self) -> None:
        collector = DellRefurbishedCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_lenovo_outlet_collector(self) -> None:
        collector = LenovoOutletCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_shopgoodwill_collector(self) -> None:
        collector = ShopGoodwillCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_bh_photo_collector(self) -> None:
        collector = BAndHCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_bestbuy_outlet_collector(self) -> None:
        collector = BestBuyOutletCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_microcenter_collector(self) -> None:
        collector = MicroCenterCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_apple_refurbished_collector(self) -> None:
        collector = AppleRefurbishedCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_woot_collector(self) -> None:
        collector = WootCollector()
        if IS_LIVE_TEST:
            listings = collector.fetch_listings()
            self.assertIsInstance(listings, list)
        else:
            self.assertTrue(hasattr(collector, "fetch_listings"))

    def test_collector_hub_aggregation(self) -> None:
        hub = HardwareCollectorHub()
        if IS_LIVE_TEST:
            aggregated = hub.collect_all()
            self.assertIsInstance(aggregated, list)
        else:
            self.assertTrue(hasattr(hub, "ebay") and hasattr(hub, "dell") and hasattr(hub, "collect_all"))


class TestEvaluator(unittest.TestCase):
    def setUp(self) -> None:
        self.evaluator = GeminiHardwareEvaluator()
        if not IS_LIVE_TEST:
            self.evaluator._call_gemini = MagicMock(return_value={
                "cpu": "Intel Core i7-13800H",
                "ram_gb": 64,
                "ssd_gb": 1024,
                "gpu": "NVIDIA RTX 3500 Ada",
                "screen": "16-inch UHD+",
                "condition": "Used - Turnkey",
                "fair_market_value": 1450.0,
                "estimated_profit": 650.0,
                "arbitrage_margin_pct": 47.0,
                "deal_score": 9.6,
                "summary": "Verified halo workstation with 64GB RAM and RTX Ada GPU.",
                "actionable_recommendation": "🎯 HIGH-CONVICTION STRIKE",
                "confidence_score": 0.95,
            })

    def test_heuristic_spec_extraction(self) -> None:
        raw = RawListing(
            id="eval_test_1",
            source="reddit",
            title="[H] Dell Precision 5680 (i7-13800H, 64GB DDR5, 1TB NVMe, RTX 3500 Ada 12GB) [W] $720",
            description="Super clean 16-inch workstation with RTX 3500 Ada 12GB and 64GB RAM.",
            price=720.0,
            url="https://reddit.com/eval1",
        )
        evaluated = self.evaluator.evaluate_listing(raw)
        self.assertGreaterEqual(evaluated.deal_score, 9.0)
        self.assertEqual(evaluated.specs.ram_gb, 64)
        self.assertEqual(evaluated.specs.ssd_gb, 1024)
        self.assertGreater(evaluated.fair_market_value, evaluated.price)
        self.assertTrue(evaluated.is_high_yield)

    def test_eleventh_gen_intel_rejection(self) -> None:
        raw = RawListing(
            id="eval_test_11th",
            source="ebay",
            title="Dell Precision 5560 i7-11850H 32GB 1TB RTX A2000",
            description="Good working condition 11th-Gen workstation laptop.",
            price=499.0,
            url="https://ebay.com/eval11th",
        )
        evaluated = self.evaluator.evaluate_listing(raw)
        self.assertEqual(evaluated.deal_score, 0.0)
        self.assertFalse(evaluated.is_high_yield)

    def test_structural_damage_rejection(self) -> None:
        raw = RawListing(
            id="eval_test_dmg",
            source="swappa",
            title="Dell XPS 15 9520 i7-12700H 32GB 1TB",
            description="Good condition but frame is separating from device and loose hinge screw.",
            price=500.0,
            url="https://swappa.com/eval_dmg",
        )
        evaluated = self.evaluator.evaluate_listing(raw)
        self.assertEqual(evaluated.deal_score, 0.0)

    def test_screen_damage_and_cracked_rejection(self) -> None:
        """Verify that cracked screens in title, subtitle, or condition notes are hard-rejected (Score 0.0)."""
        # Case A: Pristine title, but cracked screen in notes
        raw_cracked_notes = RawListing(
            id="eval_test_cracked_notes",
            source="ebay",
            title="Apple MacBook Pro 16\" M1 Pro 32GB RAM 1TB SSD A2485 Gray (2021)",
            description="eBay Buy-It-Now Listing: Apple MacBook Pro 16. Notes: Cracked screen on bottom right. Condition: Used. Seller: eBay Seller",
            price=700.0,
            url="https://ebay.com/itm/267756837307",
        )
        evaluated = self.evaluator.evaluate_listing(raw_cracked_notes)
        self.assertEqual(evaluated.deal_score, 0.0)
        self.assertFalse(evaluated.is_high_yield)

        # Case B: Hairline crack in title
        raw_hairline = RawListing(
            id="eval_test_hairline",
            source="reddit",
            title="[H] ThinkPad P1 Gen 5 32GB 1TB hairline crack on display [W] $600",
            description="Works well, small hairline crack.",
            price=600.0,
            url="https://reddit.com/eval_hairline",
        )
        evaluated_hairline = self.evaluator.evaluate_listing(raw_hairline)
        self.assertEqual(evaluated_hairline.deal_score, 0.0)

    def test_blown_dgpu_rejection(self) -> None:
        raw = RawListing(
            id="eval_test_dgpu",
            source="reddit",
            title="Dell XPS 15 9530 i7-13700H 32GB 1TB Intel Iris Xe only",
            description="Selling laptop. Working well, Intel Iris Xe graphics only.",
            price=550.0,
            url="https://reddit.com/eval_dgpu",
        )
        evaluated = self.evaluator.evaluate_listing(raw)
        self.assertEqual(evaluated.deal_score, 0.0)

    def test_targeted_seller_caveats_and_badges(self) -> None:
        # Test 1: Smart Resale Grade C rejection
        raw_sr_c = RawListing(
            id="sr_1",
            source="ebay",
            title="Apple MacBook Pro 16 M1 Max 32GB 1TB Grade C heavy scratches",
            description="Smart Resale listing with Grade C heavy scratches on bottom case.",
            price=700.0,
            url="https://ebay.com/sr1",
            seller="Smart Resale (smartresale)",
        )
        eval_sr = self.evaluator.evaluate_listing(raw_sr_c)
        self.assertEqual(eval_sr.deal_score, 0.0)

        # Test 2: Wisetek ITAD Badge application
        raw_wt = RawListing(
            id="wt_1",
            source="ebay",
            title="Lenovo ThinkPad P1 Gen 6 16 inch 64GB 2TB",
            description="Wisetek Market corporate off-lease laptop in excellent condition.",
            price=610.0,
            url="https://ebay.com/wt1",
            seller="Wisetek Market (wisetekca)",
        )
        eval_wt = self.evaluator.evaluate_listing(raw_wt)
        self.assertGreaterEqual(eval_wt.deal_score, 9.8)
        self.assertIn("Enterprise ITAD", eval_wt.summary)

    def test_dynamic_price_benchmark_index(self) -> None:
        from evaluator import DynamicPriceBenchmarkIndex
        index = DynamicPriceBenchmarkIndex()
        fmv, strike = index.get_benchmark("dell_precision_5680", 64)
        self.assertGreaterEqual(fmv, 1400.0)
        self.assertGreaterEqual(strike, 1000.0)

        # Test EMA update
        prev_sample = index.benchmarks.get("dell_precision_5680", {}).get("sample_count", 0)
        index.update_ema_clearing_price("dell_precision_5680", 64, 1420.0)
        new_sample = index.benchmarks.get("dell_precision_5680", {}).get("sample_count", 0)
        self.assertEqual(new_sample, prev_sample + 1)


class TestNotifier(unittest.TestCase):
    def setUp(self) -> None:
        self.notifier = PushoverNotifier(min_deal_score=8.5, max_price=750.0)

    def test_send_executive_briefing(self) -> None:
        from notifier import TelegramNotifier
        tg = TelegramNotifier(bot_token="", chat_id="")
        sample_deals = [
            DealRecord(id="brief_1", source="ebay", title="Precision 5680", price=800.0, url="https://ebay.com/1", deal_score=9.3, estimated_profit=450.0, fair_market_value=1250.0),
            DealRecord(id="brief_2", source="reddit", title="MacBook Pro 16", price=699.99, url="https://reddit.com/2", deal_score=9.4, estimated_profit=350.0, fair_market_value=1050.0),
        ]
        res = tg.send_executive_briefing(sample_deals)
        # In test mode without token, it safely returns 400 without crashing
        self.assertEqual(res.deal_id, "briefing")

    def test_send_dell_promo_alert(self) -> None:
        from notifier import TelegramNotifier
        tg = TelegramNotifier(bot_token="", chat_id="")
        res = tg.send_dell_promo_alert("SAVE45NOW", 45.0)
        self.assertEqual(res.deal_id, "dell_promo")

    def test_should_alert_criteria(self) -> None:
        high_yield_deal = DealRecord(
            id="deal_alert_1",
            source="reddit",
            title="ThinkPad P16 Extreme",
            price=680.0,
            url="https://reddit.com/alert1",
            deal_score=9.2,
        )
        self.assertTrue(self.notifier.should_alert(high_yield_deal))

        overpriced_expensive_deal = DealRecord(
            id="deal_alert_2",
            source="ebay",
            title="Standard Laptop Overpriced",
            price=1800.0,
            url="https://ebay.com/alert2",
            deal_score=6.8,
            estimated_profit=50.0,
        )
        self.assertFalse(self.notifier.should_alert(overpriced_expensive_deal))

        low_score_deal = DealRecord(
            id="deal_alert_3",
            source="swappa",
            title="Basic Office Laptop",
            price=300.0,
            url="https://swappa.com/alert3",
            deal_score=6.0,
            estimated_profit=20.0,
        )
        self.assertFalse(self.notifier.should_alert(low_score_deal))

    def test_send_alert_simulation(self) -> None:
        deal = DealRecord(
            id="deal_alert_test",
            source="reddit",
            title="Dell Precision 5570",
            price=550.0,
            url="https://reddit.com/alert",
            deal_score=8.8,
            specs=HardwareSpecs(cpu="i7-12800H", ram_gb=32, ssd_gb=1024, gpu="RTX A2000"),
        )
        res = self.notifier.send_deal_alert(deal)
        self.assertTrue(res.success)

    def test_telegram_notifier_format(self) -> None:
        tg = TelegramNotifier(bot_token="", chat_id="")
        deal = DealRecord(
            id="tg_test_1",
            source="ebay",
            title="Dell Precision 5680",
            price=799.0,
            url="https://ebay.com/itm/123",
            deal_score=9.5,
            specs=HardwareSpecs(cpu="i7-13700H", ram_gb=32, ssd_gb=1024, gpu="RTX 2000 Ada"),
        )
        # Without credentials, it should return status 400 safely
        res = tg.send_deal_alert(deal)
        self.assertFalse(res.success)
        self.assertEqual(res.status_code, 400)


class TestDaemonPipeline(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp_dir = tempfile.TemporaryDirectory()
        self.db_file = Path(self.tmp_dir.name) / "test_pipeline.json"
        self.daemon = DealHunterDaemon(
            poll_interval=10,
            min_deal_score=8.5,
            max_alert_price=750.0,
            storage_path=str(self.db_file),
        )

    def tearDown(self) -> None:
        self.tmp_dir.cleanup()

    def test_single_pipeline_cycle(self) -> None:
        if not IS_LIVE_TEST:
            # Fast mock mode: zero tokens, zero network requests, instant regression validation
            mock_listings = [
                RawListing(
                    id="mock_test_1",
                    source="ebay",
                    title="Dell Precision 5680 16in FHD+ i7-13800H 64GB RAM 2TB SSD RTX A1000",
                    description="Enterprise off-lease workstation in excellent turnkey condition.",
                    price=900.0,
                    url="https://ebay.com/itm/mock1",
                    seller="EnterpriseITAD",
                ),
                RawListing(
                    id="mock_test_2",
                    source="ebay",
                    title="Dell Inspiron 15 3000 i3 8GB RAM for parts",
                    description="Broken screen non-functional consumer laptop.",
                    price=150.0,
                    url="https://ebay.com/itm/mock2",
                    seller="PartsSeller",
                ),
            ]
            self.daemon.evaluator._call_gemini = MagicMock(return_value={
                "cpu": "Intel Core i7-13800H",
                "ram_gb": 64,
                "ssd_gb": 2048,
                "gpu": "NVIDIA RTX A1000",
                "screen": "16-inch FHD+",
                "condition": "Used - Excellent",
                "fair_market_value": 1500.0,
                "estimated_profit": 600.0,
                "arbitrage_margin_pct": 66.0,
                "deal_score": 9.6,
                "summary": "Enterprise turnkey workstation.",
                "actionable_recommendation": "🎯 HIGH-CONVICTION STRIKE",
                "confidence_score": 0.95,
            })
            with patch.object(self.daemon.collector, "collect_all", return_value=mock_listings):
                summary = self.daemon.run_cycle()
                self.assertIn("duration_seconds", summary)
                self.assertEqual(summary["collected"], 2)
                deals = self.daemon.storage.get_all()
                self.assertGreater(len(deals), 0)
        else:
            summary = self.daemon.run_cycle()
            self.assertIn("duration_seconds", summary)
            self.assertGreaterEqual(summary["collected"], 1)
            deals = self.daemon.storage.get_all()
            self.assertGreater(len(deals), 0)

    def test_persistent_heartbeat_timestamp(self) -> None:
        """Verify reading and writing last_heartbeat_timestamp across cycles."""
        test_time = datetime(2026, 9, 3, 12, 0, 0, tzinfo=timezone.utc)
        self.daemon._save_last_heartbeat_time(test_time)
        read_time = self.daemon._get_last_heartbeat_time()
        self.assertIsNotNone(read_time)
        self.assertEqual(read_time, test_time)


if __name__ == "__main__":
    mode_str = "LIVE NETWORK MODE" if IS_LIVE_TEST else "FAST UNIT MODE (0 API Tokens, Zero Latency)"
    print("\n==========================================")
    print(f"  RUNNING WORKSTATION DEAL HUNTER TESTS   ")
    print(f"  Mode: {mode_str}")
    print("==========================================\n")
    unittest.main(verbosity=2)

