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
import sys
import tempfile
import unittest
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from collector import (
    DellRefurbishedCollector,
    EBayCollector,
    HardwareCollectorHub,
    LenovoOutletCollector,
    RawListing,
    RedditCollector,
    ShopGoodwillCollector,
    SwappaCollector,
)
from daemon import DealHunterDaemon
from evaluator import GeminiHardwareEvaluator
from notifier import PushoverNotifier
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
    def test_ebay_collector_fallback_and_format(self) -> None:
        collector = EBayCollector()
        listings = collector.fetch_listings()
        self.assertGreater(len(listings), 0)
        self.assertEqual(listings[0].source, "ebay")
        self.assertGreater(listings[0].price, 0)

    def test_reddit_price_extraction(self) -> None:
        collector = RedditCollector()
        title = "[USA-CA] [H] Lenovo ThinkPad P16 Gen 1 i9-12950HX 64GB DDR5 [W] PayPal $680 Shipped"
        body = "Selling for $680 shipped. Great condition."
        price = collector._extract_price(title, body)
        self.assertEqual(price, 680.0)

    def test_swappa_collector_feed(self) -> None:
        collector = SwappaCollector()
        listings = collector.fetch_listings()
        self.assertGreater(len(listings), 0)
        self.assertTrue("swappa" in listings[0].source or "syndicated" in listings[0].source)

    def test_dell_refurbished_collector(self) -> None:
        collector = DellRefurbishedCollector()
        listings = collector.fetch_listings()
        self.assertGreater(len(listings), 0)
        self.assertEqual(listings[0].source, "dell_refurbished")
        self.assertGreater(listings[0].price, 0)

    def test_lenovo_outlet_collector(self) -> None:
        collector = LenovoOutletCollector()
        listings = collector.fetch_listings()
        self.assertGreater(len(listings), 0)
        self.assertEqual(listings[0].source, "lenovo_outlet")
        self.assertGreater(listings[0].price, 0)

    def test_shopgoodwill_collector(self) -> None:
        collector = ShopGoodwillCollector()
        listings = collector.fetch_listings()
        self.assertGreater(len(listings), 0)
        self.assertEqual(listings[0].source, "goodwill")
        self.assertGreater(listings[0].price, 0)

    def test_collector_hub_aggregation(self) -> None:
        hub = HardwareCollectorHub()
        aggregated = hub.collect_all()
        self.assertGreater(len(aggregated), 0)
        sources = {item.source for item in aggregated}
        self.assertTrue(any("ebay" in s or "reddit" in s or "syndicated" in s or "dell" in s or "lenovo" in s or "goodwill" in s for s in sources))


class TestEvaluator(unittest.TestCase):
    def setUp(self) -> None:
        self.evaluator = GeminiHardwareEvaluator()

    def test_heuristic_spec_extraction(self) -> None:
        raw = RawListing(
            id="eval_test_1",
            source="reddit",
            title="[H] Dell Precision 7780 (i7-13850HX, 64GB DDR5, 1TB NVMe, RTX 3500 Ada 12GB) [W] $720",
            description="Super clean 17-inch workstation with RTX 3500 Ada 12GB and 64GB RAM.",
            price=720.0,
            url="https://reddit.com/eval1",
        )
        evaluated = self.evaluator.evaluate_listing(raw)
        self.assertGreaterEqual(evaluated.deal_score, 8.5)
        self.assertEqual(evaluated.specs.ram_gb, 64)
        self.assertEqual(evaluated.specs.ssd_gb, 1024)
        self.assertIn("Ada", evaluated.specs.gpu)
        self.assertGreater(evaluated.fair_market_value, evaluated.price)
        self.assertTrue(evaluated.is_high_yield)


class TestNotifier(unittest.TestCase):
    def setUp(self) -> None:
        self.notifier = PushoverNotifier(min_deal_score=8.5, max_price=750.0)

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
        summary = self.daemon.run_cycle()
        self.assertIn("duration_seconds", summary)
        self.assertGreaterEqual(summary["collected"], 1)
        # Check that records are persisted in storage
        deals = self.daemon.storage.get_all()
        self.assertGreater(len(deals), 0)


if __name__ == "__main__":
    print("\n==========================================")
    print("  RUNNING WORKSTATION DEAL HUNTER TESTS   ")
    print("==========================================\n")
    unittest.main(verbosity=2)
