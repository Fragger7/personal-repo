"""
Workstation Deal Hunter - AI Valuation Engine
=============================================
Evaluates hardware listings using Gemini 2.5 Flash with strict structured JSON schema:
- Extracts specs: CPU, RAM GB, SSD GB, GPU, Screen, Condition
- Estimates Fair Market Value (FMV) and Arbitrage Spread ($ and %)
- Computes Deal Score (0.0 - 10.0) with multi-factor weighting
- Fallback heuristic evaluator for offline/resilient execution
"""

from __future__ import annotations

import json
import os
import re
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Union

from collector import RawListing
from storage import DealRecord, HardwareSpecs


def _load_dotenv_safely() -> None:
    """Load .env file if available into os.environ."""
    try:
        from dotenv import load_dotenv
        load_dotenv()
    except Exception:
        pass
    env_path = os.path.join(os.path.dirname(__file__), ".env")
    if os.path.exists(env_path):
        try:
            with open(env_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        k = k.strip()
                        v = v.strip().strip('"').strip("'")
                        if k and k not in os.environ:
                            os.environ[k] = v
        except Exception:
            pass


_load_dotenv_safely()


SYSTEM_EVALUATION_PROMPT = """You are an elite quantitative hardware arbitrage valuation engine for high-performance developer workstations.
Analyze the listing title, description, asking price, and source according to SYSTEM DIRECTIVE v3.0:

Key Rules:
1. SILICON GATEKEEPER:
   - Whitelist ONLY: Intel 12th/13th-Gen H/HX (i7-12700H+, i7-13700H+), Intel Core Ultra 7/9, AMD Zen 4/5 (Ryzen 7 7840HS+, 8840HS+, 7940HS), Apple Silicon (M1 Pro/Max, M2 Pro/Max, M3 Pro/Max, M4 Pro/Max).
   - Hard Blacklist (Score 0.0): Intel 11th-Gen & older (i7-11850H, i9-11950H), Intel P/U-Series (1260P, 1360P, 1355U), cut-down dies (13620H, 12650H), AMD Zen 2/3 (5000/6000), Apple Base M1/M2/M3 <=16GB RAM.
2. TOTAL LANDED COST (TLC):
   - TLC = Sticker + (8.25% tax if online) + Penalties (+65 if SSD<=256GB, +40 if missing charger, +65 if dead/missing battery, +110 if 16GB upgradable chassis).
3. EMPIRICAL FMV BENCHMARKS:
   - Dell XPS 15 9530 / Precision 5680: $950 (32GB) / $1,150-$1,450 (64GB)
   - Dell XPS 15 9520 / Precision 5570: $750-$780 (32GB) / $850-$880 (64GB)
   - ThinkPad P1 Gen 6: $1,200 (32GB) / $1,400 (64GB) | ThinkPad P1 Gen 5: $800 (32GB) / $920 (64GB)
   - Apple MacBook Pro 16" M2 Pro/Max: $1,350 (32GB) / $1,550 (64GB) | M1 Pro/Max: $1,050 (32GB) / $1,250 (64GB)
4. ARBITRAGE SCORING CURVE:
   - Margin Spread % = (FMV - TLC) / FMV * 100
   - 9.8 - 10.0: TRUE UNICORN (Margin >= 38.0% + 64GB RAM + Tier 1 Chassis + Mint)
   - 9.0 - 9.7: HIGH-CONVICTION STRIKE (Margin 25.0% - 37.9% + >=32GB RAM + Turnkey)
   - 8.0 - 8.9: STRONG VALUE BUY (Margin 15.0% - 24.9%)
   - 7.0 - 7.9: OPPORTUNISTIC OFFER TARGET (Margin 8.0% - 14.9%)
   - 0.0 - 6.9: PASS / NO ARBITRAGE (Margin < 8.0% or TLC exceeds strike ceiling)

Respond ONLY with valid JSON:
{
  "cpu": string,
  "ram_gb": integer,
  "ssd_gb": integer,
  "gpu": string,
  "screen": string,
  "condition": string,
  "fair_market_value": number,
  "deal_score": number,
  "summary": string (2-3 sentences of natural language developer commentary explaining why this deal is valuable, target workload suitability like local AI/LLM inference or compilation, hardware upgrades, and specific arbitrage upside),
  "actionable_recommendation": string (e.g. "🎯 HIGH-CONVICTION STRIKE: Buy immediately at asking price", "🔥 STRONG VALUE BUY: Excellent developer daily driver", "⚡ COUNTER-OFFER TARGET: Offer $X for high margin"),
  "confidence_score": number
}
"""


@dataclass
class GeminiUsageTracker:
    total_calls: int = 0
    cycle_calls: int = 0
    total_tokens: int = 0
    daily_quota_limit: int = 1500

    def record_call(self, prompt_tokens: int = 240, candidate_tokens: int = 110) -> None:
        self.total_calls += 1
        self.cycle_calls += 1
        self.total_tokens += (prompt_tokens + candidate_tokens)

    def reset_cycle(self) -> None:
        self.cycle_calls = 0

    def get_summary(self) -> Dict[str, Any]:
        remaining = max(0, self.daily_quota_limit - self.total_calls)
        return {
            "cycle_calls": self.cycle_calls,
            "total_calls": self.total_calls,
            "total_tokens": self.total_tokens,
            "daily_quota_limit": self.daily_quota_limit,
            "estimated_daily_left": remaining,
            "quota_used_pct": round((self.total_calls / self.daily_quota_limit) * 100, 1),
        }


class DynamicPriceBenchmarkIndex:
    """
    Adaptive Self-Learning Fair Market Value (FMV) Price Index.
    Loads baseline benchmarks from price_benchmarks.json and dynamically updates
    rolling Exponential Moving Averages (EMA) with decay alpha=0.10 when verified clearing prices are observed.
    """

    def __init__(self, filepath: Optional[Union[str, Any]] = None) -> None:
        from pathlib import Path
        if filepath is None:
            base_dir = Path(__file__).resolve().parent
            possible = [base_dir / "price_benchmarks.json", Path("price_benchmarks.json")]
            self.filepath = next((p for p in possible if p.exists()), base_dir / "price_benchmarks.json")
        else:
            self.filepath = Path(filepath)

        self.alpha_decay = 0.10
        self.benchmarks: Dict[str, Dict[str, float]] = {}
        self.component_adders: Dict[str, float] = {
            "rtx_4080_4090_ada": 450.0,
            "rtx_4070_3500_ada": 250.0,
            "ssd_2tb_plus": 80.0,
            "ssd_4tb_plus": 180.0,
            "oled_4k_display": 120.0,
        }
        self.load_benchmarks()

    def load_benchmarks(self) -> None:
        """Load benchmark index from JSON file."""
        if self.filepath.exists():
            try:
                with open(self.filepath, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self.alpha_decay = data.get("alpha_decay", 0.10)
                    self.benchmarks = data.get("benchmarks", {})
                    self.component_adders = data.get("component_adders", self.component_adders)
            except Exception as err:
                print(f"[PriceBenchmarkIndex] Error reading {self.filepath}: {err}")

    def save_benchmarks(self) -> None:
        """Persist calibrated benchmark index back to JSON file."""
        try:
            from datetime import timezone
            payload = {
                "version": "1.0.0",
                "last_updated": datetime.now(timezone.utc).isoformat(),
                "alpha_decay": self.alpha_decay,
                "benchmarks": self.benchmarks,
                "component_adders": self.component_adders,
            }
            with open(self.filepath, "w", encoding="utf-8") as f:
                json.dump(payload, f, indent=2)
        except Exception as err:
            print(f"[PriceBenchmarkIndex] Error saving {self.filepath}: {err}")

    def get_benchmark(self, model_key: str, ram_gb: int) -> Tuple[float, float]:
        """Return (base_fmv, strike_ceiling) from benchmark index."""
        entry = self.benchmarks.get(model_key)
        if entry:
            if ram_gb >= 64:
                return entry.get("base_fmv_64gb", 1000.0), entry.get("strike_ceiling_64gb", 850.0)
            elif ram_gb >= 32:
                return entry.get("base_fmv_32gb", 800.0), entry.get("strike_ceiling_32gb", 700.0)
            else:
                return entry.get("base_fmv_32gb", 800.0) * 0.85, entry.get("strike_ceiling_32gb", 700.0) * 0.85
        return 0.0, 0.0

    def update_ema_clearing_price(self, model_key: str, ram_gb: int, observed_price: float) -> None:
        """Update the rolling exponential moving average for a model."""
        if model_key not in self.benchmarks or observed_price <= 100.0:
            return
        entry = self.benchmarks[model_key]
        field_fmv = "base_fmv_64gb" if ram_gb >= 64 else "base_fmv_32gb"
        field_strike = "strike_ceiling_64gb" if ram_gb >= 64 else "strike_ceiling_32gb"
        
        current_fmv = entry.get(field_fmv, observed_price)
        new_fmv = round((1.0 - self.alpha_decay) * current_fmv + self.alpha_decay * observed_price, 2)
        entry[field_fmv] = new_fmv
        entry[field_strike] = round(new_fmv * 0.82, 2)
        entry["sample_count"] = entry.get("sample_count", 0) + 1
        self.save_benchmarks()


class GeminiHardwareEvaluator:
    """
    AI valuation engine powered by Gemini 2.5 Flash / Gemini models.
    Supports official google-genai SDK, direct REST endpoints, and intelligent heuristic fallback.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        model_name: str = "gemini-3.6-flash",
        benchmark_index: Optional[DynamicPriceBenchmarkIndex] = None,
    ) -> None:
        self.api_key = api_key or os.environ.get("GEMINI_API_KEY", "")
        self.model_name = model_name
        self.usage_tracker = GeminiUsageTracker()
        self.benchmark_index = benchmark_index or DynamicPriceBenchmarkIndex()
        self._client: Any = None
        self._init_client()

    def _init_client(self) -> None:
        """Attempt to initialize google-genai SDK if available."""
        if not self.api_key:
            return
        try:
            from google import genai
            self._client = genai.Client(api_key=self.api_key)
        except Exception:
            self._client = None

    def evaluate_listing(self, listing: Union[RawListing, Dict[str, Any]]) -> DealRecord:
        """
        Evaluate listing using Gemini structured output or heuristic valuation fallback.
        Instantly drops blacklisted accessories, damaged parts, or non-workstation units (Score 0.0).
        """
        raw = listing if isinstance(listing, RawListing) else RawListing(**listing)

        # 0. Fast Blacklist Gatekeeper: Drop accessories, damaged parts, or low-tier units immediately
        from collector import is_blacklisted_item
        if is_blacklisted_item(raw.title, raw.description):
            return DealRecord(
                id=raw.id,
                source=raw.source,
                title=raw.title,
                price=raw.price,
                url=raw.url,
                specs=HardwareSpecs(
                    cpu="Hard Excluded Item",
                    ram_gb=0,
                    ssd_gb=0,
                    gpu="None",
                    screen="None",
                    condition="Excluded (Blacklisted / Non-Workstation)",
                ),
                fair_market_value=0.0,
                estimated_profit=0.0,
                arbitrage_margin_pct=0.0,
                deal_score=0.0,
                summary=f"Hard Excluded: Listing matches negative filter blacklist.",
                actionable_recommendation="DROP / EXCLUDED ITEM",
                confidence_score=0.99,
                seller=raw.seller,
                location=raw.location,
                created_utc=raw.created_utc,
                is_high_yield=False,
            )

        # 1. First-Stage: Fast Deterministic Heuristic Evaluation (0ms, $0 tokens)
        heuristic_data = self._heuristic_evaluate(raw)
        deal = self._build_deal_record(raw, heuristic_data)

        # 2. Quality Filter: If disqualified (Score 0.0) or low margin (<8.0), skip AI entirely
        if deal.deal_score < 8.0:
            return deal

        # 3. Strategy #1 AI-Escalation Gate: Only invoke Gemini for high-potential candidates (Score >= 8.0)
        if self.api_key and len(self.api_key) >= 15:
            try:
                ai_data = self._call_gemini(raw)
                if ai_data:
                    ai_deal = self._build_deal_record(raw, ai_data)
                    # Blend AI second-opinion into recommendation & summary while preserving strict hardware constraints
                    deal.summary = ai_deal.summary or deal.summary
                    deal.actionable_recommendation = ai_deal.actionable_recommendation or deal.actionable_recommendation
                    if ai_deal.deal_score > 0.0:
                        # Refine score with AI validation
                        deal.deal_score = max(deal.deal_score, ai_deal.deal_score)
                        deal.fair_market_value = ai_deal.fair_market_value or deal.fair_market_value
                        deal.estimated_profit = round(max(0.0, deal.fair_market_value - deal.price), 2)
                        deal.arbitrage_margin_pct = round((deal.estimated_profit / deal.price) * 100.0, 1) if deal.price > 0 else 0.0
            except Exception as err:
                print(f"[GeminiEvaluator] AI Escalation error ({err}), keeping heuristic baseline.")

        return deal

    def _call_gemini(self, listing: RawListing) -> Optional[Dict[str, Any]]:
        """Call Gemini model with structured JSON response schema."""
        prompt = (
            f"Listing Title: {listing.title}\n"
            f"Description: {listing.description}\n"
            f"Asking Price: ${listing.price:.2f}\n"
            f"Source: {listing.source}\n"
            f"Seller: {listing.seller}\n"
        )
        # Method A: Use google-genai SDK if initialized
        if self._client:
            try:
                response = self._client.models.generate_content(
                    model=self.model_name,
                    contents=prompt,
                    config={
                        "system_instruction": SYSTEM_EVALUATION_PROMPT,
                        "response_mime_type": "application/json",
                        "temperature": 0.2,
                    },
                )
                text = response.text if hasattr(response, "text") else str(response)
                self.usage_tracker.record_call(250, 120)
                return self._parse_json_response(text)
            except Exception as e:
                print(f"[GeminiEvaluator] SDK generate_content failed: {e}")

        # Method B: Direct REST API invocation
        return self._call_gemini_rest(prompt)

    def _call_gemini_rest(self, prompt: str) -> Optional[Dict[str, Any]]:
        """Direct REST invocation for Gemini API with usage metadata tracking."""
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.model_name}:generateContent?key={self.api_key}"
        payload = {
            "system_instruction": {
                "parts": [{"text": SYSTEM_EVALUATION_PROMPT}]
            },
            "contents": [
                {
                    "parts": [{"text": prompt}]
                }
            ],
            "generationConfig": {
                "response_mime_type": "application/json",
                "temperature": 0.2,
            },
        }

        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        for attempt in range(2):
            try:
                with urllib.request.urlopen(req, timeout=4.0) as resp:
                    res_json = json.loads(resp.read().decode("utf-8"))
                    usage = res_json.get("usageMetadata", {})
                    p_tok = int(usage.get("promptTokenCount", 240))
                    c_tok = int(usage.get("candidatesTokenCount", 110))
                    self.usage_tracker.record_call(p_tok, c_tok)

                    candidates = res_json.get("candidates", [])
                    if candidates:
                        parts = candidates[0].get("content", {}).get("parts", [])
                        if parts:
                            raw_text = parts[0].get("text", "")
                            return self._parse_json_response(raw_text)
            except urllib.error.HTTPError as http_err:
                if http_err.code == 429:
                    sleep_time = 2.0 ** (attempt + 1)
                    print(f"[GeminiEvaluator] Rate limited (429). Retrying in {sleep_time}s...")
                    time.sleep(sleep_time)
                else:
                    print(f"[GeminiEvaluator] HTTP error: {http_err.code} {http_err.reason}")
                    break
            except Exception as ex:
                print(f"[GeminiEvaluator] REST request error: {ex}")
                break

        return None

    def _parse_json_response(self, text: str) -> Optional[Dict[str, Any]]:
        """Clean and parse JSON from model output."""
        cleaned = text.strip()
        # Strip markdown codeblocks ```json ... ```
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.MULTILINE)
        cleaned = re.sub(r"\s*```$", "", cleaned, flags=re.MULTILINE)
        try:
            return json.loads(cleaned)
        except json.JSONDecodeError:
            # Fallback regex search for { ... }
            match = re.search(r"(\{.*\})", cleaned, re.DOTALL)
            if match:
                try:
                    return json.loads(match.group(1))
                except Exception:
                    pass
        return None

    def _heuristic_evaluate(self, listing: RawListing) -> Dict[str, Any]:
        """
        Sophisticated rule-based hardware valuation aligned with AGENT_KNOWLEDGE_BASE (v3.0).
        Applies hard exclusions, Total Landed Cost (TLC) penalties, empirical FMV ground truth,
        and calibrated 4-tier arbitrage scoring.
        """
        text = f"{listing.title} {listing.description}".lower()

        # ==========================================
        # 0. HARD EXCLUSIONS & BLACKLIST (Score 0.0)
        # ==========================================

        # A. All Intel MacBook Pros (2016-2020) (Butterfly keys, EOL, thermal throttle)
        if any(w in text for w in ["macbook", "mac book"]) and any(w in text for w in ["intel", "i7", "i9", "touch bar", "touchbar", "2016", "2017", "2018", "2019", "2020"]) and not any(m in text for m in ["m1", "m2", "m3", "m4", "m5"]):
            return self._reject_dict("Hard Excluded: Legacy Intel MacBook Pro (2016-2020) rejected per Knowledge Base.")

        # B. Damaged / Defective / Structural Defects (Hinges, Separating Frame, Cracked Screen/Glass/Palmrest)
        structural_damage_keywords = [
            "for parts", "parts only", "broken screen", "cracked screen", "crack on screen", "crack in screen",
                       "screen damage", "cracked screen", "cracked display", "cracked glass", "crack on screen", "crack in screen", "hairline crack",
            "screen defect", "screen issue", "screen blemish", "screen burn", "burn in", "dead pixel", "dead pixels", "staingate", "delamination", "backlight bleed", "lines on screen", "line on display",
            "needs repair", "needs fix", "needs fixing", "for repair", "read desc", "read description", "as-is", "as is", "untested", "parts only",
            "display issue", "damaged screen", "no power", "bad logic board",
            "broken hinge", "loose hinge", "hinge separated", "hinge screw", "frame separating", "frame is separating",
            "cracked palm rest", "cracked palmrest", "keyboard imprints", "deep screen marks", "bent corner", "dropped impact"
        ]
        if any(w in text for w in structural_damage_keywords):
            return self._reject_dict("Hard Excluded: Listing contains physical, structural, or chassis damage.")

        # B.2. No RAM / Barebones
        if any(w in text for w in ["no ram", "without ram", "barebones", "missing ram", "no memory"]):
            return self._reject_dict("Hard Excluded: Barebones / No RAM workstation.")

        # C. Blown-dGPU Failure Trap (Workstations listed with "Intel Iris Xe only" or dead dGPU)
        if any(w in text for w in ["iris xe only", "intel graphics only", "uhd graphics only", "dgpu not working", "gpu disabled", "gpu code 43", "no dedicated gpu"]):
            return self._reject_dict("Hard Excluded: Workstation dGPU failure / disabled discrete graphics.")

        # D. Intel 11th-Gen & Older Silicon Blacklist (Tiger Lake, Ice Lake, Comet Lake, Xeons)
        # Drops i7-11850H, i9-11950H, 11800H, 11400H, Xeon W-11955M, and all 10th/9th/8th Gen
        intel_old_gen = re.search(r'\b(i[3579][\s-]11\d{3}|i[3579][\s-]11th(?:\s*gen)?|i[3579]\s*11gen|11th\s*gen|i[3579][\s-]10\d{3}|i[3579][\s-]10th(?:\s*gen)?|i[3579][\s-][89]\d{3}|11850h|11950h|11800h|11400h|11980hk|10885h|10750h|9750h|8750h|11955m|w-11\d{3}|xeon.*11\d{3})\b', text)
        if intel_old_gen:
            return self._reject_dict(f"Hard Excluded: Older Intel/Xeon CPU ({intel_old_gen.group(0)}) rejected. Minimum 12th-Gen Intel required.")

        # E. Intel Core i5 & Low-Tier Silicon Blacklist (All i5s rejected for developer workstation tier)
        intel_i5_match = re.search(r'\b(i5-\d{4,5}[a-z]*|core i5|intel i5)\b', text)
        if intel_i5_match:
            return self._reject_dict(f"Hard Excluded: Intel Core i5 processor ({intel_i5_match.group(0)}) lacks 14-core workstation baseline.")

        # F. Intel P-Series & Low-Voltage U-Series Blacklist (15W-28W limits)
        intel_low_voltage = re.search(r'\b(1260p|1360p|1370p|1240p|1250p|1270p|1340p|1350p|1355u|1335u|1235u|1245u|1255u|1135g7|1165g7)\b', text)
        if intel_low_voltage and not any(w in text for w in ["precision 7680", "thinkpad p16"]):
            return self._reject_dict(f"Hard Excluded: Low-voltage / thermal-limited CPU ({intel_low_voltage.group(0)}) rejected.")

        # G. Cut-Down Intel H-Die Blacklist (Reduced E-Cores / Cache)
        cutdown_intel = re.search(r'\b(i7-13620h|i7-12650h|i5-13500h|i5-12500h|i5-13420h|i5-12450h)\b', text)
        if cutdown_intel and not any(w in text for w in ["64gb", "2x32gb"]):
            return self._reject_dict(f"Hard Excluded: Cut-down Intel H-series die ({cutdown_intel.group(0)}) without verified 64GB RAM upgrade.")

        # H. AMD Zen 2 / Zen 3 & Legacy Rebrand Blacklist (5000, 6000, 7020, 7030, 7035)
        amd_old_gen = re.search(r'\b(ryzen [3579] 5\d{3}|ryzen [3579] 6\d{3}|ryzen [3579] 7[0-3]\d{2}|5800h|5900hx|6800h|6900hx)\b', text)
        if amd_old_gen:
            return self._reject_dict(f"Hard Excluded: Legacy AMD Zen 2/3 CPU ({amd_old_gen.group(0)}) rejected. Minimum Zen 4 (7840HS/8840HS) required.")

        # I. Consumer & Budget Lines (Latitude 3000/5000, Inspiron, IdeaPad, Yoga, Pavilion, Envy, OmniBook)
        if any(w in text for w in ["latitude 3", "latitude 5", "inspiron", "vostro", "ideapad", "yoga", "thinkbook", "flex 5", "chromebook", "pavilion", "envy", "omnibook", "stream 14", "victus"]):
            if not any(w in text for w in ["precision", "xps 15", "xps 17", "thinkpad p", "p1 gen", "p16", "x1 extreme", "zbook"]):
                return self._reject_dict("Hard Excluded: Budget consumer / entry business chassis lacks workstation thermal envelope.")

        # ==========================================
        # 1. DEEP RAM EXTRACTION
        # ==========================================
        ram_gb = 16
        is_ddr5 = any(w in text for w in ["ddr5", "5600mhz", "5200mhz", "4800mhz", "lpddr5"])
        
        if any(w in text for w in ["128gb", "128 gb"]):
            ram_gb = 128
        elif any(w in text for w in ["96gb", "96 gb"]):
            ram_gb = 96
        elif any(w in text for w in ["64gb", "64 gb", "2x32gb", "2x 32gb", "crucial 64", "corsair 64", "upgraded to 64", "upgraded 64"]):
            ram_gb = 64
        elif any(w in text for w in ["48gb", "48 gb"]):
            ram_gb = 48
        elif any(w in text for w in ["36gb", "36 gb"]):
            ram_gb = 36
        elif any(w in text for w in ["32gb", "32 gb", "2x16gb", "2x 16gb", "upgraded to 32"]):
            ram_gb = 32
        elif any(w in text for w in ["24gb", "24 gb"]):
            ram_gb = 24
        elif any(w in text for w in ["18gb", "18 gb"]):
            ram_gb = 18
        elif any(w in text for w in ["16gb", "16 gb"]):
            ram_gb = 16
        elif any(w in text for w in ["8gb", "8 gb"]):
            ram_gb = 8

        is_apple_silicon = any(m in text for m in ["m1", "m2", "m3", "m4", "m5", "apple silicon"])

        # Check for Dual SO-DIMM Upgradable PC Workstation chassis
        is_upgradable_chassis = any(w in text for w in [
            "precision 5570", "precision 5580", "precision 5680", "precision 7670", "precision 7680", "precision 7770", "precision 7780",
            "xps 15 9520", "xps 15 9530", "xps 17 9720", "xps 17 9730",
            "thinkpad p1 gen 5", "thinkpad p1 gen 6", "thinkpad p16", "thinkpad x1 extreme g5",
            "zbook studio g9", "zbook studio g10", "zbook fury", "zbook power"
        ])

        # Universal Hard Ban: Any system <= 16GB is outruled (upgradable or not)
        if ram_gb <= 16:
            return self._reject_dict(f"Hard Excluded: {ram_gb}GB RAM is insufficient for modern AI/Developer workloads. Minimum 32GB required.")

        # ==========================================
        # 2. TOTAL LANDED COST (TLC) CALCULATION
        # ==========================================
        tax_rate = 0.0 if listing.source in ["reddit", "local"] else 0.0825
        sticker_price = max(50.0, listing.price)
        tlc = round(sticker_price * (1.0 + tax_rate), 2)

        # Mandatory Refurbishment Penalties
        ssd_gb = 512
        if "8tb" in text:
            ssd_gb = 8192
        elif "4tb" in text:
            ssd_gb = 4096
        elif "2tb" in text:
            ssd_gb = 2048
        elif "1tb" in text:
            ssd_gb = 1024
        elif "256gb" in text or "256 gb" in text or "128gb" in text:
            ssd_gb = 256
            tlc += 65.0  # Cost of 1TB Gen4 NVMe drive upgrade

        # Missing Charger Penalty
        if any(w in text for w in ["no charger", "no ac adapter", "no power supply", "charger not included", "without charger"]):
            tlc += 40.0

        # Missing / Bad Battery Penalty
        if any(w in text for w in ["no battery", "no batt", "dead battery", "service battery", "bad battery", "battery not working"]):
            tlc += 65.0

        # If a 16GB upgradable machine, calculate base value as 16GB (do not artificially inflate to 64GB)
        if ram_gb <= 16 and is_upgradable_chassis:
            tlc += 110.0
        # ==========================================
        # 3. FAIR MARKET VALUE (FMV) & DYNAMIC BENCHMARK INDEX
        # ==========================================
        model_key = None
        if "xps 15 9530" in text or "xps 9530" in text:
            model_key = "dell_xps_15_9530"
        elif "xps 15 9520" in text or "xps 9520" in text:
            model_key = "dell_xps_15_9520"
        elif "precision 5680" in text:
            model_key = "dell_precision_5680"
        elif "precision 5570" in text:
            model_key = "dell_precision_5570"
        elif "thinkpad p1 gen 6" in text or "p1 gen 6" in text or "p16 gen 2" in text:
            model_key = "thinkpad_p1_gen_6"
        elif "thinkpad p1 gen 5" in text or "p1 gen 5" in text or "x1 extreme g5" in text or "p16 gen 1" in text:
            model_key = "thinkpad_p1_gen_5"
        elif "zbook studio g10" in text or "zbook fury g10" in text:
            model_key = "hp_zbook_studio_g10"
        elif "zbook studio g9" in text or "zbook fury g9" in text or "zbook power g9" in text:
            model_key = "hp_zbook_studio_g9"
        elif is_apple_silicon and ("m3 max" in text or "m4 max" in text or "m2 max" in text):
            model_key = "apple_mbp_16_m3_m4_max"
        elif is_apple_silicon and "m2 pro" in text:
            model_key = "apple_mbp_16_m2_pro_max"
        elif is_apple_silicon and ("m1 max" in text or "m1 pro" in text):
            model_key = "apple_mbp_16_m1_pro_max"
        elif "zephyrus" in text or "legion pro" in text or "razer blade" in text:
            model_key = "asus_razer_legion_workstation"

        if model_key:
            fmv, strike_ceiling = self.benchmark_index.get_benchmark(model_key, ram_gb)
        else:
            # General Whitelisted Silicon Fallback FMV
            base_fmv = 850.0 if ram_gb >= 64 else (750.0 if ram_gb >= 32 else 600.0)
            if "rtx 4080" in text or "rtx 4090" in text or "rtx 5080" in text:
                base_fmv += 450.0
            elif "rtx 4070" in text or "rtx 3500 ada" in text or "rtx 4000 ada" in text:
                base_fmv += 250.0
            fmv = base_fmv
            strike_ceiling = fmv * 0.82

        # Add component bonuses if high-end display or storage
        if "oled" in text or "4k" in text:
            fmv += self.benchmark_index.component_adders.get("oled_4k_display", 120.0)
        if ssd_gb >= 2048:
            fmv += self.benchmark_index.component_adders.get("ssd_2tb_plus", 80.0)

        # ==========================================
        # 4. CALIBRATED ARBITRAGE SCORING CURVE
        # ==========================================
        # Margin Spread % relative to FMV
        margin_spread_pct = round(((fmv - tlc) / fmv) * 100.0, 1)
        profit = round(max(0.0, fmv - tlc), 2)

        # Disqualify if TLC exceeds Strike Ceiling or margin < 8%
        if tlc > strike_ceiling or margin_spread_pct < 8.0:
            deal_score = max(1.0, round(5.0 + (margin_spread_pct / 10.0), 1))
            recommendation = "PASS / NO ARBITRAGE (Exceeds Strike Ceiling)"
        elif margin_spread_pct >= 38.0 and ram_gb >= 64:
            # 🦄 TRUE UNICORN DEAL (Strict: 38%+ Margin, True 64GB DDR5 / Unified, Tier 1 Chassis)
            deal_score = round(min(10.0, 9.8 + ((margin_spread_pct - 38.0) / 20.0)), 1)
            recommendation = "🦄 TRUE UNICORN DEAL (High Liquidity Equity)"
        elif margin_spread_pct >= 25.0 and ram_gb >= 32:
            # 🎯 HIGH-CONVICTION STRIKE (25.0% - 37.9% Margin + >=32GB RAM)
            deal_score = round(min(9.7, 9.0 + ((margin_spread_pct - 25.0) / 18.0)), 1)
            recommendation = "🎯 HIGH-CONVICTION STRIKE"
        elif margin_spread_pct >= 15.0 and ram_gb >= 32:
            # ⚡ STRONG VALUE BUY (15.0% - 24.9% Margin)
            deal_score = round(min(8.9, 8.0 + ((margin_spread_pct - 15.0) / 10.0)), 1)
            recommendation = "⚡ STRONG VALUE BUY"
        elif margin_spread_pct >= 8.0:
            # 🤝 OPPORTUNISTIC OFFER TARGET (8.0% - 14.9% Margin)
            deal_score = round(min(7.9, 7.0 + ((margin_spread_pct - 8.0) / 7.0)), 1)
            recommendation = "🤝 OPPORTUNISTIC OFFER TARGET"
        else:
            deal_score = 5.0
            recommendation = "FAIR MARKET VALUE"

        # ==========================================
        # 5. PRECISE SPEC EXTRACTION (CPU, GPU, Screen)
        # ==========================================
        # Exact CPU extraction
        cpu_match = re.search(r'\b(i9-\d{4,5}[a-z]*|i7-\d{4,5}[a-z]*|ultra [79] \d{3}[a-z]*|ryzen [79] \d{4}[a-z]*|m[1-4] (?:max|pro|ultra))\b', text)
        if cpu_match:
            cpu_label = cpu_match.group(0).upper()
        elif is_apple_silicon:
            if "m3 max" in text or "m2 max" in text or "m1 max" in text or "m4 max" in text:
                cpu_label = "Apple M-Series Max"
            elif "m3 pro" in text or "m2 pro" in text or "m1 pro" in text or "m4 pro" in text:
                cpu_label = "Apple M-Series Pro"
            else:
                cpu_label = "Apple Silicon"
        elif "ryzen" in text:
            cpu_label = "AMD Ryzen 7/9 Zen 4/5"
        elif "ultra" in text:
            cpu_label = "Intel Core Ultra AI Workstation"
        else:
            cpu_label = "Intel Core i7/i9 12th/13th-Gen H"

        # Exact GPU extraction
        gpu_match = re.search(r'\b(rtx [2345]000 ada|rtx a[12345]000|rtx a5500|rtx 40[6789]0|rtx 50[789]0|rtx 30[78]0 ti|radeon 890m|radeon 8050s)\b', text)
        if gpu_match:
            gpu_label = f"NVIDIA {gpu_match.group(0).upper()}"
        elif is_apple_silicon:
            gpu_label = "Apple Silicon Unified GPU"
        else:
            gpu_label = "NVIDIA RTX / Workstation GPU"

        # Screen extraction
        screen_label = '15.6" - 16" Workstation Display'
        if "oled" in text or "3.5k" in text:
            screen_label = '15.6" / 16" 3.5K OLED Display'
        elif "4k" in text or "uhd" in text or "3840x" in text:
            screen_label = '16" 4K UHD+ Display'
        elif "qhd" in text or "2560x" in text or "wqxga" in text:
            screen_label = '16" QHD+ 165Hz Display'
        elif "liquid retina" in text or "xdr" in text:
            screen_label = '16.2" Liquid Retina XDR 120Hz'

        # ==========================================
        # 6. TARGETED ENTERPRISE SELLER CAVEATS & BADGES
        # ==========================================
        seller_text = f"{(listing.seller or '')} {text}".lower()
        is_enterprise_itad = any(s in seller_text for s in [
            "wisetek", "wisetekca", "epc-texas", "epc-global", "epc texas", "epc global",
            "human-i-t", "human i t", "smartresale", "smart resale", "greentek",
            "joysystems", "joy systems", "planitroi", "techdiscounts", "tech discounts",
            "blairtech", "blair technology"
        ])

        # Caveat 1: Smart Resale - Filter out Grade C / Grade D cosmetic units
        if any(s in seller_text for s in ["smartresale", "smart resale"]):
            if any(w in text for w in ["grade c", "grade d", "heavy scratch", "cracked", "dent"]):
                return self._reject_dict("Hard Excluded: Smart Resale Grade C/D unit rejected per enterprise aesthetic baseline.")

        # Caveat 2: GreenTek Solutions - Add Best Offer notation
        if "greentek" in seller_text:
            recommendation += " (💡 Note: GreenTek typically accepts Best Offers ~20% below list)"

        # Caveat 3: Human-I-T - Verify OEM charger
        if any(s in seller_text for s in ["human-i-t", "human i t"]):
            if not any(w in text for w in ["includes charger", "oem charger", "power adapter", "with charger"]):
                tlc += 40.0

        itad_badge = " 🛡️ [Enterprise ITAD Certified]" if is_enterprise_itad else ""

        # Construct natural language developer commentary
        profit_spread = round(max(0.0, fmv - tlc), 2)
        workload_context = (
            "Ideal for local AI/LLM fine-tuning, Docker virtualization, and heavy code compilation."
            if ram_gb >= 64
            else "Excellent daily driver for full-stack engineering, multi-container development, and data analysis."
        )
        if deal_score >= 9.5:
            sentiment = f"Rare halo opportunity priced at ${listing.price:.0f} with a massive +${profit_spread:.0f} (+{margin_spread_pct:.0f}% ROI) spread below fair market clearing baseline (${fmv:.0f})."
        elif deal_score >= 8.5:
            sentiment = f"High-conviction value buy at ${listing.price:.0f} offering +${profit_spread:.0f} (+{margin_spread_pct:.0f}% ROI) profit spread below estimated FMV (${fmv:.0f})."
        else:
            sentiment = f"Opportunistic target priced at ${listing.price:.0f} with +${profit_spread:.0f} margin spread."

        summary_commentary = f"{sentiment} Configured with {cpu_label}, {ram_gb}GB RAM, {ssd_gb}GB SSD, and {gpu_label}. {workload_context}{itad_badge}"

        return {
            "cpu": cpu_label,
            "ram_gb": ram_gb,
            "ssd_gb": ssd_gb,
            "gpu": gpu_label,
            "screen": screen_label,
            "condition": listing.condition_raw,
            "fair_market_value": fmv,
            "deal_score": deal_score,
            "summary": summary_commentary,
            "actionable_recommendation": recommendation,
            "confidence_score": 0.95,
        }

    def _reject_dict(self, reason: str) -> Dict[str, Any]:
        """Construct standard reject dictionary for blacklisted / disqualified listings."""
        return {
            "cpu": "Disqualified Silicon / Chassis",
            "ram_gb": 0,
            "ssd_gb": 0,
            "gpu": "None",
            "screen": "None",
            "condition": "Disqualified",
            "fair_market_value": 0.0,
            "deal_score": 0.0,
            "summary": reason,
            "actionable_recommendation": "REJECT / NO ARBITRAGE",
            "confidence_score": 0.99,
        }

    def _build_deal_record(self, raw: RawListing, eval_dict: Dict[str, Any]) -> DealRecord:
        """Construct a validated DealRecord from raw listing and evaluation dictionary."""
        specs = HardwareSpecs(
            cpu=str(eval_dict.get("cpu", "Unknown CPU")),
            ram_gb=int(eval_dict.get("ram_gb", 16) or 16),
            ssd_gb=int(eval_dict.get("ssd_gb", 512) or 512),
            gpu=str(eval_dict.get("gpu", "Integrated")),
            screen=str(eval_dict.get("screen", '15.6" Display')),
            condition=str(eval_dict.get("condition", raw.condition_raw)),
        )

        fmv = float(eval_dict.get("fair_market_value", raw.price * 1.2))
        profit = round(max(0.0, fmv - raw.price), 2)
        margin = round((profit / raw.price) * 100.0, 1) if raw.price > 0 else 0.0
        score = float(eval_dict.get("deal_score", 5.0))
        score = round(max(0.0, min(10.0, score)), 1)

        final_summary = str(eval_dict.get("summary", ""))
        seller_check = f"{(raw.seller or '')} {raw.title} {raw.description}".lower()
        if any(s in seller_check for s in ["wisetek", "wisetekca", "epc-texas", "epc-global", "human-i-t", "smartresale", "greentek", "joysystems", "planitroi", "techdiscounts", "blairtech"]):
            if "Enterprise ITAD" not in final_summary:
                final_summary += " 🛡️ [Enterprise ITAD Refurbisher]"

        return DealRecord(
            id=raw.id,
            source=raw.source,
            title=raw.title,
            price=raw.price,
            url=raw.url,
            specs=specs,
            fair_market_value=fmv,
            estimated_profit=profit,
            arbitrage_margin_pct=margin,
            deal_score=score,
            summary=final_summary,
            actionable_recommendation=str(eval_dict.get("actionable_recommendation", "")),
            confidence_score=float(eval_dict.get("confidence_score", 0.85)),
            seller=raw.seller,
            location=raw.location,
            created_utc=raw.created_utc,
            raw_payload=raw.raw_payload,
        )


if __name__ == "__main__":
    from collector import RawListing
    evaluator = GeminiHardwareEvaluator()
    sample = RawListing(
        id="test_1",
        source="reddit",
        title="[H] Lenovo ThinkPad P16 Gen 1 i9-12950HX 64GB DDR5 2TB NVMe RTX A4500 16GB [W] $650",
        description="Selling my P16 Gen 1. Core i9 16C, 64GB DDR5 ECC, RTX A4500 16GB VRAM, 4K screen. $650 shipped.",
        price=650.0,
        url="https://reddit.com/r/hardwareswap/test_1",
    )
    result = evaluator.evaluate_listing(sample)
    print("\n[Evaluation Test Output]")
    print(f"Deal Score: {result.deal_score}/10.0")
    print(f"Asking: ${result.price} | FMV: ${result.fair_market_value} | Profit: +${result.estimated_profit} ({result.arbitrage_margin_pct}%)")
    print(f"Specs: {result.specs.cpu} | {result.specs.ram_gb}GB RAM | {result.specs.ssd_gb}GB SSD | {result.specs.gpu}")
    print(f"High-Yield Alert Trigger: {result.is_high_yield}")
