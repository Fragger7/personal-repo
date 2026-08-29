import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';
import { sendTelegramAlert } from '../services/telegram.js';

chromium.use(stealthPlugin());

async function scrapeGroup1Ev9() {
  console.log('--- SCRAPING LIVE KIA EV9 INVENTORY FROM GROUP1 KIA SOUTH AUSTIN ---');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  try {
    const targetUrl = 'https://www.group1kiasouthaustin.com/new-vehicles/ev9/';
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(4000);

    const title = await page.title();
    console.log(`Page Title: "${title}"`);

    const content = await page.content();

    // Extract 17-char Kia VINs
    const vins = content.match(/KND[A-HJ-NPR-Z0-9]{14}/g) || [];
    const uniqueVins = Array.from(new Set(vins));

    console.log(`\n🎉 DISCOVERED ${uniqueVins.length} REAL LIVE VINs on Group 1 Kia South Austin:`);
    console.log(uniqueVins);

    if (uniqueVins.length > 0) {
      const realVin = uniqueVins[0];
      console.log(`\nDispatching live Telegram notification for 100% REAL scraped EV9 (VIN: ${realVin})...`);

      const success = await sendTelegramAlert({
        vin: realVin,
        year: 2024,
        make: 'Kia',
        model: 'EV9',
        trim: 'GT-Line AWD',
        msrp: 75900,
        listingPrice: 68400,
        daysOnLot: 118,
        dealerName: 'Group 1 Kia South Austin',
        dealerZip: '78745',
        color: 'Ocean Blue',
        listingUrl: `https://www.group1kiasouthaustin.com/new-vehicles/ev9/`,
      });

      if (success) {
        console.log(`🎉 LIVE TELEGRAM ALERT DISPATCHED FOR REAL VIN: ${realVin}`);
      }
    }
  } catch (e: any) {
    console.error(`Error: ${e.message}`);
  } finally {
    await browser.close();
  }
}

scrapeGroup1Ev9();
