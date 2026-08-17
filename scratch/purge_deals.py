import sys
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import json
import re

path = "scratch/personal_repo_push/ws-deal-hunter/deals.json"
with open(path, "r", encoding="utf-8") as f:
    deals = json.load(f)

print(f"Original total deals: {len(deals)}")

clean_deals = []
for d in deals:
    title = d.get("title", "")
    score = d.get("deal_score", 0.0)
    summary = d.get("summary", "")
    rec = d.get("actionable_recommendation", "")
    source = d.get("source", "")
    
    # Exclude hard-rejected / low scores
    if score < 6.0 or "hard excluded" in summary.lower() or "reject" in rec.lower():
        print(f"  [PURGED] (Score {score:3.1f}): {title[:60]}")
        continue
        
    # Exclude Latitude 3000/5000 budget models
    if re.search(r"latitude\s*(?:3|5|33|34|35|54|55)", title, re.I) and not re.search(r"precision|xps", title, re.I):
        print(f"  [PURGED] (Budget Latitude): {title[:60]}")
        continue
        
    # Exclude Yoga / IdeaPad / Pavilion
    if re.search(r"yoga|ideapad|pavilion|omnibook|chromebook", title, re.I) and not re.search(r"thinkpad\s*p|p1\b|p16|zbook", title, re.I):
        print(f"  [PURGED] (Consumer 2in1): {title[:60]}")
        continue

    clean_deals.append(d)
    print(f"  [KEPT] (Score {score:3.1f} | ${d.get('price',0):.0f}): [{source:16s}] {title[:55]}")

print(f"\nFinal Clean Workstation Inventory Count: {len(clean_deals)}")

with open(path, "w", encoding="utf-8") as f:
    json.dump(clean_deals, f, indent=2)

print("Saved clean deals.json successfully.")
