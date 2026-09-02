================================================================================================ DOCUMENT: SCRAPER_AGENT_KNOWLEDGE_BASE.md ROLE: Core Context, Philosophy, Valuation Rules, and Domain Memory
INTENDED AUDIENCE: Automated Scraping, Parsing, and Evaluation Coding Agents PIPELINE TARGETS: Swappa, eBay, Reddit (/r/hardwareswap), Local Marketplaces PURPOSE: Guide agent logic to eliminate noise, enforce strict hardware baselines, compute true landed
cost economics, and prioritize high-conviction secondary market targets.
d
arsid13858247
---
## 1. Executive Vision & Core Philosophy
### The Mission
The objective of this pipeline is **not** to aggregate every laptop for sale on the internet. The objective is to identify **mispriced, high-performance developer and workstation laptops** on secondary platforms that offer immediate utility for demanding
engineering workflows (Docker, local LLM orchestration, parallel CLI agents) or significant cash arbitrage equity.
### The Signal-to-Noise Principle
A dashboard displaying 200+ listings a day is broken. When filters are too loose, "alert fatigue" sets in, forcing human operators to waste time triaging mediocre deals.
* **Target Volume:** Ingestion and evaluation logic must be strict enough to filter market noise down to **5 to 12 high-conviction targets per day**.
* **The "Unicorn" Definition:** The term **Unicorn** must never be used casually. A true unicorn is a rare anomaly (e.g., an off-market or mispriced 64GB DDR5 current-gen workstation selling $\38\\
%$ below fair market clearing prices). It should trigger once or twice a month, not twice an hour.
---
## 2. Evolution & Root-Cause Learnings: The Five Market Traps
Through empirical analysis of secondary market listings, several recurring traps generate high false-positive deal scores if an agent relies solely on seller titles and raw sticker prices.
The evaluation logic must explicitly counter these five failure modes:
### Trap 1: The 11th-Gen "i9" Marketing Trap
* **The Illusion:** Sellers list older 11th-Gen laptops (e.g., `i9-11950H`, `i7-11850H`) with "i9" or "64GB RAM" badges at $650– $750, making them look like powerhouse deals.
* **The Reality:** 11th-Gen Tiger Lake is an older 10nm monolithic architecture strictly capped at **8 cores / 16 threads** running on legacy **DDR4** memory.
* **The Comparison:** Intel 12th/13th-Gen hybrid silicon
(`i7-12700H`, `i7-13700H`, `i9-12900HK`) features **14 cores / 20 threads** on high-bandwidth **DDR5**. It delivers 40% to 50% higher multi-threaded throughput for compilation and agent tasks. An 11th-Gen laptop priced at $700 is overpriced; its realistic
market value is under $500.
* **Rule:** Hard-blacklist all Intel CPUs older than 12th-Gen (`i[3579]-11\\3\` and below).
### Trap 2: The Liquidator "Parts Lot" Trap (Negative Equity)
* **The Illusion:** Bulk enterprise liquidators (e.g., EPC) list barebones machines for $389– $399 labeled *"NO BATT / NO SSD / NO O.S"*.
* **The Reality:** These are not bargains. Once an operator buys a replacement OEM 86Wh battery ($55– $65), a 1TB NVMe SSD ($65), a genuine 130W USB-C charger ($35– $40), and pays state sales tax, the **Total Landed Cost balloons to $550
– $600**— for an 11th-Gen machine that sells turnkey for $460 on eBay.
* **Rule:** Never evaluate raw sticker prices. Hard-reject incomplete listings missing core components unless factored into the landed cost penalty engine.
### Trap 3: The "Good Condition" Structural Damage Disguise
* **The Illusion:** A top-tier chassis (e.g., Dell XPS 15 9520) listed for $600 labeled condition *"Good"* by bulk resellers.
* **The Reality:** Deep in the condition notes, text reads: *"Frame is separating from device... chip on left corner of palm rest... screen has keyboard imprints."* On the XPS carbon-fiber/aluminum chassis, a separating frame means dropped
impact sheared the internal metal hinge screw anchors from the palm rest. Opening and closing the lid will eventually tear internal display and daughterboard cables. A full teardown repair costs $150+ in parts and labor.
* **Rule:** Scan condition notes and descriptions for chassis deformation, hinge separation, and deep glass etching. Drop structurally compromised units immediately.
### Trap 4: The Blown-dGPU Failure Trap
* **The Illusion:** Workstations or creator laptops (MSI Stealth/Creator GS66/Z16, Dell XPS 15) listed as *"Great working condition - Intel Iris Xe graphics only"*.
* **The Reality:** These models were manufactured with dedicated NVIDIA RTX GPUs. A listing stating "Intel Iris only" or "No Nvidia" indicates the discrete GPU died from therm
al fatigue or a power-rail short, and the seller disabled it in BIOS or Device Manager to offload a defective motherboard.
* **Rule:** If a chassis was designed for a discrete GPU, any listing claiming integrated-only graphics or "bad/disabled GPU" is a broken machine.
### Trap 5: The Cut-Down CPU & Soldered RAM Trap
* **The Illusion:** Brand-new retail gaming laptops (e.g., Gigabyte A16 at $1,249 with an RTX 5070) or sleek ultrabooks (e.g., 2024 Zephyrus G16 GU605) looking like great multi-use machines.
* **The Reality:**
* Chips like the `i7-13620H` or `i7-12650H` are cut-down dies with fewer E-cores (10 cores total vs. 14 cores on standard i7s) and smaller caches.
* Modern ultrabooks (XPS 16 9640, Zephyrus G16 2024, Galaxy Book Ultra, LG Gram) frequently feature **100% soldered LPDDR5X RAM**. A 16GB soldered unit can never be upgraded to 32GB or 64GB.
* **Rule:** Enforce a strict **32GB RAM minimum floor** and restrict CPU models to verified 14-core+ H/HX series, Zen 4 HS series, or Apple Pro/Max silicon.

### Trap 6: The 13"–14" Compact Display Distraction
* **The Illusion:** Sleek 14" machines (MacBook Pro 14", Razer Blade 14, Zephyrus G14, ThinkPad P14s, Precision 5470) appear at $500–$700.
* **The Reality:** For developer workstation ergonomics, 14" screens lack the visual real-estate for multi-window IDEs, terminal splits, and Docker tooling without external monitors.
* **Rule:** Hard-reject all laptops with screen size < 15.0" (strictly require 15"–16"+ display; Mini-PCs exempted).

### Trap 7: The Legacy Model Disguise
* **The Illusion:** Sellers title older laptops as "XPS 15" or "Precision 15" with 32GB RAM at attractive prices ($320–$450).
* **The Reality:** Older generations (XPS 9560/7590, Precision 5510–5560, Precision 7510–7550, ThinkPad P1 Gen 1–4, ThinkPad P15s Gen 1–2) run on 7th–11th Gen quad/octa-core silicon.
* **Rule:** Hard-blacklist all legacy model series numbers: `9560`, `9570`, `7590`, `9500`, `9510`, `5510-5560`, `7510-7560`, `p1 gen 1-4`, `p15s gen 1-2`.

### Trap 8: The Keyboard Defect & Blown-Hardware Unit
* **The Illusion:** Enterprise workstations listed as "Good" or "Used" at a $100 discount.
* **The Reality:** Descriptions state *"keyboard issue... 2 keys not working... bad keyboard"*. Workstation keyboard replacement requires complete motherboard teardown.
* **Rule:** Hard-reject any listing mentioning keyboard defects or broken keys.

---
## 3. Target Hardware Taxonomy & Silicon Gatekeeper
### Target Machine Tiers
#### Tier 1: Clean-Deck Creator & Enterprise Workstations
*Dual SO-DIMM DDR5, 14-Core H/HX CPUs, 16:10 Displays, 64GB Headroom, CNC Aluminum / Carbon Fiber.*
* **Dell:** XPS 15 (9520, 9530), Precision 5570, Precision 5680.
* **Lenovo:** ThinkPad P1 (Gen 5, Gen 6), ThinkPad X1 Extreme Gen 5.
* **HP:** ZBook Studio (G9, G10).
#### Tier 2: Apple Silicon Unix Engines
*High-Bandwidth Unified Memory, Class-Leading Power Efficiency, Native Unix CLI.*
* **Apple:** MacBook Pro 16" (2021 M1 Pro/Max, 2023 M2 Pro/Max) with 32GB or 64GB Unified RAM.
#### Tier 3: Stealth Creator & High-TGP CUDA Engines
*Dual SO-DIMM, High Total Graphics Power for local ML and GPU pipelines, Understated Aesthetics.*
* **ASUS:** ROG Zephyrus M16 (GU604 - 2023 dual SO-DIMM edition), ProArt Studiobook 16 (H7604).
* **Razer / Gigabyte / MSI:** Razer Blade 16 (2023, RZ09-0483), Gigabyte AERO 16 OLED (2023 BSF), MSI Creator Z16P (A13U).
* **Framework:** Framework Laptop 16 (Modular Ryzen 7040/8040).
#### Tier 4: Enterprise Liquidator Sleeper Targets
*Off-lease fleet machines frequently misidentified or underpriced by generic asset recyclers.*
* **Lenovo:** ThinkPad P16s / P16v (Gen 1, Gen 2 with AMD Ryzen 7 PRO 7840HS or Intel 13th-Gen).
* **HP:** ZBook Power (G9, G10).
* **Dell:** Precision 7670 / 7680 (Thin Chassis edition).
---
### Silicon Gatekeeper Rules
d
arsid13858247 \'2b\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2b
\'a6 SILICON GATEKEEPER \'a6 \'2b\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'a6 \'a6 \'3f
ALLOWED SILICON (Whitelist Only): \'a6 \'a6  Intel 12th-Gen H/HX: i7-12700H, i7-12800H, i9-12900H/HK/HX (14C/20T+) \'a6 \'a6  Intel 13th-Gen H/HX: i7-13700H, i7-13800H, i9-13900H/HK/HX (14C/20T+) \'a6
\'a6  AMD Zen 4: Ryzen 7 7840HS, 7840U, 8840HS, Ryzen 9 7940HS (8C/16T) \'a6 \'a6  Apple Silicon: M1 Pro, M1 Max, M2 Pro, M2 Max \'a6 \'a6 \'a6 \'a6
\'3f\'3f DISQUALIFIED SILICON (Hard Blacklist): \'a6 \'a6
All Intel 11th-Gen & Older: i[3579]-11xxx, 10xxx, 9xxx, 8xxx \'a6 \'a6  Intel P-Series & U-Series: 1260P, 1360P, 1370P, 1355U, 1235U \'a6 \'a6  Cut-down Intel H-Dies: i7-13620H, i7-12650H, i5-13500H \'a6
\'a6  AMD Zen 2 / Zen 3 / Legacy Rebrands: 5000, 6000, 7020, 7030, 7035 \'a6 \'a6  Base Apple Silicon: Base M1, M2, M3 (8-core / 8GB-16GB non-Pro/Max) \'a6 \'2b\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2b
d
arsid13858247
---
## 4. Valuation, Total Landed Cost (TLC), & Ground Truth Economics
### The Total Landed Cost (TLC) Formula
Never compare the raw sticker price to market value. The true out-of-pocket acquisition cost is computed as:
$$\\TLC\ = \\Sticker Price\ + \\Shipping\ + \\Estimated Sales Tax\ + \\Mandatory Refurbishment Penalties\$$
* **Tax Rate:** Apply **8.25%** for online platforms (eBay, Swappa, retail); apply **0%** for verified local cash/in-person transactions.
* **Mandatory Penalties:**
* If SSD $\256\\GB\$: Add **+$65.00** (cost of purchasing a 1TB Gen4 NVMe drive).
* If Missing OEM Charger: Add **+$40.00** (cost of a genuine 130W/140W USB-C brick).
* If Missing / Dead Battery: Add **+$65.00** (cost of an OEM 86Wh+ internal cell).
---
### Fair Market Value (FMV) Ground Truth Table
Use these empirical market clearing prices (verified completed sales on eBay and Swappa) to compute arbitrage margins:
| Model / Generation | Architecture | Realistic 32GB FMV | Realistic 64GB FMV | \'3f\'3f
Instant Strike Ceiling (TLC) |
| :--- | :--- | :--- | :--- | :--- |
| **Dell XPS 15 9520** | Intel 12th-Gen (14C) | $750.00 | $850.00 | **$\\\$675.00$ (32GB) / $\\\$750.00$ (64GB)** |
| **Dell XPS 15 9530** | Intel 13th-Gen (14C) | $950.00 | $1,150.00 | **$\\\$780.00$ (32GB) / $\\\$850.00$ (64GB)** |
| **Dell Precision 5570** | Intel 12th-Gen (14C) | $780.00 | $880.00 | **$\\\$680.00$ (32GB) / $\\\$750.00$ (64GB)** |
| **Dell Precision 5680** | Intel 13th-Gen (14C) | $1,250.00 | $1,450.00 | **$\\\$950.00$ (32GB) / $\\\$1,050.00$ (64GB)** |
| **Lenovo ThinkPad P1 Gen 5** | Intel 12th-Gen (14C) | $800.00 | $920.00 | **$\\\$720.00$ (32GB) / $\\\$800.00$ (64GB)** |
| **Lenovo ThinkPad P1 Gen 6** | Intel 13th-Gen (14C) | $1,200.00 | $1,400.00 | **$\\\$950.00$ (32GB) / $\\\$1,050.00$ (64GB)** |
| **Lenovo ThinkPad X1 Extreme G5** | Intel 12th-Gen (14C) | $800.00 | $920.00 | **$\\\$720.00$ (32GB) / $\\\$780.00$ (64GB)** |
| **HP ZBook Studio G9** | Intel 12th-Gen (14C) | $750.00 | $850.00 | **$\\\$650.00$ (32GB) / $\\\$720.00$ (64GB)** |
| **HP ZBook Studio G10** | Intel 13th-Gen (14C) | $1,050.00 | $1,200.00 | **$\\\$850.00$ (32GB) / $\\\$950.00$ (64GB)** |
| **Apple MacBook Pro 16" (2021 M1)**| M1 Pro / M1 Max | $1,050.00 | $1,250.00 | **$\\\$900.00$ (32GB) / $\\\$1,100.00$ (64GB)** |
| **Apple MacBook Pro 16" (2023 M2)**| M2 Pro / M2 Max | $1,350.00 | $1,550.00 | **$\\\$1,150.00$ (32GB) / $\\\$1,350.00$ (64GB)** |
| **ASUS Zephyrus M16 (GU604)** | Intel 13th-Gen + 4070 | $950.00 | $1,100.00 | **$\\\$800.00$ (32GB) / $\\\$900.00$ (64GB)** |
---
## 5. The Calibrated 4-Tier Arbitrage Scoring Framework
Margin spread is computed relative to true market value:
$$\\Margin Spread (\\%)\ = \\\\FMV\ - \\Total Landed Cost\\\\\FMV\\ \100$$
d
arsid13858247 \'2b\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2b
\'a6 ARBITRAGE SCORING CURVE SPECIFICATION \'a6 \'2b\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'a6 \'a6 \'3f\'3f
9.8 –  10.0 TRUE UNICORN DEAL \'a6 \'a6  Margin Spread \'3d 38.0% below FMV \'a6 \'a6  Memory: 64GB DDR5 or 64GB Unified Memory \'a6 \'a6
Platform: Tier 1 Chassis (9530, 5680, P1 G6, M1/M2 Max) \'a6 \'a6  Condition: Mint / Very Good (Zero structural defects) \'a6 \'a6 \'a6 \'a6
\'3f\'3f 9.0 –  9.7 HIGH-CONVICTION STRIKE \'a6 \'a6  Margin Spread = 25.0% to 37.9%
\'a6 \'a6  Memory: \'3d 32GB DDR5 / Unified \'a6 \'a6  Clean turnkey hardware below strict strike ceiling \'a6 \'a6 \'a6 \'a6
\'3f\'3f 8.0 –  8.9 STRONG VALUE BUY \'a6 \'a6  Margin Spread = 15.0% to 24.9% \'a6
\'a6  Solid hardware specs, fair market discount \'a6 \'a6 \'a6 \'a6 \'3f\'3f
7.0 –  7.9 OPPORTUNISTIC OFFER TARGET \'a6 \'a6  Margin Spread = 8.0% to 14.9% \'a6 \'a6  Listed near market; viable for a lowball "Best Offer" \'a6 \'a6
\'a6 \'a6 \'3f 0.0 –
6.9 PASS / NO ARBITRAGE (Do Not Alert Dashboard) \'a6 \'a6  Margin Spread < 8.0% OR TLC exceeds Strike Ceiling \'a6 \'2b\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2b
d
arsid13858247
---
## 6. Operational Case Studies (The Scraper's Memory Bank)
The following real-world evaluations illustrate the nuances required in parsing and scoring:
### Case Study 1: The Benchmark Unicorn (Dell XPS 15 9530)
* **Listing:** Dell XPS 15 9530, 13th-Gen Intel i7-13700H, 64GB Crucial DDR5, 1TB NVMe, RTX 4050, Mint Condition.
* **Pricing:** Acquired at **$665 sticker + tax = $721 all-in**.
* **Analysis:** FMV for a 64GB 13th-Gen XPS 15 is ~$1,150– $1,200. Margin spread is **~38% below market**. High-end 64GB memory installed, zero refurbishment needed, current-gen silicon.
* **Score:** **9.8 / 10 (True Unicorn)**.
### Case Study 2: The Structural Trap Disguised as a Buy (Dell XPS 15 9520)
* **Listing:** Dell XPS 15 9520, i7-12700H, 32GB RAM, 1TB SSD, RTX 3050, Condition "Good", Price: $600 Buy It Now.
* **Flaw:** Deep description revealed: *"Frame on this device is bent... frame is separating from device... chip on left corner of palm rest."*
* **Analysis:** Dropped chassis with broken hinge screw standoffs. Requires a full palm rest rebuild ($150+ parts and labor) and carries a high risk of severed display ribbon cables.
* **Score:** **0.0 / 10 (HARD REJECT)**.
### Case Study 3: The Liquidator Parts Trap (Dell Precision 5560)
* **Listing:** Dell Precision 5560, i7-11850H, 32GB RAM, RTX A2000, "NO BATT / NO SSD / NO O.S", Price: $389.
* **Analysis:** An uneducated bot sees $389 for a 32GB workstation. Adding tax (
$32), a replacement 86Wh battery ($55), and a 1TB SSD ($65) brings the Total Landed Cost to **~$541**. Working, complete 11th-Gen 5560s sell for $460. The buyer takes on hardware risk for negative equity.
* **Score:** **0.0 / 10 (HARD REJECT)**.
### Case Study 4: The 11th-Gen "i9" Trap (Precision 5560 / ThinkPad P1 Gen 4)
* **Listing:** ThinkPad P1 Gen 4, i7-11800H, 64GB DDR4, 1TB SSD, 3K Screen, RTX A2000, Price: $699.
* **Analysis:** Listed at 12th-Gen pricing ($699 + tax = $756 TLC) for an 8-core 11th-Gen CPU on slower DDR4. A 12th-Gen XPS 9520 (14 cores / DDR5) can be acquired in the same price band.
* **Score:** **0.0 / 10 (HARD REJECT)**.
### Case Study 5: The Valid High-Margin Buy (Dell XPS 15 9520 i9)
* **Listing:** Dell XPS 15 9520, i9-12900HK, 32GB DDR5, 1TB SSD, RTX 3050 Ti, Price: $640 OBO.
* **Analysis:** Unlocked 14-core top-bin 12th-Gen silicon, modular DDR5 slots, 1TB storage, discrete RTX graphics, structurally sound. Submitting an offer at $575 yields a Landed Cost of ~$622 against an FMV of $800+.
* **Score:** **8.8 / 10 (Strong Arbitrage / High-Priority Target)**.
---
## 7. Ingestion Pipeline & Execution Logic
d
arsid13858247 [Incoming Listing Stream]
\'a6 \'3f [Filter 1: Hard Disqualification Regex] \'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'3f (Match? \'2d\'2d\'3f DROP listing / Score 0.0) \'a6 \'3f
[Filter 2: Silicon Whitelist Verification] \'2d\'2d\'2d\'2d\'2d\'2d\'2d\'3f (Not Whitelisted? \'2d\'2d\'3f DROP listing / Score 0.0) \'a6 \'3f [Filter 3: Memory Floor Check (
\'3d32GB)] \'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'3f (<32GB & Soldered? \'2d\'2d\'3f DROP listing / Score 0.0) \'a6 \'3f
[Filter 4: Compute Total Landed Cost (TLC)] \'a6 (Sticker + Tax + Shipping + Penalties) \'3f [Filter 5: Compare Against FMV Ground Truth] \'a6 (Compute Margin Spread %) \'3f [Filter 6: Assign Calibrated Deal Score] \'2d
\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'2d\'3f (Score < 7.0? \'2d\'2d\'3f Discard from Dashboard) \'a6 \'3f [Publish High-Conviction Alert to Dashboard]
d
arsid13858247
### High-Priority Negative Pattern Matchers (For Ingestion)
```regex
# Hardware Defects, Broken Chassis, and Liquidator Traps
(?i)(bent\*frame|frame\*separat|broken\*hinge|cracked\*housing|chipped\*palm|screen\*scratch|keyboard\*imprint|no\*batt|no\*battery|no\*ssd|no\*storage|no\*drive|no\*o\\.?s|for\*parts|parts\*only|as\*is|untested|for\\
s*repair|bad\*gpu|dead\*gpu|iris\*only|bad\*screen|water\*damage)
# Obsolete Silicon & Older Chassis
(?i)(i[3579]-(11\\3\|10\\3\|9\\3\|8\\3\)|precision\*(5560|5550|7550|7560)|xps\*15\*(9500|9510)|zbook\*(studio\*)?g[78]|thinkpad\*(p1|x1\*extreme)\*gen\*[1-4]|touch\*bar|sony\*vaio)
# Enterprise Locks & Profiles
(?i)(mdm|enrollment|managed|profile\*lock|icloud\*lock|activation\*lock|bios\*lock|computrace|absolute\*persistence)
d
arsid13858247
8. Standard Evaluator Output JSON Schema
d
arsid13858247
Every evaluated listing parsed by the ingestion worker must match this JSON structure:
d
arsid13858247 JSON
d
arsid13858247
\
"listing_title": "Dell XPS 15 9530 i7-13700H 64GB DDR5 1TB SSD RTX 4050 Mint",
"evaluated_model": "Dell XPS 15 9530",
"cpu_architecture": "13th-Gen (14C/20T)",
"ram_gb": 64,
"ram_type": "DDR5",
"storage_gb": 1000,
"raw_price": 665.00,
"shipping_cost": 0.00,
"estimated_tax": 54.86,
"refurbishment_penalties": 0.00,
"total_landed_cost": 719.86,
"fair_market_value": 1150.00,
"margin_spread_percent": 37.40,
"deal_score": 9.7,
"verdict": "HIGH_CONVICTION_STRIKE",
"rejection_reasons": [],
"risk_flags": []
\
d
\*504b030414000600080000002100e9de0fbfff0000001c020000130000005b436f6e74656e745f54797065735d2e786d6cac91cb4ec3301045f748fc83e52d4a
9cb2400825e982c78ec7a27cc0c8992416c9d8b2a755fbf74cd25442a820166c2cd933f79e3be372bd1f07b5c3989ca74aaff2422b24eb1b475da5df374fd9ad
5689811a183c61a50f98f4babebc2837878049899a52a57be670674cb23d8e90721f90a4d2fa3802cb35762680fd800ecd7551dc18eb899138e3c943d7e503b6
b01d583deee5f99824e290b4ba3f364eac4a430883b3c092d4eca8f946c916422ecab927f52ea42b89a1cd59c254f919b0e85e6535d135a8de20f20b8c12c3b0
0c895fcf6720192de6bf3b9e89ecdbd6596cbcdd8eb28e7c365ecc4ec1ff1460f53fe813d3cc7f5b7f020000ffff0300504b030414000600080000002100a5d6
a7e7c0000000360100000b0000005f72656c732f2e72656c73848fcf6ac3300c87ef85bd83d17d51d2c31825762fa590432fa37d00e1287f68221bdb1bebdb4f
c7060abb0884a4eff7a93dfeae8bf9e194e720169aaa06c3e2433fcb68e1763dbf7f82c985a4a725085b787086a37bdbb55fbc50d1a33ccd311ba548b6309512
0f88d94fbc52ae4264d1c910d24a45db3462247fa791715fd71f989e19e0364cd3f51652d73760ae8fa8c9ffb3c330cc9e4fc17faf2ce545046e37944c69e462
a1a82fe353bd90a865aad41ed0b5b8f9d6fd010000ffff0300504b0304140006000800000021006b799616830000008a0000001c0000007468656d652f746865
6d652f7468656d654d616e616765722e786d6c0ccc4d0ac3201040e17da17790d93763bb284562b2cbaebbf600439c1a41c7a0d29fdbd7e5e38337cedf14d59b
4b0d592c9c070d8a65cd2e88b7f07c2ca71ba8da481cc52c6ce1c715e6e97818c9b48d13df49c873517d23d59085adb5dd20d6b52bd521ef2cdd5eb9246a3d8b
4757e8d3f729e245eb2b260a0238fd010000ffff0300504b030414000600080000002100b6f4679893070000c9200000160000007468656d652f7468656d652f
7468656d65312e786d6cec59cd8b1bc915bf07f23f347d97f5d5ad8fc1f2a24fcfda33b6b164873dd648a5eef2547789aad28cc56208de532e81c026e49085bd
ed21842cecc22eb9e48f31d8249b3f22afaa5bdd5552c99e191c3061463074977eefd5afde7bf5de53d5ddcf5e26d4bbc05c1096f6fcfa9d9aefe174ce16248d
7afeb3d9a4d2f13d2151ba4094a5b8e76fb0f03fbbf7eb5fdd454732c609f6403e1547a8e7c752ae8eaa5531876124eeb0154ee1bb25e30992f0caa3ea82a34b
d09bd06aa3566b55134452df4b51026a1f2f97648ebd9952e9dfdb2a1f53784da5500373caa74a35b6243476715e5708b11143cabd0b447b3eccb3609733fc52
fa1e4542c2173dbfa6fffceabdbb5574940b517940d6909be8bf5c2e17589c37f49c3c3a2b260d823068f50bfd1a40e53e6edc1eb7c6ad429f06a0f91c569a71
b175b61bc320c71aa0ecd1a17bd41e35eb16ded0dfdce3dc0fd5c7c26b50a63fd8c34f2643b0a285d7a00c1feee1c3417730b2f56b50866fede1dbb5fe28685b
fa3528a6243ddf43d7c25673b85d6d0159327aec8477c360d26ee4ca4b144443115d6a8a254be5a1584bd00bc6270050408a24493db959e1259a43140f112567
9c7827248a21f056286502866b8ddaa4d684ffea13e827ed5174849121ad780113b137a4f87862cec94af6fc07a0d537206f7ffef9cdeb1fdfbcfee9cd575fbd
79fdf77c6eadca923b466964cafdf2dd1ffef3cd6fbd7ffff0ed2f5fff319b7a172f4cfcbbbffdeedd3ffef93ef5b0e2d2146ffff4fdbb1fbf7ffbe7dfffebaf
5f3bb4f7393a33e1339260e13dc297de5396c0021dfcf119bf9ec42c46c494e8a791402952b338f48f656ca11f6d10450edc00db767cce21d5b880f7d72f2cc2
d398af2571687c182716f094313a60dc6985876a2ec3ccb3751ab927e76b13f714a10bd7dc43945a5e1eaf579063894be530c616cd2714a5124538c5d253dfb1
738c1dabfb8210cbaea764ce99604be97d41bc01224e93ccc899154da5d03149c02f1b1741f0b7659bd3e7de8051d7aa47f8c246c2de40d4417e86a965c6fb68
2d51e252394309350d7e8264ec2239ddf0b9891b0b099e8e3065de78818570c93ce6b05ec3e90f21cdb8dd7e4a37898de4929cbb749e20c64ce4889d0f6394ac
5cd829496313fbb938871045de13265df05366ef10f50e7e40e941773f27d872f787b3c133c8b026a53240d4376beef0e57dccacf89d6ee8126157aae9f3c44a
b17d4e9cd131584756689f604cd1255a60ec3dfbdcc160c05696cd4bd20f62c82ac7d815580f901dabea3dc5027a25d5dcece7c91322ac909de2881de073bad9
493c1b9426881fd2fc08bc6eda7c0ca52e7105c0633a3f37818f08f480102f4ea33c16a0c308ee835a9fc4c82a60ea5db8e375c32dff5d658fc1be7c61d1b8c2
be04197c6d1948eca6cc7b6d3343d49aa00c9819822ec3956e41c4727f29a28aab165b3be596f6a62ddd00dd91d5f42424fd6007b4d3fb84ffbbde073a8cb77f
f9c6b10f3e4ebfe3566c25ab6b763a8792c9f14e7f7308b7dbd50c195f904fbfa919a175fa04431dd9cf58b73dcd6d4fe3ffdff73487f6f36d2773a8dfb8ed64
7ce8306e3b99fc70e5e3743265f3027d8d3af0c80e7af4b14f72f0d46749289dca0dc527421ffc08f83db398c0a092d3279eb838055cc5f0a8ca1c4c60e1228e
b48cc799fc0d91f134462b381daafb4a492472d591f0564cc0a1911e76ea5678ba4e4ed9223becacd7d5c16656590592e5782d2cc6e1a04a66e856bb3cc02bd4
6bb6913e68dd1250b2d721614c6693683a48b4b783ca48fa58178ce620a157f65158741d2c3a4afdd6557b2c805ae115f8c1edc1cff49e1f06200242701e07cd
f942f92973f5d6bbda991fd3d3878c69450034d8db08283ddd555c0f2e4fad2e0bb52b78da2261849b4d425b46377822869fc17974aad1abd0b8aeafbba54b2d
7aca147a3e08ad9246bbf33e1637f535c8ede6069a9a9982a6de65cf6f35430899395af5fc251c1ac363b282d811ea3717a211dcbccc25cf36fc4d32cb8a0b39
4222ce0cae934e960d122231f728497abe5a7ee1069aea1ca2b9d51b90103e59725d482b9f1a3970baed64bc5ce2b934dd6e8c284b67af90e1b35ce1fc568bdf
1cac24d91adc3d8d1797de195df3a708422c6cd795011744c0dd413db3e682c0655891c8caf8db294c79da356fa3740c65e388ae62945714339967709dca0b3a
faadb081f196af190c6a98242f8467912ab0a651ad6a5a548d8cc3c1aafb6121653923699635d3ca2aaa6abab39835c3b60cecd8f26645de60b53531e434b3c2
67a97b37e576b7b96ea74f28aa0418bcb09fa3ea5ea12018d4cac92c6a8af17e1a56393b1fb56bc776811fa07695226164fdd656ed8edd8a1ae19c0e066f54f9
416e376a6168b9ed2bb5a5f5adb979b1cdce5e40f2184197bba6526857c2c92e47d0104d754f92a50dd8222f65be35e0c95b73d2f3bfac85fd60d80887955a27
1c57826650ab74c27eb3d20fc3667d1cd66ba341e31514161927f530bbb19fc00506dde4f7f67a7cefee3ed9ded1dc99b3a4caf4dd7c5513d777f7f5c6e1bb7b
8f40d2f9b2d598749bdd41abd26df627956034e854bac3d6a0326a0ddba3c9681876ba9357be77a1c141bf390c5ae34ea5551f0e2b41aba6e877ba9576d068f4
8376bf330efaaff23606569ea58fdc16605ecdebde7f010000ffff0300504b0304140006000800000021000dd1909fb60000001b010000270000007468656d65
2f7468656d652f5f72656c732f7468656d654d616e616765722e786d6c2e72656c73848f4d0ac2301484f78277086f6fd3ba109126dd88d0add40384e4350d36
3f2451eced0dae2c082e8761be9969bb979dc9136332de3168aa1a083ae995719ac16db8ec8e4052164e89d93b64b060828e6f37ed1567914b284d262452282e
3198720e274a939cd08a54f980ae38a38f56e422a3a641c8bbd048f7757da0f19b017cc524bd62107bd5001996509affb3fd381a89672f1f165dfe514173d985
0528a2c6cce0239baa4c04ca5bbabac4df000000ffff0300504b01022d0014000600080000002100e9de0fbfff0000001c020000130000000000000000000000
0000000000005b436f6e74656e745f54797065735d2e786d6c504b01022d0014000600080000002100a5d6a7e7c0000000360100000b00000000000000000000
000000300100005f72656c732f2e72656c73504b01022d00140006000800000021006b799616830000008a0000001c0000000000000000000000000019020000
7468656d652f7468656d652f7468656d654d616e616765722e786d6c504b01022d0014000600080000002100b6f4679893070000c92000001600000000000000
000000000000d60200007468656d652f7468656d652f7468656d65312e786d6c504b01022d00140006000800000021000dd1909fb60000001b01000027000000
000000000000000000009d0a00007468656d652f7468656d652f5f72656c732f7468656d654d616e616765722e786d6c2e72656c73504b050600000000050005005d010000980b00000000
\*3c3f786d6c2076657273696f6e3d22312e302220656e636f64696e673d225554462d3822207374616e64616c6f6e653d22796573223f3e0d0a3c613a636c724d
617020786d6c6e733a613d22687474703a2f2f736368656d61732e6f70656e786d6c666f726d6174732e6f72672f64726177696e676d6c2f323030362f6d6169
6e22206267313d226c743122207478313d22646b3122206267323d226c743222207478323d22646b322220616363656e74313d22616363656e74312220616363
656e74323d22616363656e74322220616363656e74333d22616363656e74332220616363656e74343d22616363656e74342220616363656e74353d22616363656e74352220616363656e74363d22616363656e74362220686c696e6b3d22686c696e6b2220666f6c486c696e6b3d22666f6c486c696e6b222f3e
\*Normal;heading 1;
heading 2;heading 3;heading 4;
heading 5;heading 6;heading 7;
heading 8;heading 9;index 1;
index 2;index 3;index 4;index 5;
index 6;index 7;index 8;index 9;
toc 1;toc 2;toc 3;
toc 4;toc 5;toc 6;
toc 7;toc 8;toc 9;Normal Indent;
footnote text;annotation text;header;footer;
index heading;caption;table of figures;
envelope address;envelope return;footnote reference;annotation reference;
line number;page number;endnote reference;endnote text;
table of authorities;macro;toa heading;List;
List Bullet;List Number;List 2;List 3;
List 4;List 5;List Bullet 2;List Bullet 3;
List Bullet 4;List Bullet 5;List Number 2;List Number 3;
List Number 4;List Number 5;Title;Closing;
Signature;Default Paragraph Font;Body Text;Body Text Indent;
List Continue;List Continue 2;List Continue 3;List Continue 4;
List Continue 5;Message Header;Subtitle;Salutation;
Date;Body Text First Indent;Body Text First Indent 2;Note Heading;
Body Text 2;Body Text 3;Body Text Indent 2;Body Text Indent 3;
Block Text;Hyperlink;FollowedHyperlink;Strong;
Emphasis;Document Map;Plain Text;E-mail Signature;
HTML Top of Form;HTML Bottom of Form;Normal (Web);HTML Acronym;
HTML Address;HTML Cite;HTML Code;HTML Definition;
HTML Keyboard;HTML Preformatted;HTML Sample;HTML Typewriter;
HTML Variable;Normal Table;annotation subject;No List;
Outline List 1;Outline List 2;Outline List 3;Table Simple 1;
Table Simple 2;Table Simple 3;Table Classic 1;Table Classic 2;
Table Classic 3;Table Classic 4;Table Colorful 1;Table Colorful 2;
Table Colorful 3;Table Columns 1;Table Columns 2;Table Columns 3;
Table Columns 4;Table Columns 5;Table Grid 1;Table Grid 2;
Table Grid 3;Table Grid 4;Table Grid 5;Table Grid 6;
Table Grid 7;Table Grid 8;Table List 1;Table List 2;
Table List 3;Table List 4;Table List 5;Table List 6;
Table List 7;Table List 8;Table 3D effects 1;Table 3D effects 2;
Table 3D effects 3;Table Contemporary;Table Elegant;Table Professional;
Table Subtle 1;Table Subtle 2;Table Web 1;Table Web 2;
Table Web 3;Balloon Text;Table Grid;Table Theme;Placeholder Text;
No Spacing;Light Shading;Light List;Light Grid;Medium Shading 1;Medium Shading 2;
Medium List 1;Medium List 2;Medium Grid 1;Medium Grid 2;Medium Grid 3;Dark List;
Colorful Shading;Colorful List;Colorful Grid;Light Shading Accent 1;Light List Accent 1;
Light Grid Accent 1;Medium Shading 1 Accent 1;Medium Shading 2 Accent 1;Medium List 1 Accent 1;Revision;
List Paragraph;Quote;Intense Quote;Medium List 2 Accent 1;Medium Grid 1 Accent 1;
Medium Grid 2 Accent 1;Medium Grid 3 Accent 1;Dark List Accent 1;Colorful Shading Accent 1;Colorful List Accent 1;
Colorful Grid Accent 1;Light Shading Accent 2;Light List Accent 2;Light Grid Accent 2;Medium Shading 1 Accent 2;
Medium Shading 2 Accent 2;Medium List 1 Accent 2;Medium List 2 Accent 2;Medium Grid 1 Accent 2;Medium Grid 2 Accent 2;
Medium Grid 3 Accent 2;Dark List Accent 2;Colorful Shading Accent 2;Colorful List Accent 2;Colorful Grid Accent 2;
Light Shading Accent 3;Light List Accent 3;Light Grid Accent 3;Medium Shading 1 Accent 3;Medium Shading 2 Accent 3;
Medium List 1 Accent 3;Medium List 2 Accent 3;Medium Grid 1 Accent 3;Medium Grid 2 Accent 3;Medium Grid 3 Accent 3;
Dark List Accent 3;Colorful Shading Accent 3;Colorful List Accent 3;Colorful Grid Accent 3;Light Shading Accent 4;
Light List Accent 4;Light Grid Accent 4;Medium Shading 1 Accent 4;Medium Shading 2 Accent 4;Medium List 1 Accent 4;
Medium List 2 Accent 4;Medium Grid 1 Accent 4;Medium Grid 2 Accent 4;Medium Grid 3 Accent 4;Dark List Accent 4;
Colorful Shading Accent 4;Colorful List Accent 4;Colorful Grid Accent 4;Light Shading Accent 5;Light List Accent 5;
Light Grid Accent 5;Medium Shading 1 Accent 5;Medium Shading 2 Accent 5;Medium List 1 Accent 5;Medium List 2 Accent 5;
Medium Grid 1 Accent 5;Medium Grid 2 Accent 5;Medium Grid 3 Accent 5;Dark List Accent 5;Colorful Shading Accent 5;
Colorful List Accent 5;Colorful Grid Accent 5;Light Shading Accent 6;Light List Accent 6;Light Grid Accent 6;
Medium Shading 1 Accent 6;Medium Shading 2 Accent 6;Medium List 1 Accent 6;Medium List 2 Accent 6;
Medium Grid 1 Accent 6;Medium Grid 2 Accent 6;Medium Grid 3 Accent 6;Dark List Accent 6;Colorful Shading Accent 6;
Colorful List Accent 6;Colorful Grid Accent 6;Subtle Emphasis;Intense Emphasis;
Subtle Reference;Intense Reference;Book Title;Bibliography;
TOC Heading;Plain Table 1;Plain Table 2;Plain Table 3;Plain Table 4;
Plain Table 5;Grid Table Light;Grid Table 1 Light;Grid Table 2;Grid Table 3;Grid Table 4;
Grid Table 5 Dark;Grid Table 6 Colorful;Grid Table 7 Colorful;Grid Table 1 Light Accent 1;Grid Table 2 Accent 1;
Grid Table 3 Accent 1;Grid Table 4 Accent 1;Grid Table 5 Dark Accent 1;Grid Table 6 Colorful Accent 1;
Grid Table 7 Colorful Accent 1;Grid Table 1 Light Accent 2;Grid Table 2 Accent 2;Grid Table 3 Accent 2;
Grid Table 4 Accent 2;Grid Table 5 Dark Accent 2;Grid Table 6 Colorful Accent 2;Grid Table 7 Colorful Accent 2;
Grid Table 1 Light Accent 3;Grid Table 2 Accent 3;Grid Table 3 Accent 3;Grid Table 4 Accent 3;
Grid Table 5 Dark Accent 3;Grid Table 6 Colorful Accent 3;Grid Table 7 Colorful Accent 3;Grid Table 1 Light Accent 4;
Grid Table 2 Accent 4;Grid Table 3 Accent 4;Grid Table 4 Accent 4;Grid Table 5 Dark Accent 4;
Grid Table 6 Colorful Accent 4;Grid Table 7 Colorful Accent 4;Grid Table 1 Light Accent 5;Grid Table 2 Accent 5;
Grid Table 3 Accent 5;Grid Table 4 Accent 5;Grid Table 5 Dark Accent 5;Grid Table 6 Colorful Accent 5;
Grid Table 7 Colorful Accent 5;Grid Table 1 Light Accent 6;Grid Table 2 Accent 6;Grid Table 3 Accent 6;
Grid Table 4 Accent 6;Grid Table 5 Dark Accent 6;Grid Table 6 Colorful Accent 6;Grid Table 7 Colorful Accent 6;
List Table 1 Light;List Table 2;List Table 3;List Table 4;List Table 5 Dark;
List Table 6 Colorful;List Table 7 Colorful;List Table 1 Light Accent 1;List Table 2 Accent 1;List Table 3 Accent 1;
List Table 4 Accent 1;List Table 5 Dark Accent 1;List Table 6 Colorful Accent 1;List Table 7 Colorful Accent 1;
List Table 1 Light Accent 2;List Table 2 Accent 2;List Table 3 Accent 2;List Table 4 Accent 2;
List Table 5 Dark Accent 2;List Table 6 Colorful Accent 2;List Table 7 Colorful Accent 2;List Table 1 Light Accent 3;
List Table 2 Accent 3;List Table 3 Accent 3;List Table 4 Accent 3;List Table 5 Dark Accent 3;
List Table 6 Colorful Accent 3;List Table 7 Colorful Accent 3;List Table 1 Light Accent 4;List Table 2 Accent 4;
List Table 3 Accent 4;List Table 4 Accent 4;List Table 5 Dark Accent 4;List Table 6 Colorful Accent 4;
List Table 7 Colorful Accent 4;List Table 1 Light Accent 5;List Table 2 Accent 5;List Table 3 Accent 5;
List Table 4 Accent 5;List Table 5 Dark Accent 5;List Table 6 Colorful Accent 5;List Table 7 Colorful Accent 5;
List Table 1 Light Accent 6;List Table 2 Accent 6;List Table 3 Accent 6;List Table 4 Accent 6;
List Table 5 Dark Accent 6;List Table 6 Colorful Accent 6;List Table 7 Colorful Accent 6;Mention;
Smart Hyperlink;Hashtag;Unresolved Mention;\*010500000200000018000000
4d73786d6c322e534158584d4c5265616465722e362e3000000000000000000000060000
d0cf11e0a1b11ae1000000000000000000000000000000003e000300feff090006000000000000000000000001000000010000000000000000100000feffffff00000000feffffff0000000000000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
fffffffffffffffffdfffffffeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
ffffffffffffffffffffffffffffffff52006f006f007400200045006e00740072007900000000000000000000000000000000000000000000000000000000000000000000000000000000000000000016000500ffffffffffffffffffffffff0c6ad98892f1d411a65f0040963251e5000000000000000000000000305f
a3548d2fdd01feffffff00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffff00000000000000000000000000000000000000000000000000000000
00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffff0000000000000000000000000000000000000000000000000000
000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffff000000000000000000000000000000000000000000000000
0000000000000000000000000000000000000000000000000105000000000000