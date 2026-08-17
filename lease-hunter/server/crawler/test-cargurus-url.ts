import { chromium } from 'playwright';

const CDP_URL = 'http://127.0.0.1:9222';

async function checkUrl() {
  console.log('--- Checking simple URL ---');
  try {
    const browser = await chromium.connectOverCDP(CDP_URL);
    const context = browser.contexts()[0];
    const page = await context.newPage();

    const urls = [
      'https://www.cargurus.com/Cars/new/nl-New-Kia-EV9-sp102035',
      'https://www.cargurus.com/Cars/new/nl-New-Kia-EV9-sp102035?zip=78665&distance=50'
    ];

    for(const url of urls) {
      console.log(`Navigating to: ${url}`);
      const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
      console.log(`Status: ${resp?.status()}`);
      console.log(`Title: ${await page.title()}`);
      const text = await page.evaluate(() => document.body.innerText.substring(0, 500));
      console.log(`Body Preview: ${text.substring(0, 200).replace(/\n/g, ' ')}\n`);
    }

    await page.close();
  } catch (err: any) {
    console.error(err.message);
  }
}

checkUrl();
