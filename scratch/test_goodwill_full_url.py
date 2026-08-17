import cloudscraper
import urllib.parse
from bs4 import BeautifulSoup

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

query = "Dell Precision"
encoded = urllib.parse.quote_plus(query)
url = f"https://shopgoodwill.com/categories/listing?st={encoded}&sg=&c=&s=&lp=0&hp=999999&sbn=&spo=false&snpo=false&socs=false&sd=false&sca=false&sa=0&ic=0&pt=false&fe=0&tz=-5"

res = scraper.get(url, timeout=6)
print(f"Status: {res.status_code}, Length: {len(res.text)}")
soup = BeautifulSoup(res.text, "html.parser")
print("Title:", soup.title.string if soup.title else "No title")
