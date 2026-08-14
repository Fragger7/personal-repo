import { chromium } from 'playwright';

async function testDataDomeBypass() {
  console.log('--- TESTING DATADOME BYPASS & ROUTE DISCOVERY ---');

  // Launch Chromium with realistic user flags
  const browser = await chromium.launch({
    headless: false, // Visible window mode to pass DataDome initial check
    args: [
      '--disable-blink-features=AutomationControlled',
      '--window-size=1280,800',
    ],
  });

  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 800 },
  });

  const page = await context.newPage();

  // Listen to network responses
  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('_data=routes') || url.includes('/search/') || url.includes('inventory')) {
      console.log(`[Intercepted XHR ${res.status()}]: ${url.substring(0, 130)}`);
      try {
        const text = await res.text();
        if (text.includes('EV9') || text.includes('KNDET') || text.includes('listings')) {
          console.log(`🎉 SUCCESS! Payload containing vehicle listings captured (${text.length} bytes)`);
        }
      } catch (e) {}
    }
  });

  console.log('Navigating to CarGurus EV9 Search Page...');
  await page.goto('https://www.cargurus.com/Cars/l-Used-Kia-EV9-d3326', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(5000);

  const title = await page.title();
  console.log(`Current Page Title: "${title}"`);

  await browser.close();
  console.log('--- TEST COMPLETE ---');
}

testDataDomeBypass();
