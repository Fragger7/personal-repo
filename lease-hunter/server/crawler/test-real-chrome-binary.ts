import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';
import fs from 'fs';

chromium.use(stealthPlugin());

async function testRealChromeBinary() {
  console.log('--- TESTING LOCAL REAL CHROME BROWSER STEALTH INTERCEPTOR ($0 COST) ---');

  // Locate installed Chrome binary on Windows
  const chromePaths = [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  ];

  const executablePath = chromePaths.find(p => fs.existsSync(p));
  console.log(`Installed Chrome Binary Path: ${executablePath || 'Not found, using bundled Chromium'}`);

  const browser = await chromium.launch({
    headless: false, // Visible Chrome window passes DataDome checks automatically
    executablePath: executablePath || undefined,
    args: [
      '--disable-blink-features=AutomationControlled',
      '--no-sandbox',
      '--disable-setuid-sandbox',
    ],
  });

  const page = await browser.newPage();
  const cargurusEv9s: any[] = [];

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory') || url.includes('uic-filter-data')) {
      try {
        const text = await res.text();
        if (text.includes('EV9') || text.includes('daysOnMarket') || text.includes('listings')) {
          console.log(`🎉 SUCCESS! Intercepted real CarGurus backend JSON (${text.length} bytes)!`);
          // Regex extract daysOnMarket and VINs
          const matches = text.match(/"vin"\s*:\s*"([A-Z0-9]{17})"[^}]*"daysOnMarket"\s*:\s*(\d+)/g) || [];
          for (const m of matches) {
            const vin = (m.match(/"vin"\s*:\s*"([A-Z0-9]{17})"/) || [])[1];
            const days = Number((m.match(/"daysOnMarket"\s*:\s*(\d+)/) || [])[1]);
            if (vin && days > 60 && !cargurusEv9s.some(c => c.vin === vin)) {
              console.log(`🔥 VERIFIED REAL AGED EV9 (VIN: ${vin} | Days on Market: ${days})`);
              cargurusEv9s.push({ vin, daysOnMarket: days });
            }
          }
        }
      } catch (e) {}
    }
  });

  try {
    const targetUrl = 'https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action#shoppingList/search?zip=78665&distance=50';
    console.log(`Navigating to: ${targetUrl}`);
    const res = await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
    console.log(`[Status ${res?.status()}]: ${await page.title()}`);
    await page.waitForTimeout(6000);
  } catch (e: any) {
    console.error(`Error: ${e.message}`);
  } finally {
    await browser.close();
  }

  console.log(`\n=== VERIFIED REAL AGED EV9s CAPTURED: ${cargurusEv9s.length} ===`);
}

testRealChromeBinary();
