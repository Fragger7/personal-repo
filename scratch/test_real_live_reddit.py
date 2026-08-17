import cloudscraper
from bs4 import BeautifulSoup
import re
import html

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

subreddits = ["hardwareswap", "appleswap", "homelabsales", "LaptopDeals", "thinkpad"]
verified_posts = []

for sub in subreddits:
    url = f"https://old.reddit.com/r/{sub}/new/"
    try:
        res = scraper.get(url, timeout=6)
        if res.status_code == 200:
            soup = BeautifulSoup(res.text, "html.parser")
            entries = soup.find_all("div", attrs={"data-fullname": True})
            print(f"Scraped r/{sub}: found {len(entries)} live entries")
            for e in entries:
                title_a = e.find("a", class_=re.compile(r"\btitle\b"))
                if not title_a:
                    continue
                title = title_a.text.strip()
                permalink = e.get("data-permalink", "")
                if not permalink:
                    href = title_a.get("href", "")
                    if "/comments/" in href:
                        permalink = href
                if permalink:
                    full_url = f"https://www.reddit.com{permalink}" if permalink.startswith("/") else permalink
                    verified_posts.append({
                        "sub": sub,
                        "title": title,
                        "url": full_url,
                    })
    except Exception as err:
        print(f"Error scraping r/{sub}: {err}")

print(f"\nTotal real live Reddit posts found: {len(verified_posts)}")
for p in verified_posts[:10]:
    print(f"[{p['sub']}] {p['title'][:60]}")
    print(f"   -> {p['url']}")
