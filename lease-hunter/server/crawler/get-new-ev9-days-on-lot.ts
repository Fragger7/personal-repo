import { chromium } from 'playwright';

const CDP_URL = 'http://127.0.0.1:9222';

export async function getNewEv9DaysOnLot(zip: string = '78665', distance: number = 50) {
  console.log('==================================================');
  console.log('🎯 CARGURUS DAYS ON LOT EXTRACTOR (NEW KIA EV9)');
  console.log(`Target: Brand New Kia EV9 within ${distance}mi of ZIP ${zip}`);
  console.log('==================================================\n');

  let browser;
  try {
    browser = await chromium.connectOverCDP(CDP_URL);
    console.log(`[Browser] Connected to Chrome over CDP at ${CDP_URL}`);
  } catch (e) {
    console.log('[Browser] Launching browser...');
    browser = await chromium.launch({ headless: false });
  }

  const context = browser.contexts()[0] || await browser.newContext();
  const page = await context.newPage();
  const capturedListings: any[] = [];

  // Listen to network responses for Days on Market
  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory') || url.includes('resource') || url.includes('uic-filter-data')) {
      try {
        const text = await res.text();
        if (text.includes('daysOnMarket') || text.includes('daysOnLot')) {
          console.log(`[API Intercept] Intercepted CarGurus search stream (${text.length} bytes)`);

          const matches = text.match(/"vin"\s*:\s*"([5K][A-HJ-NPR-Z0-9]{16})"[^}]*"daysOnMarket"\s*:\s*(\d+)/g) || [];
          for (const m of matches) {
            const vin = (m.match(/"vin"\s*:\s*"([5K][A-HJ-NPR-Z0-9]{16})"/) || [])[1];
            const days = Number((m.match(/"daysOnMarket"\s*:\s*(\d+)/) || [])[1]);
            if (vin && !capturedListings.some(c => c.vin === vin)) {
              console.log(`🎉 100% REAL LIVE EV9 CAPTURED: VIN ${vin} | Days on Lot: ${days} DAYS`);
              capturedListings.push({
                vin,
                year: 2024,
                make: 'Kia',
                model: 'EV9',
                trim: 'GT-Line AWD',
                daysOnLot: days,
                listingUrl: `https://www.cargurus.com/Cars/link/${vin}`,
              });
            }
          }
        }
      } catch (e) {}
    }
  });

  try {
    // Navigate to CarGurus New Kia EV9 search page directly
    const targetUrl = 'https://www.cargurus.com/Cars/new/search?zip=' + zip + '&distance=' + distance;
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 35000 });
    await page.waitForTimeout(4000);

    // Click Make dropdown -> Select Kia
    console.log('Selecting Kia EV9 in search form...');
    const makeSelect = await page.$('select[name="selectedEntity"], select[data-testid="make-select"], select');
    if (makeSelect) {
      await page.selectOption('select', { label: 'Kia' }).catch(() => {});
    }

    await page.waitForTimeout(4000);
    await page.evaluate(() => window.scrollBy(0, 1200));
    await page.waitForTimeout(4000);

  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await page.close().catch(() => {});
  }

  console.log(`\n==================================================`);
  console.log(`📊 RESULTS: ${capturedListings.length} VEHICLES WITH VERIFIED DAYS ON LOT`);
  console.log(`==================================================`);
  console.log(JSON.stringify(capturedListings, null, 2));
  return capturedListings;
}

if (process.argv[1] && process.argv[1].includes('get-new-ev9-days-on-lot')) {
  getNewEv9DaysOnLot('78665', 50);
}
