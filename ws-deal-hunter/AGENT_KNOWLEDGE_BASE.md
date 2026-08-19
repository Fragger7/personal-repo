# SYSTEM DIRECTIVE: ARBITRAGE VALUATION & SCRAPER EVALUATION ENGINE (v3.0)

**ROLE:** Core Context, Philosophy, Valuation Rules, and Domain Memory  
**INTENDED AUDIENCE:** Automated Scraping, Parsing, and Evaluation Coding Agents  
**PIPELINE TARGETS:** Swappa, eBay, Reddit (`/r/hardwareswap`, `/r/appleswap`, `/r/homelabsales`, `/r/LaptopDeals`), B&H Photo Used, Best Buy Outlet, Dell Financial Services Refurbished, Lenovo Outlet, Micro Center  
**PURPOSE:** Guide agent logic to eliminate noise, enforce strict hardware baselines, compute true landed cost economics, and prioritize high-conviction secondary market targets.

---

## 1. Executive Vision & Core Philosophy

### The Mission
The objective of this pipeline is **not** to aggregate every laptop for sale on the internet. The objective is to identify **mispriced, high-performance developer and workstation laptops** on secondary platforms that offer immediate utility for demanding engineering workflows (Docker, local LLM orchestration, parallel CLI agents) or significant cash arbitrage equity.

### The Signal-to-Noise Principle
A dashboard displaying 200+ listings a day is broken. When filters are too loose, "alert fatigue" sets in, forcing human operators to waste time triaging mediocre deals. 
* **Target Volume:** Ingestion and evaluation logic must be strict enough to filter market noise down to **5 to 15 high-conviction targets per day**.
* **The "Unicorn" Definition:** The term **Unicorn** must never be used casually. A true unicorn is a rare anomaly (e.g., an off-market or mispriced 64GB DDR5 current-gen workstation selling $\ge 38\%$ below fair market clearing prices). It should trigger once or twice a month, not twice an hour.

---

## 2. Evolution & Root-Cause Learnings: The Five Market Traps

Through empirical analysis of secondary market listings, several recurring traps generate high false-positive deal scores if an agent relies solely on seller titles and raw sticker prices. 

The evaluation logic explicitly counters these five failure modes:

### Trap 1: The 11th-Gen "i9" Marketing Trap
* **The Illusion:** Sellers list older 11th-Gen laptops (e.g., `i9-11950H`, `i7-11850H`) with "i9" or "64GB RAM" badges at $650–$750, making them look like powerhouse deals.
* **The Reality:** 11th-Gen Tiger Lake is an older 10nm monolithic architecture strictly capped at **8 cores / 16 threads** running on legacy **DDR4** memory. 
* **The Comparison:** Intel 12th/13th-Gen hybrid silicon (`i7-12700H`, `i7-13700H`, `i9-12900HK`) features **14 cores / 20 threads** on high-bandwidth **DDR5**. It delivers 40% to 50% higher multi-threaded throughput for compilation and agent tasks. An 11th-Gen laptop priced at $700 is overpriced; its realistic market value is under $500.
* **Rule:** Hard-blacklist all Intel CPUs older than 12th-Gen (`i[3579]-11\d{3}` and below).

### Trap 2: The Liquidator "Parts Lot" Trap (Negative Equity)
* **The Illusion:** Bulk enterprise liquidators list barebones machines for $389–$399 labeled *"NO BATT / NO SSD / NO O.S"*.
* **The Reality:** These are not bargains. Once an operator buys a replacement OEM 86Wh battery ($55–$65), a 1TB NVMe SSD ($65), a genuine 130W USB-C charger ($35–$40), and pays state sales tax, the **Total Landed Cost balloons to $550–$600**—for an 11th-Gen machine that sells turnkey for $460 on eBay.
* **Rule:** Never evaluate raw sticker prices. Calculate Total Landed Cost (TLC) including refurbishment penalties.

### Trap 3: The "Good Condition" Structural Damage Disguise
* **The Illusion:** A top-tier chassis (e.g., Dell XPS 15 9520) listed for $600 labeled condition *"Good"* by bulk resellers.
* **The Reality:** Deep in the condition notes, text reads: *"Frame is separating from device... chip on left corner of palm rest... screen has keyboard imprints."* On the XPS carbon-fiber/aluminum chassis, a separating frame means dropped impact sheared the internal metal hinge screw anchors from the palm rest. Opening and closing the lid will eventually tear internal display and daughterboard cables. A full teardown repair costs $150+ in parts and labor.
* **Rule:** Scan condition notes and descriptions for chassis deformation, hinge separation, and deep glass etching. Drop structurally compromised units immediately (Score 0.0).

