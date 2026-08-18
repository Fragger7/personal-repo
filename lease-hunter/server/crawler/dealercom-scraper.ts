import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';

chromium.use(stealthPlugin());

export async function scrapeDealerCom(dealerUrl: string, make: string = 'Kia', model: string = 'EV9') {
  console.log(`==================================================`);
  console.log(`🔍 DEALER.COM LIVE SCRAPER: ${dealerUrl}`);
  console.log(`==================================================\n`);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 720 },
  });

  const page = await context.newPage();
  const results: any[] = [];
  let interceptedData: any = null;

  page.on('response', async (res) => {
    const url = res.url();
    if ((url.includes('api') || url.includes('inventory') || url.includes('search') || url.includes('graphql')) && res.status() === 200) {
      try {
        const contentType = res.headers()['content-type'] || '';
        if (contentType.includes('application/json')) {
            const data = await res.json();
            // Try to find an array of vehicles in the JSON
            const potentialVehicles = data.vehicles || data.results || data.inventory || data.pageInfo?.inventory || data.data?.vehicles || data.hits || [];
            if (Array.isArray(potentialVehicles) && potentialVehicles.length > 0 && potentialVehicles[0].vin) {
               interceptedData = potentialVehicles;
            }
        }
      } catch (e) {}
    }
  });

  try {
    const targetUrl = `${dealerUrl}/new-inventory/index.htm?make=${make}&model=${model}`;
    console.log(`Navigating to: ${targetUrl}`);
    
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
    
    // Fallback: Dealer.com sites often embed their inventory in window.DDC.pageInfo.inventory
    if (!interceptedData) {
        interceptedData = await page.evaluate(() => {
           const ddc = (window as any).DDC;
           if (ddc && ddc.pageInfo && ddc.pageInfo.inventory) {
               return ddc.pageInfo.inventory;
           }
           return null;
        });
    }

    if (interceptedData && Array.isArray(interceptedData)) {
        console.log(`Found ${interceptedData.length} vehicles via Dealer.com scraper.`);
        const standardResults = interceptedData.map((car: any) => ({
          source: 'Dealer.com',
          title: car.title || `${make} ${model} ${car.trim}`,
          year: car.year || new Date().getFullYear(),
          trim: car.trim || 'Unknown',
          vin: car.vin || 'UNKNOWN',
          daysOnLot: car.daysInStock || car.daysOnLot || 0,
          listingPrice: car.pricing?.msrp || car.pricing?.internetPrice || car.msrp || car.price || 0,
          msrp: car.pricing?.msrp || car.msrp || car.price || 0,
          dealerName: dealerUrl.replace('https://www.', '').replace('http://www.', '').split('.com')[0],
          dealerZip: 'Unknown',
          listingUrl: car.link ? (car.link.startsWith('http') ? car.link : `${dealerUrl}${car.link}`) : targetUrl,
          inTransit: car.inTransit || false
        }));
        results.push(...standardResults);
    } else {
        console.log('No structured JSON data intercepted or found in window.DDC.');
    }

  } catch (err: any) {
    console.error(`Error scraping ${dealerUrl}: ${err.message}`);
  } finally {
    await browser.close();
  }

  return {
    status: 'success',
    results
  };
}

import { pathToFileURL } from 'url';

// Allow direct execution for testing
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
    scrapeDealerCom('https://www.kiaofroundrock.com')
      .then(res => console.log(JSON.stringify(res.results, null, 2)))
      .catch(console.error);
}
