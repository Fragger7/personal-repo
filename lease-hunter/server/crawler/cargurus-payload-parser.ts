import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

async function captureAndParseCarGurus() {
  console.log('Attaching to Chrome on 127.0.0.1:9222...');
  const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  const context = browser.contexts()[0];
  const page = context.pages().find(p => p.url().includes('cargurus.com')) || await context.newPage();

  let capturedJsonPayload: any = null;

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') && url.includes('makeId=m39')) {
      try {
        const text = await res.text();
        if (text.length > 50000) {
          console.log(`[Captured Target Payload (${text.length} bytes)]: ${url.substring(0, 80)}`);
          capturedJsonPayload = JSON.parse(text);
        }
      } catch (err: any) {
        console.error('Failed to parse response stream:', err.message);
      }
    }
  });

  const searchUrl = 'https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?zip=78665&distance=50&inventoryType=NEW&makeId=m39&modelId=d3372';
  console.log(`Navigating to: ${searchUrl}`);
  
  await page.goto(searchUrl, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(6000);

  if (capturedJsonPayload) {
    const outputPath = path.join(process.cwd(), 'data', 'cargurus-sample-payload.json');
    fs.writeFileSync(outputPath, JSON.stringify(capturedJsonPayload, null, 2));
    console.log(`Saved sample payload to ${outputPath}`);

    // Inspect top level keys
    console.log('Payload Root Keys:', Object.keys(capturedJsonPayload));
    
    // Look for listings
    const listings = capturedJsonPayload.listings || capturedJsonPayload.results || capturedJsonPayload.cards || [];
    console.log(`Found ${listings.length} listings in JSON structure.`);

    if (listings.length > 0) {
      console.log('\n--- SAMPLE PARSED CARGURUS LISTINGS ---');
      listings.slice(0, 5).forEach((item: any, idx: number) => {
        console.log(`[#${idx + 1}]`);
        console.log(`  VIN: ${item.vin}`);
        console.log(`  Title: ${item.listingTitle || item.title || item.carYear + ' ' + item.makeName + ' ' + item.modelName}`);
        console.log(`  Trim: ${item.trimName || item.trim}`);
        console.log(`  Price: $${item.price}`);
        console.log(`  MSRP: $${item.msrp || item.originalPrice || 'N/A'}`);
        console.log(`  Days on Market: ${item.daysOnMarket || item.daysOnLot} Days`);
        console.log(`  Deal Rating: ${item.dealRating || item.dealScore || 'N/A'}`);
        console.log(`  Dealer: ${item.sellerName || item.dealerName} (${item.city}, ${item.state})`);
        console.log(`  VDP URL: https://www.cargurus.com/Cars/detail/${item.id || item.vin}\n`);
      });
    }
  } else {
    console.log('No JSON payload captured yet.');
  }

  process.exit(0);
}

captureAndParseCarGurus();
