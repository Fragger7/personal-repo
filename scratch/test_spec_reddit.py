import sys
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import re
import html
import cloudscraper
from bs4 import BeautifulSoup

scraper = cloudscraper.create_scraper(browser={"browser": "chrome", "platform": "windows", "desktop": True})

subreddits = [
    "hardwareswap",
    "appleswap",
    "homelabsales",
    "LaptopDeals",
    "thinkpad",
    "buildapcsales",
    "minipc",
]

# Spec-based matching patterns
CPU_PATTERNS = [
    r"i7[\s\-]*(?:1[1234][0-9]{3}[hH][xX]?|[0-9]{4,5}[hH][xX]?)",
    r"i9[\s\-]*(?:1[1234][0-9]{3}[hH][xX]?|[0-9]{4,5}[hH][xX]?)",
    r"ultra\s*[79]\b",
    r"ryzen[\s\-]*(?:7|9|pro)[\s\-]*(?:7[89][0-9]{2}[hH][sSxX]|8[89][0-9]{2}[hH][sSxX]|5[89][0-9]{2}[hH][sSxX]|6[89][0-9]{2}[hH][sSxX]|[0-9]{4}[xX]3[dD])",
    r"m[1-5]\s*(?:pro|max|ultra)\b",
    r"xeon\b",
    r"threadripper\b",
]

RAM_PATTERNS = [
    r"(?:32|64|96|128)\s*gb",
    r"2x\s*32gb",
    r"2x\s*16gb",
]

GPU_PATTERNS = [
    r"rtx\s*(?:4070|4080|4090|5070|5080|5090|3080|3070\s*ti|a2000|a3000|a4000|a4500|a5000|a5500|2000\s*ada|3500\s*ada|4000\s*ada|5000\s*ada)",
    r"quadro\s*(?:rtx|t2000|t1000|p[0-9]{4})",
    r"radeon\s*(?:7900|7800|6800)",
]

WORKSTATION_FAMILIES = [
    r"precision",
    r"thinkpad\s*p",
    r"p1\s*gen",
    r"p16",
    r"p15",
    r"p14s",
    r"zbook",
    r"xps\s*15",
    r"xps\s*17",
    r"macbook\s*pro",
    r"ms-01",
    r"ser[78]",
    r"um780",
    r"optiplex",
]

total_found = 0

for sub in subreddits:
    url = f"https://old.reddit.com/r/{sub}/new/"
    print(f"\nScanning r/{sub}...")
    try:
        res = scraper.get(url, timeout=5.0)
        if res.status_code != 200:
            print(f"  HTTP {res.status_code}")
            continue
        soup = BeautifulSoup(res.text, "html.parser")
        entries = soup.find_all("div", attrs={"data-fullname": True})
        print(f"  Entries found on page: {len(entries)}")
        for entry in entries:
            title_a = entry.find("a", class_=re.compile(r"\btitle\b"))
            if not title_a:
                continue
            title = title_a.text.strip()
            permalink = title_a.get("href", "")
            if permalink and not permalink.startswith("http"):
                permalink = f"https://www.reddit.com{permalink}"
            
            # Check spec-based match
            has_cpu = any(re.search(pat, title, re.I) for pat in CPU_PATTERNS)
            has_ram = any(re.search(pat, title, re.I) for pat in RAM_PATTERNS)
            has_gpu = any(re.search(pat, title, re.I) for pat in GPU_PATTERNS)
            has_ws = any(re.search(pat, title, re.I) for pat in WORKSTATION_FAMILIES)
            
            is_match = (has_cpu and has_ram) or (has_ws and (has_ram or has_cpu or has_gpu)) or (has_gpu and has_ram)
            
            if is_match:
                total_found += 1
                print(f"  🔥 MATCH [{sub}]: {title[:70]}")
                print(f"     URL: {permalink}")
    except Exception as e:
        print(f"  Error: {e}")

print(f"\nTotal Spec-Matched Live Listings across Subreddits: {total_found}")
