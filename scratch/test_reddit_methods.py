import cloudscraper
from bs4 import BeautifulSoup
import re

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

subreddits = ["hardwareswap", "appleswap", "homelabsales", "LaptopDeals", "thinkpad"]

print("--- Testing old.reddit.com HTML scraping ---")
for sub in subreddits:
    url = f"https://old.reddit.com/r/{sub}/new/"
    try:
        res = scraper.get(url, timeout=5)
        print(f"r/{sub} HTML Status: {res.status_code}")
        if res.status_code == 200:
            soup = BeautifulSoup(res.text, "html.parser")
            entries = soup.find_all("div", class_=re.compile(r"thing\s+link"))
            print(f"  Found {len(entries)} posts in r/{sub}")
            for entry in entries[:2]:
                title_elem = entry.find("a", class_="title")
                title = title_elem.text.strip() if title_elem else ""
                permalink = entry.get("data-permalink", "")
                if not permalink and title_elem:
                    href = title_elem.get("href", "")
                    if "/comments/" in href:
                        permalink = href
                full_url = f"https://www.reddit.com{permalink}" if permalink.startswith("/") else permalink
                print(f"   -> [{title[:40]}...] URL: {full_url}")
    except Exception as e:
        print(f"r/{sub} Error: {e}")

print("\n--- Testing Reddit RSS scraping ---")
for sub in subreddits:
    rss_url = f"https://www.reddit.com/r/{sub}/new/.rss"
    try:
        res = scraper.get(rss_url, timeout=5)
        print(f"r/{sub} RSS Status: {res.status_code}")
        if res.status_code == 200:
            item_links = re.findall(r'<link>(https://www.reddit.com/r/' + sub + r'/comments/[^<]+)</link>', res.text)
            print(f"  Found {len(item_links)} direct comment links via RSS in r/{sub}:")
            for link in item_links[:2]:
                print(f"   -> {link}")
    except Exception as e:
        print(f"r/{sub} RSS Error: {e}")
