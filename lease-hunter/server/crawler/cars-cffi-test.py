from curl_cffi import requests
import json

url = "https://www.cars.com/shopping/results/?stock_type=new&makes[]=kia&models[]=kia-ev9&zip=78665&maximum_distance=50&years_min[]=2025"

headers = {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
    "Upgrade-Insecure-Requests": "1"
}

try:
    print("Fetching Cars.com with curl_cffi...")
    r = requests.get(url, headers=headers, impersonate="chrome110", timeout=30)
    print(f"Status Code: {r.status_code}")
    
    html = r.text
    if "vehicle-card" in html or "cars-vehicle-card" in html or "PRELOADED_STATE" in html:
        print("Success! The page contains vehicle cards or preloaded state.")
        # Find the script tag containing the state
        for line in html.split('\n'):
            if "window.__PRELOADED_STATE__" in line or "INITIAL_STATE" in line:
                print("Found state script tag!")
                print(line[:500])
                break
    else:
        print("Blocked or no cards found.")
        print("Snippet:", html[:500])
        if "cloudflare" in html.lower() or "challenge" in html.lower():
            print("Detected Cloudflare Challenge.")
            
except Exception as e:
    print(f"Error: {e}")
