import { chromium } from 'playwright';

export async function testCarEdgeNextData(zip: string = '78665', distance: number = 50, minDays: number = 100) {
  console.log(`==================================================`);
  console.log(`🔎 CAREDGE NEXT.JS STATE EXTRACTION TEST`);
  console.log(`Target: Kia EV9 within ${distance}mi of ZIP ${zip} (Days on Lot > ${minDays})`);
  console.log(`==================================================\n`);

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    const targetUrl = `https://caredge.com/cars?make=Kia&model=EV9&zip=${zip}&radius=${distance}`;
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(4000);

    // Extract window.__NEXT_DATA__ Next.js JSON state
    const nextDataJson = await page.evaluate(() => {
      const script = document.getElementById('__NEXT_DATA__');
      return script ? script.innerText : null;
    });

    if (nextDataJson) {
      console.log(`🎉 SUCCESS! Extracted Next.js __NEXT_DATA__ JSON state (${nextDataJson.length} bytes)!`);
      const nextData = JSON.parse(nextDataJson);
      
      // Parse cars array from pageProps
      const pageProps = nextData?.props?.pageProps || {};
      const listings = pageProps?.listings || pageProps?.initialListings || pageProps?.cars || [];

      console.log(`Total listings in Next.js dataset: ${listings.length}`);

      const qualified = listings.filter((item: any) => Number(item.daysOnLot || item.daysOnMarket || 0) > minDays);
      console.log(`Qualified listings (Days on Lot > ${minDays}): ${qualified.length}`);

      console.log('\n--- SAMPLE EXTRACTED CAREDGE VEHICLES ---');
      listings.slice(0, 5).forEach((item: any, idx: number) => {
        console.log(`[Item #${idx + 1}] VIN: ${item.vin || 'N/A'} | Days: ${item.daysOnLot || item.daysOnMarket || 'N/A'} | Price: $${item.price || item.msrp || 'N/A'}`);
      });
    } else {
      console.log('No __NEXT_DATA__ script tag found on page.');
    }
  } catch (e: any) {
    console.error(`Error: ${e.message}`);
  } finally {
    await browser.close();
  }
}

testCarEdgeNextData('78665', 50, 100);
