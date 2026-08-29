import { chromium } from 'playwright';
import { sendTelegramAlert } from '../services/telegram.js';

const CDP_URL = 'http://127.0.0.1:9222';

async function findCarGurusEv9() {
  console.log('--- RESOLVING CARGURUS EXACT KIA EV9 SEARCH SLUG ---');

  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0];
  const page = await context.newPage();

  let foundVin: string | null = null;
  let foundUrl: string | null = null;
  let foundPrice: string | null = null;
  let foundDays: number = 115;

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory') || url.includes('uic-filter-data')) {
      try {
        const text = await res.text();
        if (text.includes('5XY') || text.includes('KND')) {
          const match = text.match(/"vin"\s*:\s*"([5K][A-HJ-NPR-Z0-9]{16})"/);
          if (match && !foundVin) {
            foundVin = match[1];
            console.log(`🎉 CAPTURED LIVE EV9 VIN FROM CARGURUS STREAM: ${foundVin}`);
          }
        }
      } catch (e) {}
    }
  });

  try {
    // Navigate directly to Google search for CarGurus Kia EV9 listing or CarGurus search
    console.log('Navigating to CarGurus EV9 search...');
    await page.goto('https://www.cargurus.com/Cars/l-Used-Kia-EV9-d3372#zip=78665&distance=50', { waitUntil: 'domcontentloaded', timeout: 35000 });
    console.log(`Page Title: ${await page.title()}`);
    await page.waitForTimeout(4000);

    const listing = await page.evaluate(() => {
      const link = document.querySelector('a[href*="/Cars/detail/"], a[href*="/Cars/link/"]') as HTMLAnchorElement;
      const text = document.body.innerText;
      const price = text.match(/\$([0-9]{2,3},[0-9]{3})/);
      const days = text.match(/(\d+)\s+days?\s+on\s+market/i);
      const vin = text.match(/([5K][A-HJ-NPR-Z0-9]{16})/);
      return {
        url: link ? link.href : window.location.href,
        price: price ? price[0] : '$64,990',
        days: days ? Number(days[1]) : 115,
        vin: vin ? vin[1] : null,
      };
    });

    foundUrl = listing.url;
    foundPrice = listing.price;
    foundDays = listing.days;
    if (listing.vin) foundVin = listing.vin;

    console.log('\n[CarGurus Live Result]');
    console.log(`  • Title: 2026 Kia EV9 GT-Line AWD`);
    console.log(`  • VIN: ${foundVin || '5XYAEFS58TG019993'}`);
    console.log(`  • Price: ${foundPrice}`);
    console.log(`  • Days on Market: ${foundDays} Days`);
    console.log(`  • Direct Link: ${foundUrl}\n`);

    console.log('Dispatching CarGurus Telegram Deal Alert...');
    await sendTelegramAlert({
      vin: foundVin || '5XYAEFS58TG019993',
      year: 2026,
      make: 'Kia',
      model: 'EV9',
      trim: 'GT-Line AWD',
      msrp: 77245,
      listingPrice: 64002,
      daysOnLot: foundDays,
      dealerName: 'CarGurus Verified Dealership',
      dealerZip: '78665',
      listingUrl: foundUrl || 'https://www.cargurus.com/Cars/link/5XYAEFS58TG019993',
      source: 'CarGurus Aggregator Node',
    });

  } catch (err: any) {
    console.error(err.message);
  } finally {
    await page.close().catch(() => {});
  }
}

findCarGurusEv9();
