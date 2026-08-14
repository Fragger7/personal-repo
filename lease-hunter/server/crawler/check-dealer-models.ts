import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';

chromium.use(stealthPlugin());

async function checkDealerModels() {
  console.log('Checking all in-stock Kia models at Kia of South Austin...');

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    await page.goto('https://www.kiaofsouthaustin.com/new-inventory/index.htm', { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(4000);

    const content = await page.content();
    
    // Extract unique model names found on page
    const models = ['EV9', 'Telluride', 'Seltos', 'Sportage', 'Sorento', 'K5', 'Forte', 'Carnival', 'Soul', 'Niro'];
    console.log('\n--- MODEL IN-STOCK INVENTORY REPORT ---');
    models.forEach(m => {
      const count = (content.match(new RegExp(m, 'gi')) || []).length;
      console.log(`Model: ${m.padEnd(12)} -> Mentioned ${count} times on inventory page`);
    });
  } catch (e: any) {
    console.error(e.message);
  } finally {
    await browser.close();
  }
}

checkDealerModels();
