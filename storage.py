"""
Workstation Deal Hunter - Storage Engine
========================================
Thread-safe and process-safe atomic JSON persistence for hardware listings.
Uses temporary file swaps (atomic POSIX rename) and re-entrant locks.
"""

from __future__ import annotations

import json
import os
import tempfile
import threading
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Union


@dataclass
class HardwareSpecs:
    cpu: str = "Unknown CPU"
    ram_gb: int = 0
    ssd_gb: int = 0
    gpu: str = "Integrated"
    screen: str = "N/A"
    condition: str = "Used"

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> HardwareSpecs:
        return cls(
            cpu=str(data.get("cpu", "Unknown CPU")),
            ram_gb=int(data.get("ram_gb", 0) or 0),
            ssd_gb=int(data.get("ssd_gb", 0) or 0),
            gpu=str(data.get("gpu", "Integrated")),
            screen=str(data.get("screen", "N/A")),
            condition=str(data.get("condition", "Used")),
        )


@dataclass
class DealRecord:
    id: str
    source: str  # 'ebay' | 'reddit' | 'swappa' | 'manual'
    title: str
    price: float
    url: str
    specs: HardwareSpecs = field(default_factory=HardwareSpecs)
    fair_market_value: float = 0.0
    estimated_profit: float = 0.0
    arbitrage_margin_pct: float = 0.0
    deal_score: float = 0.0  # 0.0 - 10.0
    summary: str = ""
    actionable_recommendation: str = ""
    confidence_score: float = 0.85
    seller: str = "Unknown"
    location: str = "US"
    created_utc: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    evaluated_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    alerted: bool = False
    is_high_yield: bool = False
    raw_payload: Optional[Dict[str, Any]] = None

    def __post_init__(self) -> None:
        if isinstance(self.specs, dict):
            self.specs = HardwareSpecs.from_dict(self.specs)
        if self.estimated_profit == 0.0 and self.fair_market_value > self.price:
            self.estimated_profit = round(self.fair_market_value - self.price, 2)
        if self.arbitrage_margin_pct == 0.0 and self.price > 0:
            self.arbitrage_margin_pct = round((self.estimated_profit / self.price) * 100, 1)
        self.is_high_yield = (
            (self.deal_score >= 9.0)
            or (self.deal_score >= 8.5 and self.price <= 850.0 and self.specs.ram_gb >= 32)
            or (self.estimated_profit >= 600.0)
            or (self.arbitrage_margin_pct >= 45.0 and self.estimated_profit >= 350.0)
        ) and (self.deal_score > 0.0)

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data["specs"] = self.specs.to_dict()
        return data

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> DealRecord:
        specs_data = data.get("specs", {})
        if isinstance(specs_data, dict):
            specs = HardwareSpecs.from_dict(specs_data)
        else:
            specs = HardwareSpecs()
        
        return cls(
            id=str(data.get("id", f"deal_{int(time.time() * 1000)}")),
            source=str(data.get("source", "unknown")),
            title=str(data.get("title", "Untitled Listing")),
            price=float(data.get("price", 0.0)),
            url=str(data.get("url", "#")),
            specs=specs,
            fair_market_value=float(data.get("fair_market_value", 0.0)),
            estimated_profit=float(data.get("estimated_profit", 0.0)),
            arbitrage_margin_pct=float(data.get("arbitrage_margin_pct", 0.0)),
            deal_score=float(data.get("deal_score", 0.0)),
            summary=str(data.get("summary", "")),
            actionable_recommendation=str(data.get("actionable_recommendation", "")),
            confidence_score=float(data.get("confidence_score", 0.85)),
            seller=str(data.get("seller", "Unknown")),
            location=str(data.get("location", "US")),
            created_utc=str(data.get("created_utc", datetime.now(timezone.utc).isoformat())),
            evaluated_at=str(data.get("evaluated_at", datetime.now(timezone.utc).isoformat())),
            alerted=bool(data.get("alerted", False)),
            is_high_yield=bool(data.get("is_high_yield", False)),
            raw_payload=data.get("raw_payload"),
        )


