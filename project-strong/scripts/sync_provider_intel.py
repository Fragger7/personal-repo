#!/usr/bin/env python3
"""
Automated Weekly Provider Intelligence Sync Script
Fetches latest upstream provider mappings, domain triggers, and category delimiters from StreamCheck,
community feeds, and local committed catalog records, and updates both project-strong/provider_intelligence.json
and the Android app assets.
"""

import json
import os
import sys
import time
import datetime
import urllib.parse

try:
    import httpx
    HAS_HTTPX = True
except ImportError:
    HAS_HTTPX = False
    import urllib.request

STREAMCHECK_PROVIDERS_URL = "https://search.streamcheck.pro/api/providers?includeOffline=true"
STREAMCHECK_SERIES_URL = "https://search.streamcheck.pro/api/series/providers"
STREAMCHECK_MOVIES_URL = "https://search.streamcheck.pro/api/movies/providers"
STREAMCHECK_SEARCH_URL = "https://search.streamcheck.pro/api/search"

LOCAL_JSON_PATH = os.path.join(os.path.dirname(__file__), "..", "provider_intelligence.json")
ANDROID_ASSET_PATH = os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main", "assets", "provider_intelligence.json")
COMMITTED_PATH = os.path.join(os.path.dirname(__file__), "..", "committed.json")

def http_get_json(url, timeout=15.0):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Accept": "application/json"
    }
    if HAS_HTTPX:
        try:
            with httpx.Client(timeout=timeout, follow_redirects=True) as client:
                resp = client.get(url, headers=headers)
                if resp.status_code == 200:
                    return resp.json()
                print(f"[!] HTTP {resp.status_code} from {url}")
        except Exception as e:
            print(f"[!] httpx error fetching {url}: {e}")
    else:
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                if resp.status == 200:
                    return json.loads(resp.read().decode("utf-8"))
        except Exception as e:
            print(f"[!] urllib error fetching {url}: {e}")
    return None

def http_post_json(url, payload, timeout=15.0):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Accept": "application/json",
        "Content-Type": "application/json"
    }
    if HAS_HTTPX:
        try:
            with httpx.Client(timeout=timeout, follow_redirects=True) as client:
                resp = client.post(url, headers=headers, json=payload)
                if resp.status_code == 200:
                    return resp.json()
        except Exception as e:
            print(f"[!] httpx error posting to {url}: {e}")
    else:
        try:
            data_bytes = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(url, data=data_bytes, headers=headers, method="POST")
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                if resp.status == 200:
                    return json.loads(resp.read().decode("utf-8"))
        except Exception as e:
            print(f"[!] urllib error posting to {url}: {e}")
    return None

COUNTRY_DEMONYM_MAP = {
    "france": "French", "french": "French", "francais": "French", "francaise": "French",
    "sweden": "Swedish / Nordic", "swedish": "Swedish / Nordic", "sverige": "Swedish / Nordic", "svenska": "Swedish / Nordic",
    "norway": "Norwegian / Nordic", "norwegian": "Norwegian / Nordic", "norge": "Norwegian / Nordic", "norsk": "Norwegian / Nordic",
    "denmark": "Danish / Nordic", "danish": "Danish / Nordic", "danmark": "Danish / Nordic", "dansk": "Danish / Nordic",
    "finland": "Finnish / Nordic", "finnish": "Finnish / Nordic", "suomi": "Finnish / Nordic",
    "nordic": "Nordic", "scandinavia": "Nordic", "scandinavian": "Nordic",
    "arabic": "Arabic", "arab": "Arabic", "arabe": "Arabic",
    "italy": "Italian", "italian": "Italian", "italia": "Italian", "italiano": "Italian",
    "germany": "German", "german": "German", "deutschland": "German", "deutsch": "German",
    "spain": "Spanish", "spanish": "Spanish", "espana": "Spanish", "espanol": "Spanish",
    "portugal": "Portuguese", "portuguese": "Portuguese", "portugues": "Portuguese",
    "brazil": "Brazilian", "brazilian": "Brazilian", "brasil": "Brazilian",
    "netherlands": "Dutch", "dutch": "Dutch", "holland": "Dutch", "nederland": "Dutch",
    "turkey": "Turkish", "turkish": "Turkish", "turkce": "Turkish", "turkiye": "Turkish",
    "greece": "Greek", "greek": "Greek", "ellada": "Greek",
    "poland": "Polish", "polish": "Polish", "polska": "Polish", "polski": "Polish",
    "romania": "Romanian", "romanian": "Romanian",
    "russia": "Russian", "russian": "Russian",
    "albania": "Albanian", "albanian": "Albanian", "shqip": "Albanian",
    "exyu": "Ex-Yu / Balkan", "balkan": "Ex-Yu / Balkan", "serbia": "Balkan", "croatia": "Balkan", "bosnia": "Balkan",
    "uk": "UK / British", "british": "UK / British", "england": "UK / British",
    "usa": "USA", "us": "USA", "america": "USA", "american": "USA",
    "canada": "Canadian", "canadian": "Canadian",
    "pakistan": "Pakistani", "pak": "Pakistani", "india": "Indian", "hindi": "Indian", "urdu": "Pakistani / Urdu"
}

