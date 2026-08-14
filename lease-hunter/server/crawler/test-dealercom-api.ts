import { chromium } from 'playwright';

async function testDealerComApi() {
  console.log('Testing Dealer.com Direct Inventory JSON API...');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  const testEndpoints = [
    'https://www.kiaofsouthaustin.com/api/widget/ws-inventory-v2/getInventoryList?compositeType=new&model=EV9',
    'https://www.kiaofsouthaustin.com/new-inventory/index.htm?search=EV9',
  ];

  for (const targetUrl of testEndpoints) {
    try {
      console.log(`\nFetching: ${targetUrl}`);
      const res = await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 15000 });
      console.log(`HTTP Status: ${res?.status()}`);
      
      const content = await page.content();
      const ev9Vins = content.match(/KNDE[ST][A-HJ-NPR-Z0-9]{13}/g) || [];
      console.log(`Found ${ev9Vins.length} EV9 VINs:`, Array.from(new Set(ev9Vins)));
    } catch (e: any) {
      console.error(`Error: ${e.message}`);
    }
  }

  await browser.close();
  console.log('Test complete.');
}

testDealerComApi();
