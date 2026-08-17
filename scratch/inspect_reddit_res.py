import cloudscraper

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

res = scraper.get("https://old.reddit.com/r/hardwareswap/new/", timeout=6)
print("old.reddit length:", len(res.text))
print("old.reddit sample:", res.text[:500])

res_rss = scraper.get("https://www.reddit.com/r/hardwareswap/new/.rss", headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"}, timeout=6)
print("\nrss length:", len(res_rss.text))
print("rss sample:", res_rss.text[:500])
