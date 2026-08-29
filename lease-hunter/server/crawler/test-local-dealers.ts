import { chromium } from 'playwright';

async function testLocalDealers() {
  console.log('Testing local dealer URL formats...');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  const testUrls = [
    { name: 'Round Rock Kia', url: 'https://www.roundrockkia.com/searchnew.aspx?Model=EV9' },
    { name: 'Kia of South Austin', url: 'https://www.kiaofsouthaustin.com/searchnew.aspx?Model=EV9' },
    { name: 'Covert Buick GMC Austin (Kia check)', url: 'https://www.covertford.com' }
  ];

  for (const dealer of testUrls) {
    try {
      console.log(`\nNavigating to: ${dealer.name} (${dealer.url})`);
      await page.goto(dealer.url, { waitUntil: 'domcontentloaded', timeout: 15000 });
      const title = await page.title();
      console.log(`[Success] Page Title: ${title}`);
      
      const content = await page.content();
      const vins = content.match(/KND[A-Z0-9]{14}/g) || [];
      console.log(`Found ${vins.length} VINs on page!`);
    } catch (e: any) {
      console.error(`[Error] ${dealer.name}: ${e.message}`);
    }
  }

  await browser.close();
  console.log('Test complete.');
}

testLocalDealers();
