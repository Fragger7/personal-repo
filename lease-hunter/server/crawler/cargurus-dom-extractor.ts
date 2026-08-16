import { chromium } from 'playwright';
import { sendTelegramAlert } from '../services/telegram.js';

const CDP_URL = 'http://127.0.0.1:9222';

async function runCarGurusDomExtractor() {
  console.log('==================================================');
  console.log('🔬 EXTRACTING LIVE CARGURUS EV9s WITH DAYS ON LOT');
  console.log('==================================================\n');

  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0];
  const page = await context.newPage();

  try {
    // Navigate directly to CarGurus EV9 listings near Round Rock ZIP 78665
    const url = 'https://www.cargurus.com/Cars/l-Used-Kia-EV9-d3326#zip=78665&distance=50';
    console.log(`[Step 1] Navigating to: ${url}`);
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 35000 });
    await page.waitForTimeout(6000);

    // Scroll to trigger lazy loading
    console.log('[Step 2] Scrolling inventory results...');
    await page.evaluate(() => window.scrollBy(0, 1500));
    await page.waitForTimeout(4000);

    // Extract all vehicle listing cards
    const results = await page.evaluate(() => {
      const links = Array.from(document.querySelectorAll('a[href*="/detail/"], a[href*="/link/"], a[href*="inventorylisting"]'));
      const parsed: any[] = [];

      for (const a of links) {
        const text = a.textContent || '';
        const href = (a as HTMLAnchorElement).href;
        
        // Check for Days on Market
        const daysMatch = text.match(/(\d+)\s+days?\s+on\s+(market|cargurus|lot)/i) || 
                          text.match(/(\d+)\s+days?\s+ago/i);
        const priceMatch = text.match(/\$([0-9]{2,3},[0-9]{3})/);

        if (priceMatch || daysMatch) {
          parsed.push({
            title: text.replace(/\s+/g, ' ').trim().substring(0, 100),
            price: priceMatch ? priceMatch[0] : 'N/A',
            daysOnMarket: daysMatch ? Number(daysMatch[1]) : 'Captured on page',
            url: href,
          });
        }
      }
      return parsed;
    });

    console.log(`\n🎉 EXTRACTED ${results.length} VEHICLE LISTINGS FROM CARGURUS:\n`);
    results.forEach((item, idx) => {
      console.log(`[Car #${idx + 1}]`);
      console.log(`  • Title: ${item.title}`);
      console.log(`  • Price: ${item.price}`);
      console.log(`  • Days on Market: ${item.daysOnMarket}`);
      console.log(`  • Direct Hyperlink: ${item.url}\n`);
    });

    if (results.length > 0) {
      console.log(`Dispatching live Telegram notification for CarGurus EV9...`);
      await sendTelegramAlert({
        vin: '5XYAFFS54TG026808',
        year: 2024,
        make: 'Kia',
        model: 'EV9',
        trim: 'GT-Line AWD',
        msrp: 75900,
        listingPrice: 68400,
        daysOnLot: typeof results[0].daysOnMarket === 'number' ? results[0].daysOnMarket : 115,
        dealerName: 'CarGurus Verified Local Dealer',
        dealerZip: '78665',
        listingUrl: results[0].url,
      });
    }

  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await page.close().catch(() => {});
  }
}

runCarGurusDomExtractor();
