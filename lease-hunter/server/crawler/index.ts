import fs from 'fs';
import path from 'path';
import { crawlCarGurus, ScrapedVehicle } from './cargurus.js';
import { scrapeLocalDealersHeadless } from './scrape-local-dealers-headless.js';
import { sendTelegramAlert } from '../services/telegram.js';

export async function runFullInventoryScan(zip: string = '78665', distance: number = 50): Promise<ScrapedVehicle[]> {
  console.log(`\n==================================================`);
  console.log(`🚀 STARTING LOCAL MULTI-SITE INVENTORY CRAWL`);
  console.log(`Target: Kia EV9 within ${distance}mi of ZIP ${zip}`);
  console.log(`==================================================\n`);

  // 1. Execute CarGurus & Dealer Direct Headless Network Interceptors
  const cargurusResults = await crawlCarGurus(zip, distance);
  const dealerDirectResults = await scrapeLocalDealersHeadless(zip);

  // 2. Read Existing Storage
  const inventoryFilePath = path.join(process.cwd(), 'data', 'inventory.json');
  const inventoryDir = path.dirname(inventoryFilePath);
  if (!fs.existsSync(inventoryDir)) {
    fs.mkdirSync(inventoryDir, { recursive: true });
  }

  let existingInventory: ScrapedVehicle[] = [];
  if (fs.existsSync(inventoryFilePath)) {
    try {
      const fileData = fs.readFileSync(inventoryFilePath, 'utf-8');
      existingInventory = JSON.parse(fileData);
    } catch (e) {
      existingInventory = [];
    }
  }

  // Merge map by VIN
  const inventoryMap = new Map<string, ScrapedVehicle>();
  existingInventory.forEach(v => inventoryMap.set(v.vin, v));
  cargurusResults.forEach(v => inventoryMap.set(v.vin, v));
  dealerDirectResults.forEach(v => inventoryMap.set(v.vin, v as ScrapedVehicle));

  const finalInventory = Array.from(inventoryMap.values());

  // 3. Save Updated Inventory to data/inventory.json
  fs.writeFileSync(inventoryFilePath, JSON.stringify(finalInventory, null, 2), 'utf-8');
  console.log(`\n✅ Inventory persistent storage updated: ${finalInventory.length} total local targets saved to data/inventory.json`);

  // 4. Check for High-Value Deals and Dispatch Telegram Alerts (Only for 100% REAL scraped items!)
  for (const vehicle of finalInventory) {
    if (vehicle.daysOnLot >= 180 || (vehicle.msrp - vehicle.listingPrice) >= 5000) {
      console.log(`\n🔥 REAL HIGH-VALUE DEAL TARGET DETECTED! Days on Lot: ${vehicle.daysOnLot}`);
      await sendTelegramAlert(vehicle);
    }
  }

  return finalInventory;
}

// Standalone CLI execution
if (process.argv[1] && process.argv[1].includes('crawler')) {
  runFullInventoryScan('78665', 50)
    .then(() => process.exit(0))
    .catch((err) => {
      console.error(err);
      process.exit(1);
    });
}
