import sys
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import urllib.request
import urllib.parse
import re
import html
from bs4 import BeautifulSoup
import cloudscraper

scraper = cloudscraper.create_scraper(browser={"browser": "chrome", "platform": "windows", "desktop": True})

def test_url(url, label):
    print(f"\n==================== {label} ====================")
    try:
        res = scraper.get(url, timeout=6.0)
        print(f"Status Code: {res.status_code} | Length: {len(res.text)} bytes")
        return res.text
    except Exception as e:
        print(f"Error fetching {label}: {e}")
        return ""

# 1. Test Newegg Refurbished Workstations RSS / Search
newegg_html = test_url("https://slickdeals.net/newsearch.php?searchfirst=1&q=newegg+refurbished+thinkpad+precision+laptop&hideexpired=1&sort=newest&rss=1", "Newegg RSS Feed")
if newegg_html:
    items = re.findall(r"<item>([\s\S]*?)</item>", newegg_html)
    print(f"Newegg deal items found: {len(items)}")
    for i, it in enumerate(items[:3], 1):
        t = re.search(r"<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>", it)
        l = re.search(r"<link>(.*?)</link>", it)
        print(f" [{i}] Title: {t.group(1) if t else 'N/A'}")
        print(f"     Link:  {l.group(1) if l else 'N/A'}")

# 2. Test Micro Center Deals RSS / Search
mc_html = test_url("https://slickdeals.net/newsearch.php?searchfirst=1&q=micro+center+laptop+refurbished+macbook+thinkpad&hideexpired=1&sort=newest&rss=1", "Micro Center Deals Stream")
if mc_html:
    items = re.findall(r"<item>([\s\S]*?)</item>", mc_html)
    print(f"Micro Center deal items found: {len(items)}")
    for i, it in enumerate(items[:3], 1):
        t = re.search(r"<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>", it)
        l = re.search(r"<link>(.*?)</link>", it)
        print(f" [{i}] Title: {t.group(1) if t else 'N/A'}")
        print(f"     Link:  {l.group(1) if l else 'N/A'}")

# 3. Test Woot Tech Deals Direct Stream
woot_html = test_url("https://computers.woot.com/feed", "Woot Computers Direct RSS")
if woot_html:
    items = re.findall(r"<item>([\s\S]*?)</item>", woot_html)
    print(f"Woot Direct items found: {len(items)}")
    for i, it in enumerate(items[:3], 1):
        t = re.search(r"<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>", it)
        l = re.search(r"<link>(.*?)</link>", it)
        print(f" [{i}] Title: {t.group(1) if t else 'N/A'}")
        print(f"     Link:  {l.group(1) if l else 'N/A'}")

# 4. Test B&H Photo Video Deal Stream
bh_html = test_url("https://slickdeals.net/newsearch.php?searchfirst=1&q=b%26h+photo+workstation+laptop+macbook+pro&hideexpired=1&sort=newest&rss=1", "B&H Photo Workstations Stream")
if bh_html:
    items = re.findall(r"<item>([\s\S]*?)</item>", bh_html)
    print(f"B&H Photo deal items found: {len(items)}")
    for i, it in enumerate(items[:3], 1):
        t = re.search(r"<title>(?:<!\[CDATA\[)?(.*?)(?:\]\]>)?</title>", it)
        l = re.search(r"<link>(.*?)</link>", it)
        print(f" [{i}] Title: {t.group(1) if t else 'N/A'}")
        print(f"     Link:  {l.group(1) if l else 'N/A'}")
