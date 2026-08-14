import { chromium } from 'playwright';

async function fetchCarEdge() {
  console.log('--- SCANNING CAREDGE FOR REAL LIVE KIA EV9 LISTINGS ---');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    const url = 'https://caredge.com/cars?make=Kia&model=EV9&zip=78665&radius=50';
    console.log(`Navigating to: ${url}`);
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await page.waitForTimeout(4000);

    const content = await page.content();
    const vins = content.match(/KND[A-Z0-9]{14}/g) || [];
    const uniqueVins = Array.from(new Set(vins));

    console.log(`\n🎉 CAREDGE DISCOVERED ${uniqueVins.length} REAL LIVE VINs:`, uniqueVins);
  } catch (e: any) {
    console.error(`Error: ${e.message}`);
  } finally {
    await browser.close();
  }
}

fetchCarEdge();
