import cloudscraper

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

for sub in ["hardwareswap", "appleswap", "LaptopDeals", "thinkpad"]:
    url = f"https://www.reddit.com/r/{sub}/new/.rss"
    res = scraper.get(url, headers={"User-Agent": "WorkstationDealHunter/1.0 (by /u/DealHunterBot)"})
    print(f"r/{sub} status: {res.status_code}, len: {len(res.text)}")
