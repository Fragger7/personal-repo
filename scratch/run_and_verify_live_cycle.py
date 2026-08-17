import sys
sys.path.insert(0, "scratch/personal_repo_push/ws-deal-hunter")

from daemon import DealHunterDaemon
from storage import AtomicDealStorage

daemon = DealHunterDaemon(storage_path="scratch/personal_repo_push/ws-deal-hunter/deals.json", auto_push=False)
result = daemon.run_cycle()

print(f"\nCycle Result: {result}")

storage = AtomicDealStorage("scratch/personal_repo_push/ws-deal-hunter/deals.json")
deals = storage.get_all()
print(f"\nTotal Deals in deals.json: {len(deals)}")
for idx, d in enumerate(deals, 1):
    print(f"[{idx:2d}] [{d.source:25s}] ${d.price:6.1f} | Score: {d.deal_score:4.1f} | {d.title[:45]}...")
    print(f"     URL: {d.url}")
