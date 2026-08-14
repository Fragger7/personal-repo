import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';

chromium.use(stealthPlugin());

async function extractSouthAustinVdp() {
  console.log('Extracting exact EV9 listing links from Kia of South Austin...');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    await page.goto('https://www.kiaofsouthaustin.com/new-inventory/index.htm?model=EV9', { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(4000);

    // Find all vehicle detail anchor links (e.g. /new/Kia/2024-Kia-EV9...)
    const ev9Links = await page.evaluate(() => {
      const anchors = Array.from(document.querySelectorAll('a[href*="EV9"], a[href*="ev9"], a[href*="new/Kia"]'));
      return anchors.map(a => (a as HTMLAnchorElement).href);
    });

    const uniqueEv9Links = Array.from(new Set(ev9Links));
    console.log(`\n🎉 DISCOVERED ${uniqueEv9Links.length} DIRECT EV9 VEHICLE LINKS:`);
    uniqueEv9Links.forEach((link, idx) => console.log(`Link #${idx + 1}: ${link}`));
  } catch (e: any) {
    console.error(e.message);
  } finally {
    await browser.close();
  }
}

extractSouthAustinVdp();
