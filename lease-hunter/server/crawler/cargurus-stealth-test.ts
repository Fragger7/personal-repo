import { chromium } from 'playwright';

async function testStealthCarGurus() {
  console.log('--- TESTING STEALTH CARGURUS EV9 SEARCH ---');

  const browser = await chromium.launch({
    headless: false, // Non-headless window allows bypassing Cloudflare/DataDome
    args: [
      '--disable-blink-features=AutomationControlled',
      '--no-sandbox',
    ],
  });

  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 800 },
  });

  const page = await context.newPage();
  const interceptedVins: string[] = [];

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('uic-filter-data') || url.includes('search') || url.includes('resource')) {
      try {
        const text = await res.text();
        const vins = text.match(/KND[A-Z0-9]{14}/g) || [];
        vins.forEach(v => {
          if (!interceptedVins.includes(v)) {
            interceptedVins.push(v);
            console.log(`🔥 Captured Kia EV9 VIN via XHR: ${v}`);
          }
        });
      } catch (e) {}
    }
  });

  try {
    console.log('Navigating to CarGurus EV9 Search Page...');
    await page.goto('https://www.cargurus.com/Cars/l-Used-Kia-EV9-d3419', { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(5000);

    const title = await page.title();
    console.log(`Page Title: "${title}"`);

    // Fallback: check DOM content
    const content = await page.content();
    const domVins = content.match(/KND[A-Z0-9]{14}/g) || [];
    domVins.forEach(v => {
      if (!interceptedVins.includes(v)) {
        interceptedVins.push(v);
        console.log(`📌 Captured Kia EV9 VIN via DOM: ${v}`);
      }
    });

  } catch (err: any) {
    console.error(`Stealth test error: ${err.message}`);
  } finally {
    await browser.close();
  }

  console.log(`\n=== FINAL RESULT ===`);
  console.log(`Total Unique EV9 VINs Captured: ${interceptedVins.length}`);
  console.log(interceptedVins);
}

testStealthCarGurus();
