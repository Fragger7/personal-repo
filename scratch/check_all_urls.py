import json
import cloudscraper

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

with open("scratch/personal_repo_push/ws-deal-hunter/deals.json", "r", encoding="utf-8") as f:
    deals = json.load(f)

print(f"Checking all {len(deals)} URLs in deals.json...")
for idx, d in enumerate(deals, 1):
    url = d.get("url", "")
    source = d.get("source", "")
    title = d.get("title", "")[:40]
    print(f"[{idx:2d}] [{source:16s}] {title:40s} -> {url}")
