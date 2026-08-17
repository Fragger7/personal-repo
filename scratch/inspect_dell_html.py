import cloudscraper
from bs4 import BeautifulSoup
import re

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

url = "https://www.dellrefurbished.com/laptops"
res = scraper.get(url, timeout=10)
print(f"Status: {res.status_code}, Length: {len(res.text)}")

soup = BeautifulSoup(res.text, "html.parser")

# Find coupon codes
coupon_matches = re.findall(r"([0-9]{2}%\s*off[^\n<]*|coupon\s*code[^\n<]*|[A-Z0-9]{4,15}\s*(?:for\s*[0-9]{2}%\s*off)?)", res.text, re.I)
print("Coupons found in HTML:")
for c in set(coupon_matches[:10]):
    if any(kw in c.lower() for kw in ["off", "code", "%"]):
        print(f"  - {c.strip()[:80]}")

# Look for product items in the page
# Let's inspect class names of div / li elements
product_divs = soup.find_all(["div", "li", "article"], class_=lambda x: x and any(k in x.lower() for k in ["product", "item", "catalog", "grid"]))
print(f"\nFound {len(product_divs)} potential product containers")
for d in product_divs[:8]:
    classes = d.get("class", [])
    text_preview = d.get_text(" ", strip=True)[:100]
    print(f"  Class: {classes} | Text: {text_preview}")
