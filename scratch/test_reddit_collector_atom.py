import cloudscraper
import re
import html

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

subreddits = ["hardwareswap", "appleswap", "homelabsales", "LaptopDeals", "thinkpad"]
all_items = []

for sub in subreddits:
    url = f"https://www.reddit.com/r/{sub}/new/.rss"
    try:
        res = scraper.get(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}, timeout=4.0)
        if res.status_code == 200:
            entries = re.findall(r'<entry>([\s\S]*?)</entry>', res.text)
            for e in entries:
                title_m = re.search(r'<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>', e, re.DOTALL)
                link_m = re.search(r'<link[^>]*href="([^"]+)"', e)
                author_m = re.search(r'<author><name>/u/([^<]+)</name>', e)
                content_m = re.search(r'<content[^>]*>([\s\S]*?)</content>', e)
                
                title = html.unescape(title_m.group(1).strip()) if title_m else ""
                link = link_m.group(1) if link_m else ""
                author = author_m.group(1) if author_m else "anonymous"
                content = html.unescape(content_m.group(1).strip()) if content_m else ""
                
                if link and "/comments/" in link:
                    all_items.append({"sub": sub, "title": title, "author": author, "link": link})
    except Exception as err:
        pass

print(f"Total live Reddit posts with direct /comments/ URLs collected: {len(all_items)}")
for itm in all_items[:10]:
    print(f"[{itm['sub']}] {itm['title'][:50]} -> {itm['link']}")