### Trap 4: The Blown-dGPU Failure Trap
* **The Illusion:** Workstations or creator laptops (MSI Stealth/Creator GS66/Z16, Dell XPS 15) listed as *"Great working condition - Intel Iris Xe graphics only"*.
* **The Reality:** These models were manufactured with dedicated NVIDIA graphics (RTX 3060/3070/4060/A2000). If the listing notes "Iris Xe only", the dedicated GPU has suffered thermal solder failure or VRM blowout. The seller has disabled the dead dGPU in Device Manager/BIOS.
* **Rule:** If a workstation or gaming chassis known to have a dGPU is listed with only integrated graphics or mentions "dGPU not working / code 43", drop immediately (Score 0.0).

### Trap 5: The Cut-Down CPU & Soldered RAM Trap
* **The Illusion:** Thin-and-light laptops listed as "Intel Core i7 13th-Gen" or "AMD Ryzen 7" for $500.
* **The Reality:** Manufacturers use deceiving model numbers. An `i7-13620H` or `i7-12650H` has half its E-cores and cache cut off compared to a full `i7-13700H` (14C/20T). U-series (`1355U`, `1235U`) and P-series (`1260P`, `1360P`) have 15W–28W limits that thermal-throttle instantly under local LLM inference. Worse, soldered 16GB RAM can never be upgraded.
* **Rule:** Enforce full H/HX-series silicon and require $\ge 32\text{GB}$ RAM (or verified dual SO-DIMM upgradable slots).

---

## 3. Target Hardware Taxonomy & Silicon Gatekeeper

### Target Machine Tiers
* **Tier 1: Clean-Deck Creator & Enterprise Workstations**:
  - Dell Precision 5570, 5680, 7670, 7680, 7770, 7780
  - Dell XPS 15 (9520, 9530), XPS 17 (9720, 9730)
  - Lenovo ThinkPad P1 (Gen 5, Gen 6), ThinkPad P16 (Gen 1, Gen 2), ThinkPad X1 Extreme (Gen 5)
  - HP ZBook Studio (G9, G10), ZBook Fury (G9, G10), ZBook Power (G9, G10)
* **Tier 2: Apple Silicon Unix Engines**:
  - Apple MacBook Pro 14" & 16" (M1 Pro/Max, M2 Pro/Max, M3 Pro/Max, M4 Pro/Max) with $\ge 32\text{GB}$ Unified Memory
* **Tier 3: Stealth Creator & High-TGP CUDA Engines**:
  - ASUS ROG Zephyrus G14 (GA402/GA403 Zen 4/Zen 5), Zephyrus G16 (GU605 / GU604), Zephyrus M16 (GU604)
  - Lenovo Legion Pro 7i (Gen 8/9/10), Legion Pro 5i
  - Razer Blade 16 (2023–2025)

---

### Silicon Gatekeeper Rules

| Status | Processors |
| :--- | :--- |
| **✅ Whitelist (Allowed)** | • **Intel 12th/13th-Gen H/HX**: `i7-12700H`, `i7-12800H`, `i9-12900H/HX`, `i7-13700H`, `i7-13800H`, `i9-13900H/HX` (14C/20T+)<br>• **Intel Core Ultra 7 / 9** (Series 1 & 2)<br>• **AMD Zen 4/5**: `Ryzen 7 7840HS`, `8840HS`, `Ryzen 9 7940HS`, `Ryzen AI 9 HX 370`<br>• **Apple Silicon**: `M1 Pro/Max`, `M2 Pro/Max`, `M3 Pro/Max`, `M4 Pro/Max` |
| **❌ Hard Blacklist (Score 0.0)** | • **All Intel 11th-Gen & Older** (`i7-11850H`, `i9-11950H`, 10th, 9th, 8th Gen)<br>• **Intel P-Series & U-Series** (`1260P`, `1360P`, `1370P`, `1355U`, `1235U`)<br>• **Cut-down Intel H-Dies** (`i7-13620H`, `i7-12650H`, `i5-13500H`)<br>• **AMD Zen 2 / Zen 3 / Rebrands** (`5000`, `6000`, `7020`, `7030`, `7035`)<br>• **Base Apple Silicon** (Base M1/M2/M3 with 8-core / 8GB-16GB RAM) |

---

## 4. Valuation, Total Landed Cost (TLC), & Ground Truth Economics

### The Total Landed Cost (TLC) Formula
$$\text{TLC} = \text{Sticker Price} + \text{Shipping} + \text{Estimated Sales Tax} + \text{Mandatory Refurbishment Penalties}$$
* **Tax Rate:** Apply **8.25%** for online storefronts (eBay, Swappa, Best Buy, B&H); apply **0%** for verified local cash/in-person transactions (Reddit local).
* **Mandatory Penalties:**
  * If SSD $\le 256\text{GB}$: Add **+$65.00** (cost of purchasing a 1TB Gen4 NVMe drive).
  * If Missing OEM Charger: Add **+$40.00** (cost of a genuine 130W/140W USB-C brick).
  * If Missing / Dead Battery: Add **+$65.00** (cost of an OEM 86Wh+ internal cell).
  * If RAM == 16GB (on dual SO-DIMM upgradable chassis): Add **+$110.00** (cost of 64GB DDR5 SO-DIMM kit).
  * If RAM $\le 16\text{GB}$ and soldered / non-upgradable: **Reject (Score 0.0)**.

