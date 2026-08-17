import cloudscraper

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.5",
}

for sub in ["hardwareswap", "appleswap", "LaptopDeals", "thinkpad"]:
    url = f"https://www.reddit.com/r/{sub}/new/.rss"
    res = scraper.get(url, headers=headers, timeout=5)
    print(f"r/{sub} status: {res.status_code}, len: {len(res.text)}")
