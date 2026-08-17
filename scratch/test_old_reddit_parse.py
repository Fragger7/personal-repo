import cloudscraper
from bs4 import BeautifulSoup
import re

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

res = scraper.get("https://old.reddit.com/r/hardwareswap/new/", timeout=8)
print(f"Status: {res.status_code}, Length: {len(res.text)}")

soup = BeautifulSoup(res.text, "html.parser")
# Find all entries
entries = soup.find_all("div", attrs={"data-fullname": True})
print(f"Found {len(entries)} entry elements with data-fullname:")
for e in entries[:5]:
    title_a = e.find("a", class_="title")
    title = title_a.text.strip() if title_a else "No title"
    permalink = e.get("data-permalink", "")
    full_url = f"https://www.reddit.com{permalink}" if permalink else (title_a.get("href", "") if title_a else "")
    print(f" - [{title[:50]}...]")
    print(f"   Direct Link: {full_url}")
