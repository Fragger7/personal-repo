import sys
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

sys.path.insert(0, "scratch/personal_repo_push/ws-deal-hunter")

from daemon import DealHunterDaemon
from storage import AtomicDealStorage

storage = AtomicDealStorage("scratch/personal_repo_push/ws-deal-hunter/deals.json")
daemon = DealHunterDaemon(storage_path="scratch/personal_repo_push/ws-deal-hunter/deals.json", auto_push=False)

print("Starting live daemon scan across all 8 enterprise endpoints...")
result = daemon.run_cycle()
print(f"\nCycle Execution Summary: {result}")

deals = storage.get_all()
print(f"\nTotal Active High-Quality Deals in deals.json: {len(deals)}")
for idx, d in enumerate(deals, 1):
    print(f"[{idx:2d}] [{d.source:18s}] ${d.price:6.1f} | Score: {d.deal_score:4.1f} | {d.title[:50]}...")
    print(f"     Direct Link: {d.url}")
