import { chromium } from 'playwright';

async function fetchCarsCom() {
  console.log('--- SCANNING CARS.COM FOR REAL LIVE KIA EV9 LISTINGS ---');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    const url = 'https://www.cars.com/shopping/results/?stock_type=all&makes[]=kia&models[]=kia-ev9&zip=78665&maximum_distance=50';
    console.log(`Navigating to: ${url}`);
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(4000);

    const content = await page.content();
    // Extract real VINs and real Cars.com listing URLs
    const vinMatches = content.match(/KND[A-Z0-9]{14}/g) || [];
    const listingUrls = content.match(/\/vehicledetail\/[a-z0-9\-]+\//g) || [];

    const uniqueVins = Array.from(new Set(vinMatches));
    const uniqueUrls = Array.from(new Set(listingUrls)).map(u => `https://www.cars.com${u}`);

    console.log(`\n🎉 SUCCESS! Discovered ${uniqueVins.length} real VINs and ${uniqueUrls.length} real vehicle detail links:`);
    console.log('Real VINs:', uniqueVins.slice(0, 5));
    console.log('Real Listing URLs:', uniqueUrls.slice(0, 5));
  } catch (e: any) {
    console.error(`Error: ${e.message}`);
  } finally {
    await browser.close();
  }
}

fetchCarsCom();
