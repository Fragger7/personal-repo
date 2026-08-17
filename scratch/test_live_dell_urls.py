import cloudscraper
from bs4 import BeautifulSoup
import re

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

res = scraper.get("https://www.dellrefurbished.com/laptops", timeout=8)
soup = BeautifulSoup(res.text, "html.parser")
items = soup.find_all("div", class_="thumb-grid")

print(f"Dell Refurbished live items found: {len(items)}")
for idx, item in enumerate(items[:10], 1):
    title_elem = item.find(["h3", "h4", "a", "span"], class_=re.compile(r"title|name|header", re.I)) or item.find("a")
    title = title_elem.get_text(" ", strip=True) if title_elem else "Dell Laptop"
    a_elem = item.find("a", href=True)
    link = a_elem["href"] if a_elem else ""
    if link and not link.startswith("http"):
        link = f"https://www.dellrefurbished.com{link}"
    
    full_text = item.get_text(" ", strip=True)
    sale_match = re.search(r"SALE\s*\$\s*([0-9,]+(?:\.[0-9]{2})?)", full_text)
    list_match = re.search(r"\$\s*([0-9,]+(?:\.[0-9]{2})?)", full_text)
    price = float(sale_match.group(1).replace(",", "")) if sale_match else (float(list_match.group(1).replace(",", "")) if list_match else 0.0)
    
    print(f"[{idx:2d}] {title:35s} | ${price:6.1f} | {link}")