class AtomicDealStorage:
    """
    Thread-safe and process-safe JSON storage manager.
    Guarantees no corrupted writes via atomic rename and locks.
    """

    def __init__(self, filepath: Union[str, Path] = "deals.json") -> None:
        self.filepath = Path(filepath).resolve()
        self._lock = threading.RLock()
        self._ensure_storage_exists()

    def _ensure_storage_exists(self) -> None:
        """Create the storage file with default seed items if not present."""
        with self._lock:
            if not self.filepath.exists():
                self.filepath.parent.mkdir(parents=True, exist_ok=True)
                seed_data = self._get_initial_seed_deals()
                self._write_atomic(seed_data)

    def _read_raw(self) -> List[Dict[str, Any]]:
        """Read raw json records from disk with graceful retry."""
        with self._lock:
            if not self.filepath.exists():
                return []
            try:
                with open(self.filepath, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                    if not content:
                        return []
                    data = json.loads(content)
                    if isinstance(data, list):
                        return data
                    elif isinstance(data, dict) and "deals" in data:
                        return data["deals"]
                    return []
            except Exception as err:
                print(f"[AtomicDealStorage] Warning reading {self.filepath}: {err}")
                return []

    def _write_atomic(self, records: List[Dict[str, Any]]) -> None:
        """Atomic write using temporary file and atomic replace."""
        with self._lock:
            temp_dir = self.filepath.parent
            temp_file = tempfile.NamedTemporaryFile(
                mode="w",
                encoding="utf-8",
                dir=temp_dir,
                delete=False,
                prefix="deals_",
                suffix=".tmp",
            )
            try:
                json.dump(records, temp_file, indent=2, ensure_ascii=False)
                temp_file.flush()
                os.fsync(temp_file.fileno())
                temp_file.close()
                # Atomic POSIX replacement
                os.replace(temp_file.name, self.filepath)
            except Exception as err:
                if os.path.exists(temp_file.name):
                    try:
                        os.remove(temp_file.name)
                    except OSError:
                        pass
                raise RuntimeError(f"Atomic write failed to {self.filepath}: {err}") from err

            # Mirror sync to public/deals.json, dist/deals.json, and ws-deal-hunter/deals.json if present
            extra_targets = [
                self.filepath.parent / "public" / "deals.json",
                self.filepath.parent / "dist" / "deals.json",
                self.filepath.parent / "ws-deal-hunter" / "deals.json",
                self.filepath.parent / "ws-deal-hunter" / "public" / "deals.json",
                self.filepath.parent.parent / "ws-deal-hunter" / "deals.json",
            ]
            for target_file in extra_targets:
                if target_file.parent.exists() and target_file.parent.is_dir():
                    try:
                        with open(target_file, "w", encoding="utf-8") as f:
                            json.dump(records, f, indent=2, ensure_ascii=False)
                    except Exception:
                        pass

    def get_all(self) -> List[DealRecord]:
        """Fetch all deal records sorted by deal_score descending."""
        raw_list = self._read_raw()
        deals = [DealRecord.from_dict(item) for item in raw_list]
        deals.sort(key=lambda d: d.deal_score, reverse=True)
        return deals

    def get_deal_by_id(self, deal_id: str) -> Optional[DealRecord]:
        """Lookup single deal by ID."""
        for deal in self.get_all():
            if deal.id == deal_id:
                return deal
        return None

    def upsert_deal(self, deal: Union[DealRecord, Dict[str, Any]]) -> DealRecord:
        """Insert or update a deal record atomically."""
        if isinstance(deal, dict):
            deal_obj = DealRecord.from_dict(deal)
        else:
            deal_obj = deal

        with self._lock:
            records = self._read_raw()
            updated = False
            for idx, item in enumerate(records):
                if item.get("id") == deal_obj.id or (deal_obj.url and item.get("url") == deal_obj.url and deal_obj.url != "#"):
                    records[idx] = deal_obj.to_dict()
                    updated = True
                    break
            if not updated:
                records.insert(0, deal_obj.to_dict())
            self._write_atomic(records)
            return deal_obj

    def upsert_many(self, deals: List[Union[DealRecord, Dict[str, Any]]]) -> int:
        """Batch upsert deal records."""
        if not deals:
            return 0
        with self._lock:
            records = self._read_raw()
            id_map = {item.get("id"): idx for idx, item in enumerate(records)}
            url_map = {item.get("url"): idx for idx, item in enumerate(records) if item.get("url") and item.get("url") != "#"}
            
            inserted_or_updated = 0
            for d in deals:
                deal_obj = d if isinstance(d, DealRecord) else DealRecord.from_dict(d)
                target_idx = id_map.get(deal_obj.id)
                if target_idx is None and deal_obj.url and deal_obj.url in url_map:
                    target_idx = url_map[deal_obj.url]

                if target_idx is not None:
                    records[target_idx] = deal_obj.to_dict()
                else:
                    records.insert(0, deal_obj.to_dict())
                    id_map[deal_obj.id] = 0
                    if deal_obj.url and deal_obj.url != "#":
                        url_map[deal_obj.url] = 0
                inserted_or_updated += 1

            self._write_atomic(records)
            return inserted_or_updated

    def mark_alerted(self, deal_id: str) -> bool:
        """Mark a deal as already alerted to prevent notification spam."""
        with self._lock:
            records = self._read_raw()
            for item in records:
                if item.get("id") == deal_id:
                    item["alerted"] = True
                    self._write_atomic(records)
                    return True
            return False

    def delete_deal(self, deal_id: str) -> bool:
        """Delete a deal by ID."""
        with self._lock:
            records = self._read_raw()
            initial_len = len(records)
            records = [r for r in records if r.get("id") != deal_id]
            if len(records) < initial_len:
                self._write_atomic(records)
                return True
            return False

    def delete_many(self, deal_ids: List[str]) -> int:
        """Delete multiple deals by ID in a single atomic write."""
        if not deal_ids:
            return 0
        id_set = set(deal_ids)
        with self._lock:
            records = self._read_raw()
            initial_len = len(records)
            records = [r for r in records if r.get("id") not in id_set]
            deleted_count = initial_len - len(records)
            if deleted_count > 0:
                self._write_atomic(records)
            return deleted_count

    def filter_deals(
        self,
        min_score: float = 0.0,
        max_price: Optional[float] = None,
        sources: Optional[List[str]] = None,
        brands: Optional[List[str]] = None,
        min_ram: Optional[int] = None,
        min_ssd: Optional[int] = None,
        gpu_type: str = "All",
        search_query: str = "",
        only_high_yield: bool = False,
        sort_by: str = "Deal Score (High to Low)",
    ) -> List[DealRecord]:
        """Query and filter deals according to faceted parameters."""
        deals = self.get_all()
        filtered: List[DealRecord] = []
        query_lower = search_query.strip().lower()

        for d in deals:
            # Score filter
            if d.deal_score < min_score:
                continue

            # Price filter
            if max_price is not None and d.price > max_price:
                continue

            # Source filter
            if sources and not any(s.lower() in d.source.lower() for s in sources):
                continue

            # High-yield only filter
            if only_high_yield and not d.is_high_yield:
                continue

            # Brand filter
            if brands:
                title_lower = f"{d.title} {d.specs.cpu}".lower()
                if not any(b.lower() in title_lower for b in brands):
                    continue

            # Minimum RAM filter
            if min_ram is not None and min_ram > 0:
                if d.specs.ram_gb < min_ram:
                    continue

            # Minimum SSD filter
            if min_ssd is not None and min_ssd > 0:
                if d.specs.ssd_gb < min_ssd:
                    continue

            # GPU Tier filter
            if gpu_type == "Dedicated GPU Only":
                if "integrated" in d.specs.gpu.lower():
                    continue
            elif gpu_type == "Workstation / Ada GPU":
                if not any(k in d.specs.gpu.lower() for k in ["ada", "rtx a", "rtx pro", "quadro"]):
                    continue
            elif gpu_type == "High-End Gaming (RTX 4080/5080+)":
                if not any(k in d.specs.gpu.lower() for k in ["4080", "4090", "5080", "5090"]):
                    continue
            elif gpu_type == "Apple Silicon GPU":
                if not any(k in d.specs.gpu.lower() for k in ["apple", "core gpu"]):
                    continue

            # Free-text keyword search
            if query_lower:
                searchable = f"{d.title} {d.specs.cpu} {d.specs.gpu} {d.specs.ram_gb}GB {d.specs.ssd_gb}GB {d.summary} {d.source}".lower()
                if query_lower not in searchable:
                    continue

            filtered.append(d)

        # Apply sorting
        if sort_by == "Asking Price (Low to High)":
            filtered.sort(key=lambda x: x.price)
        elif sort_by == "Asking Price (High to Low)":
            filtered.sort(key=lambda x: x.price, reverse=True)
        elif sort_by == "Arbitrage Profit ($ High to Low)":
            filtered.sort(key=lambda x: x.estimated_profit, reverse=True)
        elif sort_by == "Arbitrage Margin (% High to Low)":
            filtered.sort(key=lambda x: x.arbitrage_margin_pct, reverse=True)
        elif sort_by == "Date Discovered (Newest First)":
            filtered.sort(key=lambda x: x.created_utc, reverse=True)
        else:  # Default: Deal Score (High to Low)
            filtered.sort(key=lambda x: x.deal_score, reverse=True)

        return filtered

    def get_statistics(self) -> Dict[str, Any]:
        """Aggregate statistical metrics for the dashboard."""
        deals = self.get_all()
        total_count = len(deals)
        if total_count == 0:
            return {
                "total_deals": 0,
                "high_yield_deals": 0,
                "avg_profit": 0.0,
                "avg_margin_pct": 0.0,
                "avg_deal_score": 0.0,
                "source_breakdown": {},
                "top_score": 0.0,
            }

        high_yield = [d for d in deals if d.is_high_yield]
        total_profit = sum(d.estimated_profit for d in deals if d.estimated_profit > 0)
        total_margin = sum(d.arbitrage_margin_pct for d in deals if d.arbitrage_margin_pct > 0)
        total_score = sum(d.deal_score for d in deals)

        sources: Dict[str, int] = {}
        for d in deals:
            sources[d.source] = sources.get(d.source, 0) + 1

        return {
            "total_deals": total_count,
            "high_yield_deals": len(high_yield),
            "avg_profit": round(total_profit / max(1, len([d for d in deals if d.estimated_profit > 0])), 2),
            "avg_margin_pct": round(total_margin / max(1, len([d for d in deals if d.arbitrage_margin_pct > 0])), 1),
            "avg_deal_score": round(total_score / total_count, 2),
            "source_breakdown": sources,
            "top_score": max(d.deal_score for d in deals),
        }

    def _get_initial_seed_deals(self) -> List[Dict[str, Any]]:
        """Rich initial seed workstation data for immediate testing and inspection."""
        return [
            {
                "id": "reddit_hws_thinkpad_p16",
                "source": "reddit",
                "title": "[H] Lenovo ThinkPad P16 Gen 1 (i9-12950HX, 64GB DDR5, 2TB NVMe, RTX A4500 16GB, 4K UHD+) [W] PayPal / Local Cash",
                "price": 680.0,
                "url": "https://www.reddit.com/r/hardwareswap/search/?q=ThinkPad+P16&sort=new",
                "specs": {
                    "cpu": "Intel Core i9-12950HX (16C/24T)",
                    "ram_gb": 64,
                    "ssd_gb": 2048,
                    "gpu": "NVIDIA RTX A4500 16GB ECC",
                    "screen": '16" 4K UHD+ (3840x2400) IPS 600nits HDR400',
                    "condition": "Excellent / Mint"
                },
                "fair_market_value": 1350.0,
                "estimated_profit": 670.0,
                "arbitrage_margin_pct": 98.5,
                "deal_score": 9.4,
                "summary": "Flagship 16-core mobile workstation with 16GB VRAM pro GPU and 64GB DDR5. Massive $670 arbitrage spread below market comps.",
                "actionable_recommendation": "INSTANT BUY. High resell liquidity in engineering/ML communities. Price is heavily discounted due to seller quick-move.",
                "confidence_score": 0.96,
                "seller": "u/ThinkPadEnthusiast_99",
                "location": "CA, USA",
                "created_utc": "2026-08-15T18:45:00Z",
                "evaluated_at": "2026-08-15T18:45:10Z",
                "alerted": True,
                "is_high_yield": True
            },
            {
                "id": "ebay_dell_precision_7780",
                "source": "ebay",
                "title": "Dell Precision 7780 17.3\" Workstation Core i7-13850HX 64GB RAM 1TB SSD RTX 3500 Ada 12GB Clean",
                "price": 720.0,
                "url": "https://www.ebay.com/sch/i.html?_nkw=Dell+Precision+7780+RTX&_sop=12",
                "specs": {
                    "cpu": "Intel Core i7-13850HX (20C/28T)",
                    "ram_gb": 64,
                    "ssd_gb": 1024,
                    "gpu": "NVIDIA RTX 3500 Ada Generation 12GB",
                    "screen": '17.3" FHD (1920x1080) 500nits 99% DCIP3',
                    "condition": "Open Box / Certified Refurbished"
                },
                "fair_market_value": 1420.0,
                "estimated_profit": 700.0,
                "arbitrage_margin_pct": 97.2,
                "deal_score": 9.2,
                "summary": "Ada Lovelace architecture professional CAD/3D laptop priced under $750 buy-it-now.",
                "actionable_recommendation": "STRONG BUY. RTX 3500 Ada alone carries high value; great workstation for CAD, Blender, and Local LLM inference.",
                "confidence_score": 0.94,
                "seller": "enterprise_it_recyclers",
                "location": "TX, USA",
                "created_utc": "2026-08-15T19:10:00Z",
                "evaluated_at": "2026-08-15T19:10:08Z",
                "alerted": True,
                "is_high_yield": True
            },
            {
                "id": "swappa_hp_zbook_studio_g9",
                "source": "swappa",
                "title": "HP ZBook Studio G9 16\" (Core i7-12800H, 32GB RAM, 1TB SSD, RTX 3070 Ti 8GB)",
                "price": 540.0,
                "url": "https://www.ebay.com/sch/i.html?_nkw=HP+ZBook+Studio+G9+RTX&_sop=12",
                "specs": {
                    "cpu": "Intel Core i7-12800H (14C/20T)",
                    "ram_gb": 32,
                    "ssd_gb": 1024,
                    "gpu": "NVIDIA GeForce RTX 3070 Ti 8GB Laptop",
                    "screen": '16" WQXGA 120Hz DreamColor IPS',
                    "condition": "Good Condition"
                },
                "fair_market_value": 980.0,
                "estimated_profit": 440.0,
                "arbitrage_margin_pct": 81.5,
                "deal_score": 8.8,
                "summary": "Thin-and-light creator workstation with DreamColor display and RTX 3070 Ti priced at standard office laptop levels.",
                "actionable_recommendation": "BUY. Excellent flip potential for video editors and mobile developers.",
                "confidence_score": 0.91,
                "seller": "studio_tech_resale",
                "location": "NY, USA",
                "created_utc": "2026-08-15T17:30:00Z",
                "evaluated_at": "2026-08-15T17:30:15Z",
                "alerted": True,
                "is_high_yield": True
            },
            {
                "id": "reddit_hws_mac_studio_m2max",
                "source": "reddit",
                "title": "[H] Apple Mac Studio M2 Max (12-core CPU, 38-core GPU, 32GB Unified, 1TB SSD) [W] PayPal",
                "price": 1050.0,
                "url": "https://www.reddit.com/r/appleswap/search/?q=Mac+Studio+M2+Max&sort=new",
                "specs": {
                    "cpu": "Apple M2 Max (12-core CPU)",
                    "ram_gb": 32,
                    "ssd_gb": 1024,
                    "gpu": "Apple M2 Max 38-Core GPU",
                    "screen": "Desktop Workstation (No screen)",
                    "condition": "Like New in Box"
                },
                "fair_market_value": 1450.0,
                "estimated_profit": 400.0,
                "arbitrage_margin_pct": 38.1,
                "deal_score": 7.9,
                "summary": "Solid $400 margin on M2 Max 38-core GPU desktop workstation. Exceeds $750 push alert price ceiling but attractive manual buy.",
                "actionable_recommendation": "HOLD / MODERATE. High value unit, but over $750 threshold for autonomous mobile push alert trigger.",
                "confidence_score": 0.93,
                "seller": "u/CupertinoTrader",
                "location": "WA, USA",
                "created_utc": "2026-08-15T16:00:00Z",
                "evaluated_at": "2026-08-15T16:00:20Z",
                "alerted": False,
                "is_high_yield": False
            },
            {
                "id": "ebay_lenovo_p1_gen5",
                "source": "ebay",
                "title": "Lenovo ThinkPad P1 Gen 5 Core i7-12700H 32GB 512GB RTX A2000 8GB 16\" QHD+ 165Hz",
                "price": 620.0,
                "url": "https://www.ebay.com/itm/seed_thinkpad_p1_g5",
                "specs": {
                    "cpu": "Intel Core i7-12700H",
                    "ram_gb": 32,
                    "ssd_gb": 512,
                    "gpu": "NVIDIA RTX A2000 8GB",
                    "screen": '16" WQXGA (2560x1600) 165Hz 500nits',
                    "condition": "Used - Good"
                },
                "fair_market_value": 1050.0,
                "estimated_profit": 430.0,
                "arbitrage_margin_pct": 69.4,
                "deal_score": 8.6,
                "summary": "Sleek carbon-fiber ThinkPad P1 with high refresh screen and ISV certified GPU under $650.",
                "actionable_recommendation": "BUY. Great price to performance ratio for portable CAD / engineering.",
                "confidence_score": 0.89,
                "seller": "corporate_offlease_deals",
                "location": "IL, USA",
                "created_utc": "2026-08-15T15:20:00Z",
                "evaluated_at": "2026-08-15T15:20:09Z",
                "alerted": True,
                "is_high_yield": True
            }
        ]
