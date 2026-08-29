import { chromium } from 'playwright';

async function fastCdpExtract() {
  console.log('Connecting to Chrome on 127.0.0.1:9222...');
  const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  const context = browser.contexts()[0];
  
  // Find the CarGurus or Cars.com page
  let targetPage = context.pages().find(p => p.url().includes('cargurus.com') || p.url().includes('cars.com'));
  if (!targetPage) {
    targetPage = await context.newPage();
  }

  console.log(`Using target page: ${targetPage.url()}`);

  const results: any[] = [];

  targetPage.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory') || url.includes('listing')) {
      try {
        const text = await res.text();
        if (text.includes('5XY') || text.includes('KND') || text.includes('EV9')) {
          console.log(`[Captured Live Stream (${text.length}b)]: ${url.substring(0, 80)}`);
          
          // Regex extraction of all VINs and Prices
          const vinMatches = text.match(/"vin"\s*:\s*"([5K][A-HJ-NPR-Z0-9]{16})"/g) || [];
          for (const m of vinMatches) {
            const vin = (m.match(/"vin"\s*:\s*"([5K][A-HJ-NPR-Z0-9]{16})"/) || [])[1];
            if (vin && !results.some(r => r.vin === vin)) {
              results.push({
                vin,
                source: 'CarGurus/Cars.com CDP Stream',
                scrapedAt: new Date().toISOString()
              });
            }
          }
        }
      } catch {}
    }
  });

  // Navigate to Kia EV9 search with exact query parameters on CarGurus
  const searchUrl = 'https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?zip=78665&distance=50&inventoryType=NEW&makeId=m39&modelId=d3372';
  console.log(`Navigating to: ${searchUrl}`);
  
  try {
    await targetPage.goto(searchUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
    console.log(`Page title: "${await targetPage.title()}"`);
    
    await targetPage.waitForTimeout(5000);
    await targetPage.evaluate(() => window.scrollBy(0, 1500));
    await targetPage.waitForTimeout(4000);

    const domData = await targetPage.evaluate(() => {
      const text = document.body.innerText || '';
      const prices = text.match(/\$([0-9]{2,3},[0-9]{3})/g) || [];
      const vins = Array.from(new Set(text.match(/([5K][A-HJ-NPR-Z0-9]{16})/g) || []));
      const days = text.match(/(\d+)\s+days?\s+(on\s+market|on\s+lot)/gi) || [];
      return { textPreview: text.substring(0, 200), prices, vins, days };
    });

    console.log('\n--- EXTRACTED DATA ---');
    console.log('Prices:', domData.prices.slice(0, 10));
    console.log('VINs:', domData.vins.slice(0, 10));
    console.log('Days on Lot:', domData.days.slice(0, 10));
    console.log('Stream Captured VINs:', results.length);

  } catch (err: any) {
    console.error('Extraction error:', err.message);
  }

  console.log('\nDone.');
  process.exit(0);
}

fastCdpExtract();
