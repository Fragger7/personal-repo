import cloudscraper
from bs4 import BeautifulSoup
import re

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

test_urls = [
    "https://shopgoodwill.com/categories/listing?st=Dell%20Precision&sg=&c=&s=&lp=0&hp=999999&sbn=&spo=false&snpo=false&socs=false&sd=false&sca=false&sa=0&ic=0&pt=false&fe=0&tz=-5",
    "https://shopgoodwill.com/categories/listing?st=Dell+Precision",
    "https://shopgoodwill.com/categories/listing?st=thinkpad",
    "https://shopgoodwill.com/categories/listing?st=",
    "https://shopgoodwill.com/categories/computers-tablets-networking",
    "https://shopgoodwill.com/categories/listing?c=16",
    "https://shopgoodwill.com/search/overview?st=Dell+Precision",
    "https://shopgoodwill.com/search?st=Dell+Precision",
    "https://shopgoodwill.com",
]

for url in test_urls:
    try:
        res = scraper.get(url, timeout=6)
        print(f"URL: {url[:70]}... -> Status: {res.status_code}")
    except Exception as e:
        print(f"URL: {url[:70]}... -> Error: {e}")
