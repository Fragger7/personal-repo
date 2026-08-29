import { chromium } from 'playwright';

async function findRealEv9() {
  console.log('--- FINDING REAL CARGURUS EV9 LISTINGS & REAL URLS ---');

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 800 },
  });

  const page = await context.newPage();
  const realListings: any[] = [];

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('uic-filter-data') || url.includes('search') || url.includes('resource')) {
      try {
        const text = await res.text();
        // Regex search for CarGurus listing detail links or listing IDs
        const listingIdMatches = text.match(/"listingId":\s*(\d+)/g) || text.match(/"id":\s*(\d{9})/g) || [];
        const vinMatches = text.match(/KND[A-Z0-9]{14}/g) || [];

        listingIdMatches.forEach((m, idx) => {
          const id = m.replace(/[^0-9]/g, '');
          const vin = vinMatches[idx] || '';
          if (id && !realListings.some(item => item.id === id)) {
            realListings.push({
              id,
              vin,
              cargurusUrl: `https://www.cargurus.com/Cars/link/${id}`,
              vdpUrl: `https://www.cargurus.com/Cars/inventorylisting/vdp.action?inventoryListing=${id}`,
            });
            console.log(`🔥 FOUND REAL CARGURUS LISTING ID: ${id} | VIN: ${vin}`);
          }
        });
      } catch (e) {}
    }
  });

  try {
    console.log('Navigating to CarGurus Kia EV9 Search...');
    await page.goto('https://www.cargurus.com/Cars/l-Used-Kia-EV9-d3419', { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(6000);
  } catch (err: any) {
    console.error(`Fetch error: ${err.message}`);
  } finally {
    await browser.close();
  }

  console.log(`\n=== REAL CARGURUS LISTINGS DISCOVERED (${realListings.length}) ===`);
  console.log(JSON.stringify(realListings, null, 2));
}

findRealEv9();
