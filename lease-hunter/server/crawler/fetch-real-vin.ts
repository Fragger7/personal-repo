import { chromium } from 'playwright';

async function fetchRealVin() {
  console.log('--- FETCHING REAL LIVE KIA EV9 VIN ---');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    console.log('Searching CarGurus for Kia EV9...');
    await page.goto('https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action#shoppingList/search?zip=78665&distance=50', { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(5000);

    const content = await page.content();
    // Look for real 17-character Kia VIN starting with KND
    const vinMatches = content.match(/KND[A-Z0-9]{14}/g) || [];
    console.log(`Found ${vinMatches.length} real VINs:`, Array.from(new Set(vinMatches)));
  } catch (e: any) {
    console.error(`Fetch error: ${e.message}`);
  } finally {
    await browser.close();
  }
}

fetchRealVin();
