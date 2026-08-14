import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';

chromium.use(stealthPlugin());

async function inspectGroup1Dom() {
  console.log('Inspecting Group 1 Kia South Austin DOM structure...');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    await page.goto('https://www.group1kiasouthaustin.com/new-vehicles/ev9/', { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(4000);

    // Extract all attributes containing VIN or listing URLs
    const data = await page.evaluate(() => {
      const vinAttrs = Array.from(document.querySelectorAll('[data-vin], [data-vehicle], .vehicle-card, .inventory-item'))
        .map(el => el.getAttribute('data-vin') || el.outerHTML.substring(0, 200));
      const links = Array.from(document.querySelectorAll('a[href*="/inventory/"], a[href*="/new-vehicles/"], a[href*="ev9"]'))
        .map(a => (a as HTMLAnchorElement).href);
      return { vinAttrs: vinAttrs.slice(0, 10), links: Array.from(new Set(links)).slice(0, 10) };
    });

    console.log('\n--- DOM DATA ATTRS ---');
    console.log(data.vinAttrs);
    console.log('\n--- VEHICLE DETAIL LINKS ---');
    console.log(data.links);
  } catch (e: any) {
    console.error(e.message);
  } finally {
    await browser.close();
  }
}

inspectGroup1Dom();
