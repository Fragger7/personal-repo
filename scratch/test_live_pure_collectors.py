import sys
sys.path.insert(0, "scratch/personal_repo_push/ws-deal-hunter")

from collector import (
    DellRefurbishedCollector,
    EBayCollector,
    LenovoOutletCollector,
    RedditCollector,
    SwappaCollector,
)

print("--- Testing Live Dell Refurbished ---")
dell = DellRefurbishedCollector()
dell_items = [item for item in dell.fetch_listings() if not item.id.startswith("dell_refurb_precision_5570_dfs")]
print(f"Dell Refurbished live count: {len(dell_items)}")
for i, d in enumerate(dell_items[:5], 1):
    print(f"  {i}. ${d.price:6.1f} | {d.title[:60]}")
    print(f"     URL: {d.url}")

print("\n--- Testing Live Reddit Hub ---")
reddit = RedditCollector()
reddit_items = [item for item in reddit.fetch_listings() if not item.id.startswith("reddit_hws_")]
print(f"Reddit live count: {len(reddit_items)}")
for i, d in enumerate(reddit_items[:5], 1):
    print(f"  {i}. ${d.price:6.1f} | {d.title[:60]}")
    print(f"     URL: {d.url}")

print("\n--- Testing Live Syndicated Tech Streams ---")
swappa = SwappaCollector()
syndicated_items = [item for item in swappa.fetch_listings() if not item.id.startswith("seed_")]
print(f"Syndicated live count: {len(syndicated_items)}")
for i, d in enumerate(syndicated_items[:5], 1):
    print(f"  {i}. ${d.price:6.1f} | {d.title[:60]}")
    print(f"     URL: {d.url}")

print("\n--- Testing Live Lenovo Outlet Deals ---")
lenovo = LenovoOutletCollector()
lenovo_items = [item for item in lenovo.fetch_listings() if not item.id.startswith("lenovo_outlet_thinkpad_")]
print(f"Lenovo Outlet live count: {lenovo_items}")
