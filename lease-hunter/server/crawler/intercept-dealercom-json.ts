import { chromium } from 'playwright';

async function testDealerCom() {
  console.log('==================================================');
  console.log('🔍 DEALER.COM XHR/JSON INTERCEPTOR (Kia of Round Rock)');
  console.log('==================================================\n');

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
  });
  const page = await context.newPage();

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('api') || url.includes('inventory') || url.includes('search') || url.includes('json')) {
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
    const targetUrl = `https://www.kiaofroundrock.com/new-inventory/index.htm?model=EV9`;
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
    
    // Check if inventory is embedded in window object
    const windowData = await page.evaluate(() => {
       const ddc = (window as any).DDC;
       if (ddc) return JSON.stringify(ddc).substring(0, 500);
       return "No window.DDC object found.";
    });
    console.log(`\n[Window State Data]: ${windowData}`);

    await page.waitForTimeout(10000); // Wait for XHRs to complete
  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await browser.close();
  }
}

testDealerCom();
