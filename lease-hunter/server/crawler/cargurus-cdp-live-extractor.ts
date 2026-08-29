import { chromium } from 'playwright';

async function runCarGurusCdpExtractor() {
  console.log('==================================================');
  console.log('🚗 CARGURUS CDP EXTRACTION NODE (Target: d3266 - Kia EV9)');
  console.log('==================================================\n');

  let browser;
  try {
    browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  } catch (err: any) {
    console.error('Failed to connect to Chrome on port 9222:', err.message);
    process.exit(1);
  }

  const contexts = browser.contexts();
  const page = contexts[0].pages()[0] || await contexts[0].newPage();

  const capturedCars: any[] = [];

  // Listen for CarGurus JSON network payloads
  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('/search') || url.includes('/inventory') || url.includes('/listings') || url.includes('json') || url.includes('d3266')) {
      try {
        const text = await res.text();
        if (text.includes('5XY') || text.includes('EV9') || text.includes('daysOnMarket') || text.includes('listingTitle')) {
          console.log(`[XHR Intercepted]: ${url.substring(0, 90)}... (${text.length} bytes)`);
          
          try {
            const data = JSON.parse(text);
            const listings = data.listings || data.results || data.cards || (Array.isArray(data) ? data : []);
            for (const item of listings) {
              if (item.vin || (item.listingTitle && item.listingTitle.includes('EV9'))) {
                const vin = item.vin || 'UNKNOWN_VIN';
                if (!capturedCars.some(c => c.vin === vin)) {
                  capturedCars.push({
                    vin: vin,
                    year: item.carYear || 2026,
                    make: 'Kia',
                    model: 'EV9',
                    trim: item.trimName || item.trim || 'EV9',
                    price: item.price || item.expectedPrice || item.sellingPrice,
                    msrp: item.msrp || item.originalPrice || item.price,
                    daysOnLot: item.daysOnMarket || item.daysOnLot || 0,
                    dealerName: item.sellerName || item.dealerName || 'Local Kia Dealer',
                    dealerZip: item.zip || '78665',
                    dealRating: item.dealRating || item.dealTag || 'Great Deal',
                    listingUrl: item.vdpUrl ? `https://www.cargurus.com${item.vdpUrl}` : `https://www.cargurus.com/Cars/link/${vin}`,
                    source: 'CarGurus CDP Direct',
                    scrapedAt: new Date().toISOString()
                  });
                }
              }
            }
          } catch {}
        }
      } catch {}
    }
  });

  const targetSearchUrl = 'https://www.cargurus.com/Cars/new/Kia-EV9-d3266#zip=78665&distance=50';
  console.log(`Navigating attached Chrome to: ${targetSearchUrl}`);
  
  try {
    await page.goto(targetSearchUrl, { waitUntil: 'domcontentloaded', timeout: 35000 });
    console.log(`Page Loaded! Title: "${await page.title()}"`);
    
    // Wait for JS hydration and scroll down
    await page.waitForTimeout(6000);
    await page.evaluate(() => window.scrollBy(0, 1500));
    await page.waitForTimeout(5000);
    await page.evaluate(() => window.scrollBy(0, 2000));
    await page.waitForTimeout(4000);

    // Extract listing cards from DOM
    const domListings = await page.evaluate(() => {
      const results: any[] = [];
      const cards = document.querySelectorAll('a[href*="/Cars/detail/"], a[href*="/Cars/link/"], [data-cg-ft="car-blade"], [data-testid="listing-blade"]');
      
      cards.forEach((card) => {
        const text = card.textContent || '';
        const link = (card.tagName === 'A' ? card : card.querySelector('a')) as HTMLAnchorElement;
        const priceMatch = text.match(/\$([0-9]{2,3},[0-9]{3})/);
        const daysMatch = text.match(/(\d+)\s+days?\s+on\s+market/i) || text.match(/(\d+)\s+days?\s+on\s+lot/i);
        const dealRatingMatch = text.match(/(GREAT DEAL|GOOD DEAL|FAIR DEAL|HIGH PRICE)/i);
        const vinMatch = text.match(/([5K][A-HJ-NPR-Z0-9]{16})/);
        
        if (text.includes('EV9') || text.includes('Kia') || priceMatch) {
          results.push({
            title: text.substring(0, 100).replace(/\s+/g, ' ').trim(),
            price: priceMatch ? priceMatch[0] : null,
            daysOnMarket: daysMatch ? parseInt(daysMatch[1], 10) : null,
            dealRating: dealRatingMatch ? dealRatingMatch[0] : null,
            vin: vinMatch ? vinMatch[0] : null,
            url: link ? link.href : null
          });
        }
      });

      return results;
    });

    console.log(`\n==================================================`);
    console.log(`📊 TOTAL DOM CARDS FOUND: ${domListings.length}`);
    console.log(`📊 TOTAL XHR CAPTURED LISTINGS: ${capturedCars.length}`);
    console.log(`==================================================\n`);

    if (domListings.length > 0) {
      console.log('Sample DOM Cards:');
      domListings.slice(0, 10).forEach((item, i) => {
        console.log(`[#${i + 1}] Price: ${item.price} | Deal: ${item.dealRating} | Days: ${item.daysOnMarket} | VIN: ${item.vin} | ${item.title}`);
      });
    }

    if (capturedCars.length > 0) {
      console.log('\nSample XHR Captured Cars:');
      capturedCars.slice(0, 10).forEach((car, i) => {
        console.log(`[#${i + 1}] VIN: ${car.vin} | ${car.trim} | $${car.price} | ${car.daysOnLot} Days | ${car.dealRating} | ${car.dealerName}`);
      });
    }

  } catch (err: any) {
    console.error('Error during scraping cycle:', err.message);
  }
}

runCarGurusCdpExtractor();
