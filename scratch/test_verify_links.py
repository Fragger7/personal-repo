import sys
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import cloudscraper

scraper = cloudscraper.create_scraper(browser={"browser": "chrome", "platform": "windows", "desktop": True})

test_links = [
    ("Reddit HWS Post", "https://www.reddit.com/r/hardwareswap/comments/1vqf5ue/usatx_h_dell_latitude_5550_core_ultra_7_155u_64gb/"),
    ("Reddit AppleSwap Post", "https://www.reddit.com/r/appleswap/comments/1vqetq7/usail_h_m4_pro_macbook_pro_m4_13_inch_ipad_pro_m5/"),
    ("Direct eBay Item from LaptopDeals", "https://www.ebay.com/itm/277705687959"),
]

for label, url in test_links:
    try:
        res = scraper.get(url, timeout=5.0)
        print(f"[{res.status_code}] {label}: {url[:80]}...")
    except Exception as e:
        print(f"[ERR] {label}: {e}")
