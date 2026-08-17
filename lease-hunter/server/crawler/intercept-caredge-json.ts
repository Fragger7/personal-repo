import { chromium } from 'playwright';

async function testCarEdge() {
  console.log('==================================================');
  console.log('🔍 CAREDGE XHR/JSON INTERCEPTOR');
  console.log('==================================================\n');

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  });
  const page = await context.newPage();

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('api') || url.includes('graphql') || url.includes('inventory')) {
      try {
        const text = await res.text();
        if (text.includes('vin') || text.includes('EV9') || text.includes('price')) {
           console.log(`\n[JSON Intercepted] URL: ${url}`);
           console.log(text.substring(0, 1500));
        }
      } catch (e) {}
    }
  });

  try {
    const targetUrl = `https://my.caredge.com/buy?radius=50&zip=78665&make=Kia&model=EV9`;
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(10000); // Wait for XHRs to complete
  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await browser.close();
  }
}

testCarEdge();
