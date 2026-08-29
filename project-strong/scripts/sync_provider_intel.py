#!/usr/bin/env python3
"""
Automated Weekly Provider Intelligence Sync Script
Fetches latest upstream provider mappings, domain triggers, and category delimiters from StreamCheck / community feeds,
and updates both project-strong/provider_intelligence.json and the Android app assets.
"""

import json
import os
import sys
import httpx

STREAMCHECK_API_URL = "https://search.streamcheck.pro/api/providers"
LOCAL_JSON_PATH = os.path.join(os.path.dirname(__file__), "..", "provider_intelligence.json")
ANDROID_ASSET_PATH = os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main", "assets", "provider_intelligence.json")

def fetch_upstream_providers():
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Accept": "application/json"
    }
    try:
        with httpx.Client(timeout=15.0, follow_redirects=True) as client:
            resp = client.get(STREAMCHECK_API_URL, headers=headers)
            if resp.status_code == 200:
                return resp.json()
            else:
                print(f"[!] Warning: API returned status code {resp.status_code}. Using fallback enrichment.")
                return None
    except Exception as e:
        print(f"[!] Network error fetching from streamcheck API: {e}")
        return None

def main():
    print("Fetching provider intelligence feeds...")
    data = fetch_upstream_providers()

    existing_intel = {}
    if os.path.exists(LOCAL_JSON_PATH):
        try:
            with open(LOCAL_JSON_PATH, "r", encoding="utf-8") as f:
                existing_intel = json.load(f)
        except Exception as e:
            print(f"[!] Failed to parse existing intelligence JSON: {e}")

    updated_count = 0
    if isinstance(data, list):
        for item in data:
            domain = item.get("domain", "").strip().lower()
            provider_name = item.get("provider", item.get("name", "")).strip()
            if domain and provider_name:
                if domain not in existing_intel or not existing_intel[domain].get("provider_name"):
                    existing_intel[domain] = {
                        "domain": domain,
                        "provider_name": f"🎯 Identified: {provider_name}",
                        "community_link": item.get("community", None),
                        "evidence": "Discovered via StreamCheck provider index",
                        "first_seen": item.get("date", "2026-08-29"),
                        "last_seen": "2026-08-29"
                    }
                    updated_count += 1

    print(f"Intelligence database updated with {updated_count} new mappings. Total domains: {len(existing_intel)}")

    # Write to root json
    os.makedirs(os.path.dirname(os.path.abspath(LOCAL_JSON_PATH)), exist_ok=True)
    with open(LOCAL_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(existing_intel, f, indent=2)

    # Write to Android asset json
    if os.path.exists(os.path.dirname(os.path.abspath(ANDROID_ASSET_PATH))):
        with open(ANDROID_ASSET_PATH, "w", encoding="utf-8") as f:
            json.dump(existing_intel, f, indent=2)
        print(f"Synced to Android assets at {ANDROID_ASSET_PATH}")

if __name__ == "__main__":
    main()
