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
        self._client: Any = None
        self._init_client()

    def _init_client(self) -> None:
        """Attempt to initialize google-genai SDK if available."""
        if not self.api_key:
            return
        try:
            from google import genai
            self._client = genai.Client(api_key=self.api_key)
        except ImportError:
            self._client = None

    def evaluate_listing(self, listing: Union[RawListing, Dict[str, Any]]) -> DealRecord:
        """
        Evaluate listing using Gemini structured output or heuristic valuation fallback.
        """
        raw = listing if isinstance(listing, RawListing) else RawListing(**listing)

        # 1. Try Gemini evaluation if valid API key is present
        if self.api_key and self.api_key.startswith("AIza") and len(self.api_key) >= 30:
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
        prompt = f"""Listing Data:
- Source: {listing.source}
- Title: {listing.title}
- Asking Price: ${listing.price:.2f}
- Seller / Location: {listing.seller} ({listing.location})
- Description: {listing.description[:800]}
"""

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
                return self._parse_json_response(text)
            except Exception as e:
                print(f"[GeminiEvaluator] SDK generateContent failed: {e}")

        # Method B: Direct REST API invocation
        return self._call_gemini_rest(prompt)

    def _call_gemini_rest(self, prompt: str) -> Optional[Dict[str, Any]]:
        """Direct REST invocation for Gemini API."""
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
                with urllib.request.urlopen(req, timeout=3.0) as resp:
                    res_json = json.loads(resp.read().decode("utf-8"))
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
        Sophisticated rule-based hardware valuation fallback.
        Estimates baseline market value from CPU generation, RAM, GPU tier, and display.
        """
        text = f"{listing.title} {listing.description}".lower()

        # 1. CPU extraction & baseline score
        cpu = "Intel Core i7 / AMD Ryzen 7"
        cpu_val = 250.0

        # Apple Silicon Hierarchy
        if "m4 max" in text or "m3 max" in text or "m2 ultra" in text:
            cpu = "Apple Silicon M-Series Max/Ultra"
            cpu_val = 750.0
        elif "m4 pro" in text or "m3 pro" in text or "m2 max" in text:
            cpu = "Apple Silicon Pro/Max"
            cpu_val = 580.0
        elif "m4" in text or "m5" in text:
            cpu = "Apple M4 / M5 (Latest Gen)"
            cpu_val = 520.0
        elif "m3" in text or "m2 pro" in text or "m1 max" in text:
            cpu = "Apple M3 / M2 Pro / M1 Max"
            cpu_val = 460.0
        elif "m2" in text or "m1 pro" in text:
            cpu = "Apple M2 / M1 Pro"
            cpu_val = 380.0
        elif "m1" in text:
            cpu = "Apple M1"
            cpu_val = 280.0
        # Intel Core Ultra & Extreme Gen
        elif "ultra 9" in text or "ultra 7" in text or "255hx" in text or "185h" in text:
            cpu = "Intel Core Ultra 7/9 (AI Workstation)"
            cpu_val = 500.0
        elif "ai max pro" in text or "ai max 390" in text or "ai max 385" in text or "hx 375" in text:
            cpu = "AMD Ryzen AI MAX PRO (Strix Halo 12/16-Core Zen 5)"
            cpu_val = 680.0
        elif "i9-14" in text or "14900hx" in text:
            cpu = "Intel Core i9 14th Gen HX"
            cpu_val = 480.0
        elif "i9-13" in text or "13950hx" in text or "13900hx" in text or "13980hx" in text:
            cpu = "Intel Core i9 13th Gen (HX Extreme)"
            cpu_val = 450.0
        elif "i9-12" in text or "12950hx" in text or "12900hx" in text:
            cpu = "Intel Core i9 12th Gen (HX Extreme)"
            cpu_val = 380.0
        elif "i7-14" in text or "i7-13" in text or "13850hx" in text or "13800h" in text:
            cpu = "Intel Core i7 13th/14th Gen"
            cpu_val = 350.0
        elif "i7-12" in text or "12800h" in text or "12700h" in text:
            cpu = "Intel Core i7 12th Gen"
            cpu_val = 280.0
        elif "threadripper" in text:
            cpu = "AMD Ryzen Threadripper Pro"
            cpu_val = 650.0
        elif "ryzen 9" in text or "7940hs" in text or "7945hx" in text or "8945hs" in text:
            cpu = "AMD Ryzen 9 (Zen 4/5)"
            cpu_val = 420.0
        elif "ryzen 7" in text or "7840hs" in text or "6800h" in text:
            cpu = "AMD Ryzen 7 Pro"
            cpu_val = 300.0
        elif "xeon" in text:
            cpu = "Intel Xeon Workstation"
            cpu_val = 320.0

        # 2. RAM capacity
        ram_gb = 16
        if "128gb" in text or "128 gb" in text:
            ram_gb = 128
        elif "96gb" in text or "96 gb" in text:
            ram_gb = 96
        elif "64gb" in text or "64 gb" in text:
            ram_gb = 64
        elif "48gb" in text or "48 gb" in text:
            ram_gb = 48
        elif "36gb" in text or "36 gb" in text:
            ram_gb = 36
        elif "32gb" in text or "32 gb" in text:
            ram_gb = 32
        elif "24gb" in text or "24 gb" in text:
            ram_gb = 24
        elif "18gb" in text or "18 gb" in text:
            ram_gb = 18
        elif "16gb" in text or "16 gb" in text:
            ram_gb = 16

        ram_val = ram_gb * 3.5

        # 3. SSD capacity
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

        ssd_val = (ssd_gb / 512) * 45.0

        # 4. GPU extraction & value
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
        elif "rtx 5000 ada" in text or "rtx 4000 ada" in text or "rtx pro 3000" in text:
            gpu = "NVIDIA RTX 4000/5000 Ada Generation 12-16GB"
            gpu_val = 700.0
        elif "radeon 8050s" in text or "radeon 8060s" in text or "radeon 890m" in text:
            gpu = "AMD Radeon 8050S / 8060S RDNA 3.5 (40-CU)"
            gpu_val = 550.0
        elif "rtx 3500 ada" in text or "rtx 2000 ada" in text or "rtx pro 2000" in text:
            gpu = "NVIDIA RTX 2000/3500 Ada Generation"
            gpu_val = 480.0
        elif "rtx 4070" in text or "rtx 4060" in text or "rtx 5060" in text or "rtx 5070" in text:
            gpu = "NVIDIA GeForce RTX 4060 / 4070 / 5060 8GB"
            gpu_val = 320.0
        elif "rtx a5000" in text or "rtx a4500" in text:
            gpu = "NVIDIA RTX A4500 / A5000 16GB"
            gpu_val = 520.0
        elif "rtx a4000" in text or "rtx a3000" in text or "rtx a2000" in text:
            gpu = "NVIDIA RTX A2000 / A3000 / A4000 8-12GB"
            gpu_val = 360.0
        elif "rtx 3080" in text or "rtx 3070 ti" in text:
            gpu = "NVIDIA RTX 3070 Ti / 3080 8-16GB"
            gpu_val = 340.0
        elif "40-core gpu" in text or "38-core gpu" in text or "32-core gpu" in text or "18-core" in text:
            gpu = "Apple Silicon High-Core Workstation GPU"
            gpu_val = 450.0

        # 5. Screen extraction
        screen = '15.6" - 16" Workstation Display'
        screen_val = 100.0
        if "4k" in text or "uhd" in text or "3840x" in text:
            screen = '16" 4K UHD+ (3840x2400) IPS/OLED'
            screen_val = 200.0
        elif "liquid retina" in text or "xdr" in text:
            screen = '16.2" Liquid Retina XDR 120Hz ProMotion'
            screen_val = 220.0
        elif "oled" in text:
            screen = '16" 3.2K OLED 120Hz'
            screen_val = 180.0
        elif "qhd" in text or "2560x" in text or "wqxga" in text:
            screen = '16" QHD+ (2560x1600) 165Hz'
            screen_val = 140.0
        elif "desktop" in text or "mac studio" in text or "sff" in text or "mac mini" in text:
            screen = "Desktop / SFF Workstation (No screen)"
            screen_val = 0.0

        # Calculate Total Fair Market Value (FMV)
        chassis_base = 250.0
        fair_market_value = round(chassis_base + cpu_val + ram_val + ssd_val + gpu_val + screen_val, 2)

        # Price spread & margin
        asking = max(50.0, listing.price)
        profit = fair_market_value - asking
        margin_pct = (profit / asking) * 100.0

        # Calculate Deal Score (0.0 to 10.0)
        # Base formula: 5.0 + (profit / 150.0) + (margin_pct / 30.0)
        score_val = 5.0 + (profit / 180.0) + (margin_pct / 50.0)
        # Bonus for high-end components priced under $750
        if asking <= 750.0 and ("a4500" in text or "4000 ada" in text or "64gb" in text or "4k" in text):
            score_val += 1.2
        # Penalize if overpriced
        if profit < 0:
            score_val = max(1.0, 5.0 + (profit / 100.0))

        deal_score = round(max(0.5, min(9.9, score_val)), 1)

        recommendation = "FAIR VALUE"
        if deal_score >= 9.0:
            recommendation = "INSTANT ARBITRAGE BUY"
        elif deal_score >= 8.5:
            recommendation = "STRONG BUY (HIGH-YIELD DEAL)"
        elif deal_score >= 7.5:
            recommendation = "GOOD BUY FOR PERSONAL WORKSTATION"
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
            "confidence_score": 0.88,
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
