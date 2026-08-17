import sys
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import re
import html
import cloudscraper

scraper = cloudscraper.create_scraper(browser={"browser": "chrome", "platform": "windows", "desktop": True})

def test_stream(name, url, keywords):
    print(f"\n--- Testing {name} ---")
    try:
        res = scraper.get(url, timeout=5.0)
        if res.status_code != 200:
            print(f"Status {res.status_code}")
            return []
        items = re.findall(r"<item>([\s\S]*?)</item>", res.text)
        print(f"Total raw items: {len(items)}")
        matches = []
        for it in items:
            t_m = re.search(r"<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>", it)
            l_m = re.search(r"<link>(.*?)</link>", it)
            if not t_m or not l_m:
                continue
            title = html.unescape(t_m.group(1).strip())
            link = html.unescape(l_m.group(1).strip())
            
            # Check keywords
            if any(re.search(kw, title, re.I) for kw in keywords):
                price_m = re.search(r"\$\s*([0-9,]+(?:\.[0-9]{2})?)", title)
                price = float(price_m.group(1).replace(",", "")) if price_m else 0.0
                matches.append((title, price, link))
                print(f"  [MATCH] ${price:6.1f} | {title[:65]}")
                print(f"          URL: {link}")
        return matches
    except Exception as e:
        print(f"Error: {e}")
        return []

# B&H Photo Workstation Streams
bh_matches = test_stream(
    "B&H Photo Workstations Stream",
    "https://slickdeals.net/newsearch.php?searchfirst=1&q=b%26h+photo+macbook+pro+workstation+thinkpad&hideexpired=1&sort=newest&rss=1",
    [r"macbook\s*pro", r"m[1-5]\s*(?:pro|max)", r"thinkpad", r"precision", r"zbook", r"48gb", r"32gb", r"64gb"]
)

# Micro Center Streams
mc_matches = test_stream(
    "Micro Center Workstations Stream",
    "https://slickdeals.net/newsearch.php?searchfirst=1&q=micro+center+laptop+macbook+thinkpad+ryzen&hideexpired=1&sort=newest&rss=1",
    [r"macbook", r"thinkpad", r"ryzen", r"core\s*ultra", r"rtx", r"32gb", r"64gb", r"alienware", r"predator"]
)

# Newegg Streams
newegg_matches = test_stream(
    "Newegg Refurbished Workstations Stream",
    "https://slickdeals.net/newsearch.php?searchfirst=1&q=newegg+refurbished+thinkpad+precision+laptop+32gb&hideexpired=1&sort=newest&rss=1",
    [r"thinkpad", r"precision", r"zbook", r"32gb", r"64gb", r"rtx"]
)
