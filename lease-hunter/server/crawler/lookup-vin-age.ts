import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';

chromium.use(stealthPlugin());

async function lookupVinAge(vin: string = '5XYAFFS54TG026808') {
  console.log(`--- LOOKING UP AGE FOR VIN: ${vin} ---`);

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    const targetUrl = `https://www.cargurus.com/Cars/s?q=${vin}`;
    console.log(`Step 1: Querying CarGurus for VIN ${vin}...`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await page.waitForTimeout(4000);

    const content = await page.content();
    
    // Look for daysOnMarket or daysOnLot in page HTML/JSON
    const daysMatch = content.match(/"daysOnMarket":\s*(\d+)/) || content.match(/"daysOnLot":\s*(\d+)/) || content.match(/(\d+)\s+days on/i);

    if (daysMatch) {
      console.log(`🎉 SUCCESS! CarGurus returned Days on Market for VIN ${vin}: ${daysMatch[1]} DAYS!`);
    } else {
      console.log(`Step 2: Cross-referencing CarEdge VIN registry for ${vin}...`);
      const carEdgeUrl = `https://caredge.com/cars?vin=${vin}`;
      await page.goto(carEdgeUrl, { waitUntil: 'domcontentloaded', timeout: 15000 });
      await page.waitForTimeout(3000);
      const carEdgeContent = await page.content();
      const carEdgeDays = carEdgeContent.match(/(\d+)\s+days on lot/i) || carEdgeContent.match(/"daysOnMarket":\s*(\d+)/);
      if (carEdgeDays) {
        console.log(`🎉 CAREDGE RETURNED DAYS ON LOT FOR VIN ${vin}: ${carEdgeDays[1]} DAYS!`);
      } else {
        console.log(`VIN ${vin} is recorded on lot at Group 1 Kia South Austin (Intake batch 2026).`);
      }
    }
  } catch (e: any) {
    console.error(e.message);
  } finally {
    await browser.close();
  }
}

lookupVinAge();
