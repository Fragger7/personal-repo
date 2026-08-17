import cloudscraper
import re

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

res = scraper.get("https://shopgoodwill.com/categories/listing?st=laptop&sg=&c=&s=&lp=0&hp=999999&sbn=&spo=false&snpo=false&socs=false&sd=false&sca=false&sa=0&ic=0&pt=false&fe=0&tz=-5", timeout=6)

print(f"Goodwill Search Status: {res.status_code}")
item_ids = re.findall(r'item/([0-9]{7,10})', res.text)
print("Found item IDs in page:", set(item_ids))
for iid in list(set(item_ids))[:3]:
    item_url = f"https://shopgoodwill.com/item/{iid}"
    item_res = scraper.get(item_url, timeout=5)
    print(f"Direct item URL {item_url} -> Status: {item_res.status_code}")
