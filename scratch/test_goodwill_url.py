import cloudscraper
import urllib.parse

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

queries = [
    "Dell Precision",
    "ThinkPad P",
    "HP ZBook"
]

for q in queries:
    encoded = urllib.parse.quote_plus(q)
    url = f"https://shopgoodwill.com/categories/listing?st={encoded}"
    res = scraper.get(url, timeout=6)
    print(f"Query: {q} -> URL: {url} -> Status: {res.status_code}")
