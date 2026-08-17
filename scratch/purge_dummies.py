import sys
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import json

path = "scratch/personal_repo_push/ws-deal-hunter/deals.json"
with open(path, "r", encoding="utf-8") as f:
    deals = json.load(f)

print(f"Total deals before purge: {len(deals)}")

# Dummy fallback IDs to purge completely
DUMMY_IDS = {
    "lenovo_outlet_thinkpad_p16_g1",
    "lenovo_outlet_thinkpad_p1_g6",
    "goodwill_auction_precision_7550",
    "ebay_item_405128491",
    "ebay_item_296184910",
    "ebay_item_185934812",
    "dell_refurb_precision_5570_dfs",
    "dell_refurb_precision_7680_dfs",
    "swappa_listing_lenovo_p15_g2",
    "swappa_listing_macbook_pro_16_m1pro",
}

clean_deals = []
for d in deals:
    deal_id = d.get("id", "")
    url = d.get("url", "")
    title = d.get("title", "")
    score = d.get("deal_score", 0.0)
    source = d.get("source", "")
    
    if deal_id in DUMMY_IDS:
        print(f"  [PURGED DUMMY ID]: {deal_id} -> {title[:50]}")
        continue
        
    if "seed_" in url or "ebay.com/sch/i.html" in url:
        print(f"  [PURGED GENERIC/SEED URL]: {title[:50]} -> {url}")
        continue
        
    clean_deals.append(d)
    print(f"  [KEPT REAL LIVE DEAL] (${d.get('price',0):.0f} | Score {score:3.1f}): [{source:16s}] {title[:50]}")
    print(f"       -> {url}")

print(f"\nFinal Live-Only Deals Count: {len(clean_deals)}")

with open(path, "w", encoding="utf-8") as f:
    json.dump(clean_deals, f, indent=2)

print("Saved clean live-only deals.json successfully.")