GENERIC_BLACKLIST = set(COUNTRY_DEMONYM_MAP.keys()) | {
    'vip', 'vod', 'series', 'movies', 'live', 'channels', 'channel',
    'sport', 'sports', 'kids', 'news', 'catchup', 'all', 'xxx', 'adult',
    'cinema', 'documentary', 'music', 'radio', 'entertainment',
    'general', 'local', 'regional', 'national', 'international', 'ppv',
    '4k', 'fhd', 'hd', 'hevc', 'sd', 'premium', 'ultra', 'free'
}

def is_demonym_or_country(text):
    if not text:
        return False
    clean = text.lower().strip()
    if clean.startswith("🎯 identified:"):
        clean = clean[14:].strip()
    return clean in COUNTRY_DEMONYM_MAP

def get_regional_focus(text):
    if not text:
        return None
    clean = text.lower().strip()
    if clean.startswith("🎯 identified:"):
        clean = clean[14:].strip()
    return COUNTRY_DEMONYM_MAP.get(clean)

def normalize_domain_key(url_or_host):
    if not url_or_host:
        return ""
    clean = url_or_host.strip()
    try:
        parsed = urllib.parse.urlparse(clean)
        netloc = parsed.netloc
        if netloc:
            clean = netloc
    except Exception:
        pass
    if clean.startswith("http://"):
        clean = clean[7:]
    elif clean.startswith("https://"):
        clean = clean[8:]
    clean = clean.split("/")[0].strip().lower()
    return clean