---

### Fair Market Value (FMV) Ground Truth Table

| Model / Generation | Architecture | Realistic 32GB FMV | Realistic 64GB FMV | 🎯 Instant Strike Ceiling (TLC) |
| :--- | :--- | :--- | :--- | :--- |
| **Dell XPS 15 9520** | Intel 12th-Gen (14C) | $750.00 | $850.00 | **$\le \$675.00$ (32GB) / $\le \$750.00$ (64GB)** |
| **Dell XPS 15 9530** | Intel 13th-Gen (14C) | $950.00 | $1,150.00 | **$\le \$780.00$ (32GB) / $\le \$850.00$ (64GB)** |
| **Dell Precision 5570** | Intel 12th-Gen (14C) | $780.00 | $880.00 | **$\le \$680.00$ (32GB) / $\le \$750.00$ (64GB)** |
| **Dell Precision 5680** | Intel 13th-Gen (14C) | $1,250.00 | $1,450.00 | **$\le \$950.00$ (32GB) / $\le \$1,050.00$ (64GB)** |
| **Lenovo ThinkPad P1 Gen 5** | Intel 12th-Gen (14C) | $800.00 | $920.00 | **$\le \$720.00$ (32GB) / $\le \$800.00$ (64GB)** |
| **Lenovo ThinkPad P1 Gen 6** | Intel 13th-Gen (14C) | $1,200.00 | $1,400.00 | **$\le \$950.00$ (32GB) / $\le \$1,050.00$ (64GB)** |
| **Lenovo ThinkPad X1 Extreme G5** | Intel 12th-Gen (14C) | $800.00 | $920.00 | **$\le \$720.00$ (32GB) / $\le \$780.00$ (64GB)** |
| **HP ZBook Studio G9** | Intel 12th-Gen (14C) | $750.00 | $850.00 | **$\le \$650.00$ (32GB) / $\le \$720.00$ (64GB)** |
| **HP ZBook Studio G10** | Intel 13th-Gen (14C) | $1,050.00 | $1,200.00 | **$\le \$850.00$ (32GB) / $\le \$950.00$ (64GB)** |
| **Apple MacBook Pro 16" (2021 M1)**| M1 Pro / M1 Max | $1,050.00 | $1,250.00 | **$\le \$900.00$ (32GB) / $\le \$1,100.00$ (64GB)** |
| **Apple MacBook Pro 16" (2023 M2)**| M2 Pro / M2 Max | $1,350.00 | $1,550.00 | **$\le \$1,150.00$ (32GB) / $\le \$1,350.00$ (64GB)** |
| **ASUS Zephyrus M16 (GU604)** | Intel 13th-Gen + 4070 | $950.00 | $1,100.00 | **$\le \$800.00$ (32GB) / $\le \$900.00$ (64GB)** |

---

## 5. The Calibrated 4-Tier Arbitrage Scoring Framework

$$\text{Margin Spread (\%)} = \frac{\text{FMV} - \text{Total Landed Cost}}{\text{FMV}} \times 100$$

* **🦄 9.8 – 10.0 TRUE UNICORN DEAL**:
  - Margin Spread $\ge 38.0\%$ below FMV
  - Memory: 64GB DDR5 or 64GB Unified Memory
  - Platform: Tier 1 Chassis (9530, 5680, P1 G6, M1/M2 Max)
  - Condition: Mint / Very Good (Zero structural defects)
* **🎯 9.0 – 9.7 HIGH-CONVICTION STRIKE**:
  - Margin Spread: $25.0\% - 37.9\%$
  - Memory: $\ge 32\text{GB}$ DDR5 / Unified
  - Clean turnkey hardware below strict strike ceiling
* **⚡ 8.0 – 8.9 STRONG VALUE BUY**:
  - Margin Spread: $15.0\% - 24.9\%$
  - Solid hardware specs, fair market discount
* **🤝 7.0 – 7.9 OPPORTUNISTIC OFFER TARGET**:
  - Margin Spread: $8.0\% - 14.9\%$
  - Listed near market; viable for a lowball "Best Offer"
* **❌ 0.0 – 6.9 PASS / NO ARBITRAGE (Do Not Alert Dashboard)**:
  - Margin Spread $< 8.0\%$ OR TLC exceeds Strike Ceiling OR fails Silicon/Hardware gatekeeper.