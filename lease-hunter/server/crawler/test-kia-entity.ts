import { chromium } from 'playwright';

async function testKiaEntity() {
  console.log('Testing CarGurus Kia Make ID m50...');
  const browser = await chromium.launch({ headless: false });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 800 },
  });
  const page = await context.newPage();

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('uic-filter-data') || url.includes('search') || url.includes('inventory')) {
      try {
        const text = await res.text();
        if (text.includes('EV9') || text.includes('KND')) {
          console.log(`🎉 SUCCESS! Found Kia EV9 data in XHR response (${text.length} bytes)`);
        }
      } catch (e) {}
    }
  });

  try {
    await page.goto('https://www.cargurus.com/Cars/m-Kia-d50', { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(4000);
    console.log(`Page Title: ${await page.title()}`);
  } catch (e: any) {
    console.error(e.message);
  } finally {
    await browser.close();
  }
}

testKiaEntity();
