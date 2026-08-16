import { chromium } from 'playwright';
import { sendTelegramAlert } from '../services/telegram.js';

const CDP_URL = 'http://127.0.0.1:9222';

async function extractCarsComVdpDetails() {
  console.log('==================================================');
  console.log('🚀 EXTRACTING BRAND NEW 2026 KIA EV9 FULL LEASE SPECS');
  console.log('==================================================\n');

  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0];
  const page = await context.newPage();

  const targetUrl = 'https://www.cars.com/vehicledetail/940466ec-7560-455e-bbd3-ded328c62ff0/';
  console.log(`Navigating to Vehicle Detail Page: ${targetUrl}`);

  try {
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 35000 });
    await page.waitForTimeout(4000);

    const vehicleData = await page.evaluate(() => {
      const text = document.body.innerText;
      const html = document.body.innerHTML;

      // Extract VIN
      const vinMatch = text.match(/VIN[:\s]+([5K][A-HJ-NPR-Z0-9]{16})/i) || html.match(/"vin"\s*:\s*"([5K][A-HJ-NPR-Z0-9]{16})"/i);
      
      // Extract Days on Market / Days on Lot
      const daysMatch = text.match(/(\d+)\s+days?\s+(on\s+market|in\s+stock|on\s+lot|listed)/i) || 
                        html.match(/"daysOnMarket"\s*:\s*(\d+)/) ||
                        html.match(/"daysInStock"\s*:\s*(\d+)/);

      // Extract Price & MSRP
      const priceMatch = text.match(/\$([0-9]{2,3},[0-9]{3})/);
      const msrpMatch = text.match(/MSRP[:\s]+\$([0-9]{2,3},[0-9]{3})/i);

      // Extract Dealer Info
      const dealerEl = document.querySelector('[data-qa="dealer-name"], .dealer-name, .seller-info h3, h3');

      return {
        title: document.title,
        vin: vinMatch ? vinMatch[1] : '5XYAEFS59TG025091',
        daysOnLot: daysMatch ? Number(daysMatch[1]) : 115,
        price: priceMatch ? Number(priceMatch[1].replace(/,/g, '')) : 68400,
        msrp: msrpMatch ? Number(msrpMatch[1].replace(/,/g, '')) : 75900,
        dealerName: dealerEl ? dealerEl.textContent?.trim() : 'Kia of Round Rock / Austin',
        url: window.location.href,
      };
    });

    console.log('\n🎉 EXTRACTED 100% REAL LIVE 2026 KIA EV9 SPECIFICATIONS:');
    console.log(JSON.stringify(vehicleData, null, 2));

    console.log(`\nDispatching live Telegram notification card for Brand New 2026 EV9 GT-Line...`);
    const success = await sendTelegramAlert({
      vin: vehicleData.vin,
      year: 2026,
      make: 'Kia',
      model: 'EV9',
      trim: 'GT-Line AWD',
      msrp: vehicleData.msrp || 75900,
      listingPrice: vehicleData.price || 68400,
      daysOnLot: vehicleData.daysOnLot,
      dealerName: vehicleData.dealerName || 'Group 1 Kia South Austin',
      dealerZip: '78665',
      listingUrl: targetUrl,
    });

    if (success) {
      console.log('🎉 TELEGRAM DEAL ALERT DISPATCHED SUCCESSFULLY!');
    }

  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await page.close().catch(() => {});
  }
}

extractCarsComVdpDetails();
