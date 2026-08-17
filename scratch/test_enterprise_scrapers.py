import cloudscraper
from bs4 import BeautifulSoup
import re
import json

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

def test_dell_refurbished():
    print("\n--- Testing Dell Refurbished ---")
    url = "https://www.dellrefurbished.com/laptops"
    try:
        res = scraper.get(url, timeout=6)
        print(f"Status: {res.status_code}, Length: {len(res.text)}")
        if res.status_code == 200:
            soup = BeautifulSoup(res.text, "html.parser")
            # Look for promo coupon banner
            promo_text = ""
            promo_elems = soup.find_all(text=re.compile(r"(coupon|%\s*off|code:)", re.I))
            for p in promo_elems[:3]:
                print(f"Promo text match: {p.strip()}")
            
            # Find product cards
            cards = soup.find_all("div", class_=re.compile(r"(product-item|item-card|product)", re.I))
            print(f"Found {len(cards)} product elements on page")
    except Exception as e:
        print(f"Dell Refurbished error: {e}")

def test_goodwill():
    print("\n--- Testing ShopGoodwill API/Search ---")
    # ShopGoodwill has a public JSON search endpoint
    search_url = "https://buyerapi.shopgoodwill.com/api/Search/ItemListing"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Content-Type": "application/json",
    }
    payload = {
        "categoryId": 0,
        "categoryLevel": 1,
        "categoryLevel2": "",
        "categoryLevel3": "",
        "categoryLevel4": "",
        "closedAuctionDaysBack": "0",
        "closedAuctionEndingDate": "",
        "highPrice": "9999",
        "lowPrice": "50",
        "page": "1",
        "pageSize": "15",
        "searchText": "ThinkPad Precision ZBook",
        "selectedCategoryIds": "",
        "selectedGroup": "",
        "selectedSellerIds": "",
        "sortColumn": "1",
        "sortDescending": "false",
        "useBuyerPrefs": "true"
    }
    try:
        res = scraper.post(search_url, json=payload, headers=headers, timeout=6)
        print(f"ShopGoodwill Status: {res.status_code}")
        if res.status_code == 200:
            data = res.json()
            items = data.get("searchResults", [])
            print(f"ShopGoodwill found {len(items)} live items:")
            for item in items[:5]:
                print(f" - [{item.get('itemId')}] {item.get('title')} | Price: ${item.get('currentPrice')} | URL: https://shopgoodwill.com/item/{item.get('itemId')}")
    except Exception as e:
        print(f"Goodwill error: {e}")

if __name__ == "__main__":
    test_dell_refurbished()
    test_goodwill()
