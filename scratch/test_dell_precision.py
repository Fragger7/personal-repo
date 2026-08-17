import cloudscraper
from bs4 import BeautifulSoup
import re

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

urls = [
    "https://www.dellrefurbished.com/laptops?model_family=266",  # Dell Precision Family
    "https://www.dellrefurbished.com/laptops",
]

for url in urls:
    print(f"\nFetching: {url}")
    res = scraper.get(url, timeout=10)
    soup = BeautifulSoup(res.text, "html.parser")
    items = soup.find_all("div", class_="thumb-grid")
    print(f"Found {len(items)} items in {url}")
    for idx, item in enumerate(items[:5], 1):
        title_elem = item.find(["h3", "h4", "a", "span"], class_=re.compile(r"title|name|header", re.I)) or item.find("a")
        title = title_elem.get_text(" ", strip=True) if title_elem else item.get_text(" ", strip=True)[:50]
        link = title_elem.get("href", "") if title_elem and title_elem.name == "a" else ""
        if not link:
            a_elem = item.find("a", href=True)
            link = a_elem["href"] if a_elem else ""
        if link and not link.startswith("http"):
            link = f"https://www.dellrefurbished.com{link}"
            
        full_text = item.get_text(" ", strip=True)
        # Extract Sale / List price
        sale_match = re.search(r"SALE\s*\$\s*([0-9,]+(?:\.[0-9]{2})?)", full_text)
        list_match = re.search(r"\$\s*([0-9,]+(?:\.[0-9]{2})?)", full_text)
        price = float(sale_match.group(1).replace(",", "")) if sale_match else (float(list_match.group(1).replace(",", "")) if list_match else 0.0)
        
        # Extract coupon discount if present (e.g. 50% off)
        discount_match = re.search(r"([0-9]{2})%\s*off", full_text, re.I)
        discount_pct = float(discount_match.group(1)) if discount_match else 0.0
        
        # Extract specs
        cpu_match = re.search(r"CPU\s*1x\s*([^\n\r\|]+?)(?=\s*Memory|\s*Hard Drive|\s*Display|\s*Graphics|\s*\$|$)", full_text, re.I)
        mem_match = re.search(r"Memory\s*([0-9]+)\s*GB", full_text, re.I)
        hdd_match = re.search(r"(?:Hard Drive|Storage|SSD)\s*([0-9]+)\s*(?:GB|TB)", full_text, re.I)
        
        print(f"[{idx}] {title}")
        print(f"     Price: ${price} (Discount: {discount_pct}%) | URL: {link}")
        print(f"     CPU: {cpu_match.group(1).strip() if cpu_match else 'N/A'} | RAM: {mem_match.group(1) if mem_match else 'N/A'}GB | SSD: {hdd_match.group(1) if hdd_match else 'N/A'}")
