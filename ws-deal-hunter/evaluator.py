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
from typing import Any, Dict, List, Optional, Union

from collector import RawListing
from storage import DealRecord, HardwareSpecs


SYSTEM_EVALUATION_PROMPT = """You are an elite enterprise workstation & PC hardware arbitrage valuation specialist.
Analyze the given listing title, description, asking price, and source.

Extract the exact hardware specifications and calculate accurate market resale values and a Deal Score (0.0 to 10.0).

Key Guidelines:
1. CPU: Identify exact family and model (e.g. "Intel Core i9-13950HX", "AMD Ryzen 9 Pro 7940HS", "Apple M2 Max").
2. RAM: Total capacity in integer GB (e.g. 16, 32, 64, 128). If unspecified, estimate typical configuration.
3. SSD: Total SSD storage capacity in integer GB (e.g. 512, 1024, 2048, 4096).
4. GPU: Specific GPU and VRAM (e.g. "NVIDIA RTX 4080 12GB Laptop", "RTX A4500 16GB", "Apple 38-Core GPU", "Integrated").
5. Screen: Display size, resolution, panel type (e.g. '16" 4K UHD+ 3840x2400 IPS', '15.6" FHD 144Hz'). If desktop, state 'Desktop (No screen)'.
6. Fair Market Value (FMV): Realistic average price for this configured machine in the refurbished / used secondary market (eBay sold comps, Swappa, Hardwareswap).
7. Deal Score (0.0 to 10.0):
   - 9.0 - 10.0: Legendary deal / 70%+ margin below market / High resell liquidity (e.g. ThinkPad P16 or RTX 4090 laptop under $750).
   - 8.5 - 8.9: Excellent deal / 40-70% profit spread / High priority mobile push alert threshold.
   - 7.0 - 8.4: Good fair price / Moderate value / Modest flip margin.
   - 5.0 - 6.9: Fair market value / Average retail.
   - < 5.0: Overpriced or obsolete specs.

You must respond ONLY with valid, parseable JSON matching this schema:
{
  "cpu": string,
  "ram_gb": integer,
  "ssd_gb": integer,
  "gpu": string,
  "screen": string,
  "condition": string,
  "fair_market_value": number,
  "deal_score": number,
  "summary": string,
  "actionable_recommendation": string,
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


class GeminiHardwareEvaluator:
    """
    AI valuation engine powered by Gemini 2.5 Flash / Gemini models.
    Supports official google-genai SDK, direct REST endpoints, and intelligent heuristic fallback.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        model_name: str = "gemini-2.5-flash",
    ) -> None:
        self.api_key = api_key or os.environ.get("GEMINI_API_KEY", "")
        self.model_name = model_name
        self.usage_tracker = GeminiUsageTracker()
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
        """
        raw = listing if isinstance(listing, RawListing) else RawListing(**listing)

        # 1. Try Gemini evaluation if valid API key is present
        if self.api_key and len(self.api_key) >= 15:
            try:
                ai_data = self._call_gemini(raw)
                if ai_data:
                    return self._build_deal_record(raw, ai_data)
            except Exception as err:
                print(f"[GeminiEvaluator] Gemini API error ({err}), falling back to heuristic engine.")

        # 2. Resilient Rule-Based Heuristic Evaluator
        heuristic_data = self._heuristic_evaluate(raw)
        return self._build_deal_record(raw, heuristic_data)

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
                response = self._client.models.generateContent(
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
                print(f"[GeminiEvaluator] SDK generateContent failed: {e}")

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
        Sophisticated rule-based hardware valuation aligned with AGENT_KNOWLEDGE_BASE.
        Applies hard exclusions, deep RAM extraction, and component arbitrage scoring.
        """
        text = f"{listing.title} {listing.description}".lower()

        # ==========================================
        # 0. HARD EXCLUSION CHECKS (Score 0.0)
        # ==========================================
        # A. All Intel MacBook Pros (2016-2020) (Butterfly keys, EOL, thermal throttle)
        if any(w in text for w in ["macbook", "mac book"]) and any(w in text for w in ["intel", "i7", "i9", "touch bar", "touchbar", "2016", "2017", "2018", "2019", "2020"]) and not any(m in text for m in ["m1", "m2", "m3", "m4", "m5"]):
            return {
                "cpu": "Intel MacBook (Legacy/EOL)",
                "ram_gb": 16,
                "ssd_gb": 512,
                "gpu": "Legacy Intel/Radeon",
                "screen": "MacBook Display",
                "condition": "Hard Excluded (Intel Mac)",
                "fair_market_value": 0.0,
                "deal_score": 0.0,
                "summary": "Hard Excluded: Legacy Intel MacBook Pro (2016-2020) rejected per Knowledge Base.",
                "actionable_recommendation": "REJECT / EOL HARDWARE",
                "confidence_score": 0.99,
            }

        # B. Damaged / Parts Only
        if any(w in text for w in ["for parts", "parts only", "broken screen", "cracked screen", "no power", "bad logic board", "broken hinge"]):
            return {
                "cpu": "Damaged Hardware",
                "ram_gb": 0,
                "ssd_gb": 0,
                "gpu": "Damaged",
                "screen": "Broken/Defective",
                "condition": "Broken / For Parts",
                "fair_market_value": 0.0,
                "deal_score": 0.0,
                "summary": "Hard Excluded: Damaged / Parts-only listing.",
                "actionable_recommendation": "REJECT / DAMAGED",
                "confidence_score": 0.99,
            }

        # ==========================================
        # 1. DEEP RAM EXTRACTION (Body vs. Title)
        # ==========================================
        ram_gb = 16
        is_ddr5 = any(w in text for w in ["ddr5", "5600mhz", "5200mhz", "4800mhz", "lpddr5"])
        
        # Deep extraction: detect aftermarket 64GB upgrades in description
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

        # C. Hard Exclude <= 16GB Apple Silicon (Strict User Directive)
        is_apple_silicon = any(m in text for m in ["m1", "m2", "m3", "m4", "m5", "apple silicon"])
        if is_apple_silicon and ram_gb <= 16:
            return {
                "cpu": "Apple Silicon (<=16GB RAM)",
                "ram_gb": ram_gb,
                "ssd_gb": 512,
                "gpu": "Apple Silicon GPU",
                "screen": "Liquid Retina XDR",
                "condition": "Hard Excluded (<=16GB RAM)",
                "fair_market_value": 0.0,
                "deal_score": 0.0,
                "summary": f"Hard Excluded: Apple Silicon with only {ram_gb}GB Unified RAM is insufficient for multi-agent container workloads.",
                "actionable_recommendation": "REJECT / INSUFFICIENT RAM",
                "confidence_score": 0.99,
            }

        # D. Hard Exclude Non-Workstation / Low-Grade Budget Consumer Lines (Score 0.0)
        # 1) Dell Latitude 3000 / 5000 series and Inspiron
        if any(w in text for w in ["latitude 3", "latitude 5", "latitude 33", "latitude 34", "latitude 35", "latitude 54", "latitude 55", "inspiron", "vostro"]) and not any(w in text for w in ["precision", "xps 15", "xps 17"]):
            return {
                "cpu": "Entry Business / Consumer",
                "ram_gb": ram_gb,
                "ssd_gb": 256,
                "gpu": "Integrated Intel Iris/UHD",
                "screen": "Budget Non-Workstation Display",
                "condition": "Hard Excluded (Non-Workstation / Budget Latitude/Inspiron)",
                "fair_market_value": 0.0,
                "deal_score": 0.0,
                "summary": "Hard Excluded: Dell Latitude 3000/5000 and Inspiron series lack discrete workstation GPU / H-series thermal envelope.",
                "actionable_recommendation": "REJECT / NON-WORKSTATION TIER",
                "confidence_score": 0.99,
            }

        # 2) Lenovo Consumer Laptops (IdeaPad, Yoga, ThinkBook, Flex, Chromebook)
        if any(w in text for w in ["ideapad", "yoga", "thinkbook", "flex 5", "chromebook"]) and not any(w in text for w in ["thinkpad p", "p1 gen", "p16", "p15", "p14s", "x1 extreme"]):
            return {
                "cpu": "Consumer 2-in-1 / Low Voltage",
                "ram_gb": ram_gb,
                "ssd_gb": 256,
                "gpu": "Integrated Graphics",
                "screen": "Consumer Display",
                "condition": "Hard Excluded (Consumer Yoga/IdeaPad)",
                "fair_market_value": 0.0,
                "deal_score": 0.0,
                "summary": "Hard Excluded: Lenovo IdeaPad/Yoga consumer devices lack ISV workstation certification and thermal capacity.",
                "actionable_recommendation": "REJECT / CONSUMER LINE",
                "confidence_score": 0.99,
            }

        # 3) HP Consumer Laptops (Pavilion, Envy, OmniBook, Stream, Victus)
        if any(w in text for w in ["pavilion", "envy", "omnibook", "stream 14", "victus"]) and not any(w in text for w in ["zbook", "elitebook"]):
            return {
                "cpu": "Consumer Laptop",
                "ram_gb": ram_gb,
                "ssd_gb": 256,
                "gpu": "Consumer Graphics",
                "screen": "Consumer Display",
                "condition": "Hard Excluded (Consumer HP)",
                "fair_market_value": 0.0,
                "deal_score": 0.0,
                "summary": "Hard Excluded: HP Pavilion/Envy/OmniBook consumer lines rejected per Workstation Deal Hunter mandate.",
                "actionable_recommendation": "REJECT / CONSUMER LINE",
                "confidence_score": 0.99,
            }

        # 4) Low-Voltage 15W U-Series CPUs without Workstation GPU or >=32GB RAM
        if any(w in text for w in ["-1335u", "-1345u", "-1355u", "-1235u", "-1245u", "-1255u", "-1135g7", "-1165g7"]) and ram_gb < 32 and not any(w in text for w in ["precision", "zbook", "thinkpad p"]):
            return {
                "cpu": "Low-Voltage U-Series (15W)",
                "ram_gb": ram_gb,
                "ssd_gb": 256,
                "gpu": "Integrated Intel Iris/UHD",
                "screen": "Standard Display",
                "condition": "Hard Excluded (15W U-Series CPU)",
                "fair_market_value": 0.0,
                "deal_score": 0.0,
                "summary": "Hard Excluded: 15W U-Series ultra-low voltage processor is insufficient for workstation arbitrage and heavy multitasking.",
                "actionable_recommendation": "REJECT / LOW-VOLTAGE CPU",
                "confidence_score": 0.99,
            }
        # 5) Hard Exclude Gaming Laptops (MSI, ROG, Legion, Alienware)
        if any(w in text for w in ["msi", "gaming laptop", "alienware", "rog strix", "zephyrus", "lenovo legion", "hp omen", "acer predator", "razer blade"]) and not any(w in text for w in ["precision", "zbook", "thinkpad p"]):
            return {
                "cpu": "Gaming CPU",
                "ram_gb": ram_gb,
                "ssd_gb": 512,
                "gpu": "Gaming GPU",
                "screen": "High Refresh Rate",
                "condition": "Hard Excluded (Gaming Laptop)",
                "fair_market_value": 0.0,
                "deal_score": 0.0,
                "summary": "Hard Excluded: Gaming laptops are rejected per Workstation Deal Hunter mandate.",
                "actionable_recommendation": "REJECT / GAMING LAPTOP",
                "confidence_score": 0.99,
            }

        # 6) Hard Exclude PC Components / Combos
        if any(w in text for w in ["combo", "bundle", "mobo", "motherboard", "barebone", "cpu cooler", "case", "chassis", "cpu only"]) and not any(w in text for w in ["precision", "zbook", "thinkpad p", "macbook", "mac studio", "mac pro"]):
            return {
                "cpu": "Component/Combo",
                "ram_gb": ram_gb,
                "ssd_gb": 0,
                "gpu": "N/A",
                "screen": "N/A",
                "condition": "Hard Excluded (PC Components)",
                "fair_market_value": 0.0,
                "deal_score": 0.0,
                "summary": "Hard Excluded: Individual PC components or motherboard combos are not complete workstations.",
                "actionable_recommendation": "REJECT / PC COMPONENTS",
                "confidence_score": 0.99,
            }

        # ==========================================
        # 2. CPU & SILICON VALUATION
        # ==========================================
        cpu = "Intel Core i7 / AMD Ryzen 7"
        cpu_val = 250.0

        if "m4 max" in text or "m3 max" in text or "m2 ultra" in text:
            cpu = "Apple Silicon M-Series Max/Ultra"
            cpu_val = 850.0
        elif "m4 pro" in text or "m3 pro" in text or "m2 max" in text:
            cpu = "Apple Silicon Pro/Max"
            cpu_val = 680.0
        elif "m1 max" in text or "m2 pro" in text:
            cpu = "Apple M1 Max / M2 Pro"
            cpu_val = 550.0
        elif "m1 pro" in text or "m3" in text:
            cpu = "Apple M1 Pro / M3"
            cpu_val = 450.0
        elif "m4" in text or "m5" in text:
            cpu = "Apple M4/M5 Base"
            cpu_val = 480.0
        elif "ai max pro" in text or "ai max 390" in text or "ai max 385" in text or "hx 375" in text:
            cpu = "AMD Ryzen AI MAX PRO (Strix Halo Zen 5)"
            cpu_val = 720.0
        elif "ultra 9" in text or "ultra 7" in text or "255hx" in text or "185h" in text:
            cpu = "Intel Core Ultra 7/9 (AI Workstation)"
            cpu_val = 520.0
        elif "i9-14" in text or "14900hx" in text or "i9-13" in text or "13950hx" in text or "13900hx" in text:
            cpu = "Intel Core i9 13th/14th Gen HX"
            cpu_val = 480.0
        elif "i7-13" in text or "13850hx" in text or "13800h" in text or "13700h" in text or "13620h" in text:
            cpu = "Intel Core i7 13th Gen Workstation"
            cpu_val = 380.0
        elif "i9-12" in text or "12950hx" in text or "12900h" in text:
            cpu = "Intel Core i9 12th Gen"
            cpu_val = 360.0
        elif "i7-12" in text or "12800h" in text or "12700h" in text:
            cpu = "Intel Core i7 12th Gen"
            cpu_val = 310.0
        elif "i7-11" in text or "11850h" in text or "11800h" in text:
            cpu = "Intel Core i7 11th Gen Workstation"
            cpu_val = 220.0
        elif "ryzen 9" in text or "7940hs" in text or "7945hx" in text or "8945hs" in text:
            cpu = "AMD Ryzen 9 (Zen 4/5)"
            cpu_val = 440.0
        elif "ryzen 7" in text or "7840hs" in text or "8845hs" in text or "6850u" in text:
            cpu = "AMD Ryzen 7 Pro (Zen 3+/4)"
            cpu_val = 340.0
        elif "threadripper" in text or "xeon" in text:
            cpu = "Enterprise Xeon / Threadripper Pro"
            cpu_val = 500.0

        # RAM Valuation (Tiered bonus for 64GB DDR5 / 64GB Unified)
        ram_multiplier = 4.5 if is_ddr5 or is_apple_silicon else 3.0
        ram_val = ram_gb * ram_multiplier
        if ram_gb >= 64:
            ram_val += 80.0  # Enterprise 64GB premium bonus

        # ==========================================
        # 3. SSD STORAGE VALUATION
        # ==========================================
        ssd_gb = 512
        if "8tb" in text:
            ssd_gb = 8192
        elif "4tb" in text:
            ssd_gb = 4096
        elif "2tb" in text:
            ssd_gb = 2048
        elif "1tb" in text:
            ssd_gb = 1024
        elif "512gb" in text or "512 gb" in text:
            ssd_gb = 512

        ssd_val = (ssd_gb / 512.0) * 45.0

        # ==========================================
        # 4. GPU VALUATION
        # ==========================================
        gpu = "Integrated Graphics"
        gpu_val = 0.0
        if "rtx 5090" in text or "rtx 5080" in text:
            gpu = "NVIDIA GeForce RTX 5080 / 5090 16GB"
            gpu_val = 900.0
        elif "rtx 4090" in text:
            gpu = "NVIDIA GeForce RTX 4090 16GB"
            gpu_val = 750.0
        elif "rtx 4080" in text:
            gpu = "NVIDIA GeForce RTX 4080 12GB"
            gpu_val = 550.0
        elif "rtx 5000 ada" in text or "rtx 4000 ada" in text or "rtx a5500" in text or "rtx a5000" in text:
            gpu = "NVIDIA RTX 4000/5000 Ada / A5500 16GB Pro"
            gpu_val = 700.0
        elif "rtx 3500 ada" in text or "rtx 2000 ada" in text or "rtx a4500" in text:
            gpu = "NVIDIA RTX 2000/3500 Ada / A4500 12-16GB"
            gpu_val = 480.0
        elif "rtx 4070" in text or "rtx 4060" in text or "rtx 5060" in text or "rtx 5070" in text:
            gpu = "NVIDIA GeForce RTX 4060 / 4070 / 5060 8GB"
            gpu_val = 320.0
        elif "rtx a3000" in text or "rtx a2000" in text or "rtx 3070 ti" in text:
            gpu = "NVIDIA RTX A2000 / A3000 / 3070 Ti 8GB"
            gpu_val = 280.0
        elif "radeon 8050s" in text or "radeon 8060s" in text or "radeon 890m" in text:
            gpu = "AMD Radeon 8050S / 8060S RDNA 3.5"
            gpu_val = 450.0
        elif "38-core" in text or "40-core" in text or "32-core" in text:
            gpu = "Apple Silicon High-Core GPU"
            gpu_val = 400.0

        # ==========================================
        # 5. SCREEN VALUATION
        # ==========================================
        screen = '15.6" - 16" Workstation Display'
        screen_val = 100.0
        if "liquid retina" in text or "xdr" in text:
            screen = '16.2" Liquid Retina XDR 120Hz ProMotion'
            screen_val = 240.0
        elif "oled" in text or "3.5k" in text:
            screen = '15.6"/16" 3.5K OLED 120Hz'
            screen_val = 200.0
        elif "4k" in text or "uhd" in text or "3840x" in text:
            screen = '16" 4K UHD+ (3840x2400) IPS 500nits'
            screen_val = 180.0
        elif "qhd" in text or "2560x" in text or "wqxga" in text:
            screen = '16" QHD+ (2560x1600) 165Hz'
            screen_val = 140.0
        elif "mini pc" in text or "micro" in text or "desktop" in text or "mac mini" in text or "mac studio" in text:
            screen = "Headless Mini-PC / Micro Desktop (No screen)"
            screen_val = 0.0

        # Base chassis value
        chassis_base = 220.0 if screen_val > 0 else 100.0
        fair_market_value = round(chassis_base + cpu_val + ram_val + ssd_val + gpu_val + screen_val, 2)

        # ==========================================
        # 6. ARBITRAGE & DEAL SCORING CALIBRATION
        # ==========================================
        asking = max(50.0, listing.price)
        profit = fair_market_value - asking
        margin_pct = (profit / asking) * 100.0

        # Base scoring
        score_val = 5.0 + (profit / 180.0) + (margin_pct / 45.0)

        # Target Sweet-Spot Multipliers:
        # A. Windows Workstation Sweet Spot: 64GB RAM & <= $750 (Unicorn)
        if not is_apple_silicon and ram_gb >= 64 and asking <= 750.0:
            score_val = max(score_val, 9.8)

        # B. Windows Workstation Sweet Spot: 32GB RAM & <= $650
        elif not is_apple_silicon and ram_gb >= 32 and asking <= 650.0:
            score_val = max(score_val, 9.0)

        # C. Apple Silicon Sweet Spot: 16" M1/M2/M3 Max + 64GB RAM & <= $1,200 (Halo Pricing Error)
        elif is_apple_silicon and ram_gb >= 64 and asking <= 1200.0:
            score_val = max(score_val, 9.9)

        # D. Apple Silicon Sweet Spot: 16" M1/M2/M3 Pro + 32GB RAM & <= $1,050
        elif is_apple_silicon and ram_gb >= 32 and asking <= 1050.0:
            score_val = max(score_val, 9.3)

        # E. Mini-PC Sweet Spot: >=32GB RAM & <= $450
        elif screen_val == 0.0 and ram_gb >= 32 and asking <= 450.0:
            score_val = max(score_val, 9.2)

        # F. High-Ticket Halo Arbitrage: Profit >= $600
        if profit >= 600.0:
            score_val = max(score_val, 9.4)

        # Penalize if overpriced
        if profit < 0:
            score_val = max(1.0, 5.0 + (profit / 100.0))

        deal_score = round(max(0.5, min(9.9, score_val)), 1)

        recommendation = "FAIR VALUE"
        if deal_score >= 9.5:
            recommendation = "🦄 UNICORN / HALO ARBITRAGE BUY"
        elif deal_score >= 8.8:
            recommendation = "🔥 STRONG ARBITRAGE BUY"
        elif deal_score >= 7.5:
            recommendation = "GOOD VALUE FOR DEVELOPER WORKSTATION"
        elif deal_score < 5.0:
            recommendation = "AVOID / OVERPRICED"

        return {
            "cpu": cpu,
            "ram_gb": ram_gb,
            "ssd_gb": ssd_gb,
            "gpu": gpu,
            "screen": screen,
            "condition": listing.condition_raw,
            "fair_market_value": fair_market_value,
            "deal_score": deal_score,
            "summary": f"{cpu} with {ram_gb}GB RAM, {ssd_gb}GB NVMe SSD, and {gpu}.",
            "actionable_recommendation": recommendation,
            "confidence_score": 0.92,
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
            summary=str(eval_dict.get("summary", "")),
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
