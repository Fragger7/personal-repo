import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';

// Attach Stealth Plugin to Playwright
chromium.use(stealthPlugin());

async function testStealthBypass() {
  console.log('--- TESTING PLAYWRIGHT STEALTH PLUGIN ON CARGURUS & LOCAL DEALERS ---');

  const browser = await chromium.launch({
    headless: true,
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-blink-features=AutomationControlled',
    ],
  });

  const page = await browser.newPage();
  const capturedVehicles: any[] = [];

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory') || url.includes('uic-filter-data')) {
      try {
        const text = await res.text();
        // Look for 17-char Kia EV9 VINs (KNDET... or KNDES...)
        const ev9Vins = text.match(/KNDE[ST][A-HJ-NPR-Z0-9]{13}/g) || [];
        const uniqueVins = Array.from(new Set(ev9Vins));

        uniqueVins.forEach(vin => {
          if (!capturedVehicles.some(v => v.vin === vin)) {
            console.log(`🎉 STEALTH BYPASS SUCCESS! Captured EV9 VIN: ${vin}`);
            capturedVehicles.push({ vin, source: 'Stealth Plugin Interceptor' });
          }
        });
      } catch (e) {}
    }
  });

  const testUrls = [
    { name: 'CarGurus EV9 Search', url: 'https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action#shoppingList/search?zip=78665&distance=50' },
    { name: 'Kia of South Austin', url: 'https://www.kiaofsouthaustin.com/new-inventory/index.htm?model=EV9' },
  ];

  for (const item of testUrls) {
    try {
      console.log(`\nNavigating with Stealth: ${item.name} (${item.url})`);
      const res = await page.goto(item.url, { waitUntil: 'domcontentloaded', timeout: 25000 });
      console.log(`[Status ${res?.status()}]: ${await page.title()}`);
      await page.waitForTimeout(4000);
    } catch (e: any) {
      console.error(`Note: ${e.message}`);
    }
  }

  await browser.close();
  console.log(`\n=== TOTAL VERIFIED EV9 VINs CAPTURED WITH STEALTH: ${capturedVehicles.length} ===`);
}

testStealthBypass();
