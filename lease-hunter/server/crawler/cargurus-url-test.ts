import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';

chromium.use(stealthPlugin());

async function testCarGurusUrlFormats() {
  console.log('--- TESTING CARGURUS EV9 SEARCH URL FORMATS ---');

  const browser = await chromium.launch({
    headless: false, // Visible window mode ensures DataDome turnstile passes
    args: ['--disable-blink-features=AutomationControlled'],
  });

  const page = await browser.newPage();

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory') || url.includes('uic-filter-data')) {
      try {
        const text = await res.text();
        if (text.includes('daysOnMarket') || text.includes('EV9') || text.includes('listings')) {
          console.log(`🎉 SUCCESS! Intercepted CarGurus JSON payload (${text.length} bytes) from: ${url.substring(0, 110)}`);
          // Print snippet of daysOnMarket data
          const daysMatches = text.match(/"daysOnMarket"\s*:\s*\d+/g) || [];
          console.log(`Found ${daysMatches.length} daysOnMarket fields:`, daysMatches.slice(0, 5));
        }
      } catch (e) {}
    }
  });

  const urlsToTest = [
    'https://www.cargurus.com/Cars/l-Used-Kia-EV9-d3444#zip=78665&distance=50',
    'https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?zip=78665&distance=50&entitySelectingHelper.selectedEntity=d3444',
  ];

  for (const url of urlsToTest) {
    try {
      console.log(`\nTesting URL: ${url}`);
      const res = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 25000 });
      console.log(`[Status ${res?.status()}]: ${await page.title()}`);
      await page.waitForTimeout(5000);
    } catch (e: any) {
      console.error(`Error: ${e.message}`);
    }
  }

  await browser.close();
  console.log('--- TEST COMPLETE ---');
}

testCarGurusUrlFormats();
