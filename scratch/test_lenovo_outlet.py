import cloudscraper
from bs4 import BeautifulSoup
import re

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

urls = [
    "https://slickdeals.net/newsearch.php?searchfirst=1&q=lenovo+outlet+thinkpad&hideexpired=1&sort=newest&rss=1",
    "https://slickdeals.net/newsearch.php?searchfirst=1&q=dell+outlet+precision&hideexpired=1&sort=newest&rss=1",
    "https://slickdeals.net/newsearch.php?searchfirst=1&q=goodwill+laptop+thinkpad&hideexpired=1&sort=newest&rss=1",
]

for url in urls:
    print(f"\n--- Testing feed: {url[:60]}... ---")
    try:
        res = scraper.get(url, timeout=6)
        print(f"Status: {res.status_code}, Length: {len(res.text)}")
        if res.status_code == 200:
            item_blocks = re.findall(r"<item>([\s\S]*?)</item>", res.text)
            print(f"Found {len(item_blocks)} item blocks in feed")
            for idx, block in enumerate(item_blocks[:3], 1):
                title_m = re.search(r"<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>", block, re.DOTALL)
                link_m = re.search(r"<link>(.*?)</link>", block)
                if title_m:
                    print(f"[{idx}] {title_m.group(1).strip()[:75]}")
                    if link_m:
                        print(f"     Link: {link_m.group(1).strip()[:75]}")
    except Exception as e:
        print(f"Error: {e}")
