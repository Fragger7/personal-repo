import { chromium } from 'playwright';

async function debugCargurus() {
  console.log('--- DEBUGGING CARGURUS EV9 SEARCH ---');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('api') || url.includes('json') || url.includes('inventory') || url.includes('search')) {
      console.log(`[Network XHR]: ${res.status()} -> ${url.substring(0, 120)}`);
      try {
        const text = await res.text();
        if (text.includes('EV9') || text.includes('vin') || text.includes('listings')) {
          console.log(`>>> FOUND KEYWORD IN XHR! Length: ${text.length}`);
        }
      } catch (e) {}
    }
  });

  const testUrls = [
    'https://www.cargurus.com/Cars/l-Used-Kia-EV9-d3326#zip=78665&distance=50',
    'https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action#shoppingList/search?zip=78665&distance=50&search=Kia%20EV9',
  ];

  for (const targetUrl of testUrls) {
    console.log(`\nNavigating to: ${targetUrl}`);
    try {
      await page.goto(targetUrl, { waitUntil: 'networkidle', timeout: 20000 });
      const title = await page.title();
      console.log(`Page Title: ${title}`);
      await page.waitForTimeout(4000);
    } catch (e: any) {
      console.error(`Navigation error: ${e.message}`);
    }
  }

  await browser.close();
  console.log('--- DEBUG COMPLETE ---');
}

debugCargurus();
