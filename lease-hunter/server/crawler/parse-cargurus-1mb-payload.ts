import { chromium } from 'playwright';
import fs from 'fs';

const CDP_URL = 'http://127.0.0.1:9222';

async function parseCarGurus1MbPayload() {
  console.log('--- EXTRACTING CARGURUS 1.24MB LIVE SEARCH PAYLOAD OVER CDP ---');

  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0];
  const page = await context.newPage();

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory')) {
      try {
        const text = await res.text();
        if (text.length > 50000) {
          console.log(`🎉 INTERCEPTED ${text.length} BYTES FROM: ${url}`);
          fs.writeFileSync('scratch/cargurus-live-payload.json', text);
          console.log('Saved raw payload to scratch/cargurus-live-payload.json');

          // Parse JSON structure
          try {
            const data = JSON.parse(text);
            console.log('JSON Root Keys:', Object.keys(data));
            if (data.listings) console.log(`Found data.listings: ${data.listings.length} items`);
            if (data.results) console.log(`Found data.results: ${data.results.length} items`);
          } catch (e) {
            console.log('Text is raw response payload, regex scanning for VINs and Days on Lot...');
            const vinMatches = text.match(/"vin"\s*:\s*"([A-Z0-9]{17})"/g) || [];
            const daysMatches = text.match(/"daysOnMarket"\s*:\s*(\d+)/g) || text.match(/"daysOnLot"\s*:\s*(\d+)/g) || [];
            console.log(`Found ${vinMatches.length} VIN fields and ${daysMatches.length} Days on Lot fields!`);
          }
        }
      } catch (e) {}
    }
  });

  try {
    await page.goto('https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action#shoppingList/search?zip=78665&distance=50', {
      waitUntil: 'domcontentloaded',
      timeout: 35000,
    });
    await page.waitForTimeout(6000);
  } catch (e: any) {
    console.error(e.message);
  } finally {
    await page.close().catch(() => {});
  }
}

parseCarGurus1MbPayload();