def main():
    print("=" * 60)
    print("🕵️‍♂️ Sherlock Streams — Provider Intelligence Sync Engine")
    print("=" * 60)
    today_str = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S")

    # 1. Load existing database
    existing_intel = {}
    if os.path.exists(LOCAL_JSON_PATH):
        try:
            with open(LOCAL_JSON_PATH, "r", encoding="utf-8") as f:
                existing_intel = json.load(f)
            print(f"Loaded {len(existing_intel)} existing provider intelligence records.")
        except Exception as e:
            print(f"[!] Failed to parse existing intelligence JSON: {e}")

    updated_count = 0

    # Cleanse any previous false-positive country/demonym classifications
    for domain_k, entry in list(existing_intel.items()):
        p_name = entry.get("provider_name", "")
        if is_demonym_or_country(p_name):
            reg = get_regional_focus(p_name)
            entry["provider_name"] = "Unidentified Provider"
            entry["regional_focus"] = reg
            entry["confidence"] = f"Regional Bouquet ({reg})"
            entry["evidence"] = f"Identified {reg} channel/category bouquet from verified node."
            updated_count += 1
            print(f"  [⟳] Corrected misclassified country entry: {domain_k} -> Regional Bouquet ({reg})")

    # 2. Ingest StreamCheck Upstream Provider Directory
    print("\n[+] Fetching live providers from StreamCheck index...")
    providers_data = http_get_json(STREAMCHECK_PROVIDERS_URL)
    if isinstance(providers_data, list):
        print(f"Found {len(providers_data)} active providers in StreamCheck directory:")
        for item in providers_data:
            provider_name = item.get("provider", "").strip()
            ch_count = item.get("channel_count", "0")
            last_run = item.get("last_run", "")
            dash_url = item.get("dashboard_url", "")
            print(f"  • {provider_name:12} | Channels: {ch_count:>6} | Last Seen: {last_run}")

    # 3. Ingest and Learn from Committed Records Catalog
    if os.path.exists(COMMITTED_PATH):
        print("\n[+] Cross-referencing verified records in committed.json...")
        try:
            with open(COMMITTED_PATH, "r", encoding="utf-8") as f:
                committed_records = json.load(f)
            if isinstance(committed_records, list):
                for record in committed_records:
                    provider = record.get("Provider", "").strip()
                    base_url = record.get("base_url", "").strip()
                    if not provider or not base_url:
                        continue
                    clean_p = provider.strip()
                    if (clean_p.lower() in ["unknown", "unbranded", "none", "null", ""] 
                        or clean_p.startswith("Host:") 
                        or clean_p.startswith("👤")):
                        continue

                    domain_key = normalize_domain_key(base_url)
                    if not domain_key:
                        continue

                    # Demonym / Regional classification check
                    if is_demonym_or_country(clean_p):
                        reg_focus = get_regional_focus(clean_p)
                        existing_entry = existing_intel.get(domain_key)
                        if not existing_entry:
                            existing_intel[domain_key] = {
                                "domain": domain_key,
                                "provider_name": "Unidentified Provider",
                                "regional_focus": reg_focus,
                                "server": record.get("Server"),
                                "cloudflare": "Yes" if "cloudflare" in (record.get("Server") or "").lower() else "No",
                                "timezone": record.get("Timezone", "UTC"),
                                "metadata_message": None,
                                "server_protocol": "http",
                                "https_port": "443",
                                "rtmp_port": "25462",
                                "allowed_formats": "['m3u8', 'ts']",
                                "community_link": None,
                                "confidence": f"Regional Bouquet ({reg_focus})",
                                "evidence": f"Identified {reg_focus} channel bouquet from verified node",
                                "first_seen": record.get("DateAdded", today_str),
                                "last_seen": today_str
                            }
                            updated_count += 1
                            print(f"  [+] Learned regional bouquet: {domain_key} -> {reg_focus}")
                        elif existing_entry.get("regional_focus") != reg_focus:
                            existing_entry["regional_focus"] = reg_focus
                            if not existing_entry.get("provider_name", "").startswith("🎯"):
                                existing_entry["confidence"] = f"Regional Bouquet ({reg_focus})"
                            updated_count += 1
                        continue

                    # If not already present, or if current record is generic/unbranded
                    existing_entry = existing_intel.get(domain_key)
                    needs_update = False
                    if not existing_entry:
                        needs_update = True
                    elif not existing_entry.get("provider_name", "").startswith("🎯"):
                        needs_update = True

                    if needs_update:
                        clean_provider = provider if provider.startswith("🎯") else f"🎯 Identified: {provider}"
                        server_info = record.get("Server") or (existing_entry.get("server") if existing_entry else None)
                        is_cf = "Yes" if "cloudflare" in (server_info or "").lower() else (existing_entry.get("cloudflare", "No") if existing_entry else "No")
                        tz = record.get("Timezone") or (existing_entry.get("timezone", "UTC") if existing_entry else "UTC")
                        first_seen = record.get("DateAdded") or (existing_entry.get("first_seen", today_str) if existing_entry else today_str)

                        existing_intel[domain_key] = {
                            "domain": domain_key,
                            "provider_name": clean_provider,
                            "server": server_info,
                            "cloudflare": is_cf,
                            "timezone": tz,
                            "metadata_message": existing_entry.get("metadata_message") if existing_entry else None,
                            "server_protocol": "http",
                            "https_port": "443",
                            "rtmp_port": "25462",
                            "allowed_formats": "['m3u8', 'ts']",
                            "community_link": existing_entry.get("community_link") if existing_entry else None,
                            "confidence": "Verified Brand (Committed Catalog)",
                            "evidence": f"Discovered via verified committed node {provider}",
                            "regional_focus": existing_entry.get("regional_focus") if existing_entry else None,
                            "first_seen": first_seen,
                            "last_seen": today_str
                        }
                        updated_count += 1
                        print(f"  [+] Learned new mapping: {domain_key} -> {clean_provider}")
        except Exception as e:
            print(f"[!] Error reading committed.json: {e}")

    # 4. Sample Upstream Delimiters from StreamCheck Channel Search
    print("\n[+] Sampling live channel delimiters from StreamCheck search...")
    sample_queries = ["VIP", "4K", "SPORT", "EPL"]
    discovered_delimiters = set()
    for q in sample_queries:
        res = http_post_json(STREAMCHECK_SEARCH_URL, {"query": q})
        if isinstance(res, dict) and "results" in res:
            for r in res.get("results", []):
                p_name = r.get("provider", "")
                for ch in r.get("channels", []):
                    grp = ch.get("group", "")
                    # Check for delimiter markers
                    for delim in ["▎", "┃", "✪", "★", "▶ ️", "ᴿᴬᵂ", "|", "=== ", "### "]:
                        if delim in grp:
                            discovered_delimiters.add((p_name, delim))
    if discovered_delimiters:
        print(f"Verified {len(discovered_delimiters)} provider watermark delimiters in live streams:")
        for p_name, delim in sorted(discovered_delimiters)[:10]:
            print(f"  • {p_name:12} uses watermark delimiter '{delim}'")

    print("\n" + "=" * 60)
    print(f"Intelligence sync complete: {updated_count} new entries learned.")
    print(f"Total entries in knowledge base: {len(existing_intel)}")
    print("=" * 60)

    # 5. Persist updates if changes occurred
    if updated_count > 0:
        # Write to root json with 4-space indent
        os.makedirs(os.path.dirname(os.path.abspath(LOCAL_JSON_PATH)), exist_ok=True)
        with open(LOCAL_JSON_PATH, "w", encoding="utf-8") as f:
            json.dump(existing_intel, f, indent=4)
        print(f"[✓] Saved updated intelligence to {LOCAL_JSON_PATH}")

        # Write to Android asset json
        android_dir = os.path.dirname(os.path.abspath(ANDROID_ASSET_PATH))
        if os.path.exists(android_dir):
            with open(ANDROID_ASSET_PATH, "w", encoding="utf-8") as f:
                json.dump(existing_intel, f, indent=4)
            print(f"[✓] Synced updated intelligence to Android assets at {ANDROID_ASSET_PATH}")
    else:
        print("[i] No new updates detected. Database is completely up to date. Files preserved intact.")

if __name__ == "__main__":
    main()
