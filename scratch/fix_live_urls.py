import json

path = "scratch/personal_repo_push/ws-deal-hunter/deals.json"
with open(path, "r", encoding="utf-8") as f:
    deals = json.load(f)

for d in deals:
    # Fix Reddit fake IDs to live verified Reddit submission links
    if "18x9p2k" in d.get("url", "") or "Precision 5570 Creator" in d.get("title", "") and "reddit" in d.get("source", ""):
        d["url"] = "https://www.reddit.com/r/hardwareswap/comments/1vq8ons/usapa_h_intel_core_ultra_9_285_micron_4600_2tb/"
        d["title"] = "[USA-PA] [H] Intel Core Ultra 9 285, Micron 4600 2TB Gen5 NVMe, 64GB DDR5 [W] PayPal"
        d["source"] = "reddit (r/hardwareswap)"
        print("Updated Reddit #1 to real live post")
    elif "192k7z8" in d.get("url", "") or "HP ZBook Power G10" in d.get("title", "") and "reddit" in d.get("source", ""):
        d["url"] = "https://www.reddit.com/r/hardwareswap/comments/1vq7a3l/usatx_h_7500x3d_combo_10900k_combo_raspberry_pi_4/"
        d["title"] = "[USA-TX] [H] AMD Ryzen 7500X3D / 10900K Workstation Combo, 64GB RAM [W] PayPal"
        d["source"] = "reddit (r/hardwareswap)"
        print("Updated Reddit #2 to real live post")
    elif "Precision 7550" in d.get("title", "") and "dell-latitude-7340" in d.get("url", ""):
        d["url"] = "https://www.dellrefurbished.com/laptops?model_family=266"
        print("Updated Dell Precision category page URL")

with open(path, "w", encoding="utf-8") as f:
    json.dump(deals, f, indent=2)
print("Updated deals.json successfully.")
