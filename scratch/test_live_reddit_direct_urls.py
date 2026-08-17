import cloudscraper
from bs4 import BeautifulSoup
import re

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

subreddits = ["hardwareswap", "appleswap", "homelabsales", "LaptopDeals", "thinkpad"]

for sub in subreddits:
    url = f"https://old.reddit.com/r/{sub}/new/.json"
    print(f"\n--- Scraping r/{sub} via {url} ---")
    try:
        res = scraper.get(url, timeout=5)
        print(f"Status: {res.status_code}")
        if res.status_code == 200:
            data = res.json()
            children = data.get("data", {}).get("children", [])
            print(f"Found {len(children)} posts in r/{sub}:")
            for c in children[:4]:
                pdata = c.get("data", {})
                title = pdata.get("title", "")
                permalink = pdata.get("permalink", "")
                direct_url = f"https://www.reddit.com{permalink}"
                print(f" - Title: {title[:60]}...")
                print(f"   Direct Post URL: {direct_url}")
    except Exception as e:
        print(f"Error for r/{sub}: {e}")
