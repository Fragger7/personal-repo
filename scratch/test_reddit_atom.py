import cloudscraper
import re
import html

scraper = cloudscraper.create_scraper(browser={'browser': 'chrome', 'platform': 'windows', 'desktop': True})

subreddits = ["hardwareswap", "appleswap", "homelabsales", "LaptopDeals", "thinkpad"]

print("--- Testing Reddit Atom Feed Parsing ---")
for sub in subreddits:
    rss_url = f"https://www.reddit.com/r/{sub}/new/.rss"
    try:
        res = scraper.get(rss_url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}, timeout=5)
        if res.status_code == 200:
            entries = re.findall(r'<entry>([\s\S]*?)</entry>', res.text)
            print(f"r/{sub} has {len(entries)} entries:")
            for e in entries[:3]:
                title_m = re.search(r'<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>', e, re.DOTALL)
                link_m = re.search(r'<link[^>]*href="([^"]+)"', e)
                author_m = re.search(r'<author><name>/u/([^<]+)</name>', e)
                content_m = re.search(r'<content[^>]*>([\s\S]*?)</content>', e)
                
                title = html.unescape(title_m.group(1).strip()) if title_m else ""
                link = link_m.group(1) if link_m else ""
                author = author_m.group(1) if author_m else "reddit_user"
                
                print(f"  • Title: {title[:55]}...")
                print(f"    Direct URL: {link}")
                print(f"    Author: /u/{author}")
    except Exception as err:
        print(f"Error r/{sub}: {err}")
