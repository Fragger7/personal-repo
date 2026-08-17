import { chromium } from 'playwright';
import { exec } from 'child_process';

const CDP_URL = 'http://127.0.0.1:9222';
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PROFILE_DIR = 'C:\\Development\\Apps\\Lease Hunter\\chrome-debug-stable';

async function ensureChromeDebug(): Promise<void> {
  // Check if CDP is already available
  try {
    const response = await fetch(`${CDP_URL}/json/version`);
    if (response.ok) {
      console.log('[Chrome] CDP port 9222 already active');
      return;
    }
  } catch (e) {
    // Not running, need to launch
  }

  console.log('[Chrome] Launching Chrome with remote debugging...');
  
  const args = [
    '--remote-debugging-port=9222',
    '--remote-allow-origins=*',
    `--user-data-dir=${PROFILE_DIR}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-background-networking',
    '--disable-component-update',
    '--disable-default-apps',
    '--disable-extensions',
    '--disable-sync',
    '--metrics-recording-only',
    '--safebrowsing-disable-auto-update',
  ];

  const child = exec(`"${CHROME_PATH}" ${args.join(' ')}`, { windowsHide: true });
  child.unref();

  // Wait for CDP port to become available
  for (let i = 0; i < 30; i++) {
    await new Promise(r => setTimeout(r, 1000));
    try {
      const r = await fetch(`${CDP_URL}/json/version`);
      if (r.ok) {
        console.log(`[Chrome] CDP active after ${i + 1}s`);
        return;
      }
    } catch (e) {}
    console.log(`[Chrome] Waiting for CDP... (${i + 1}/30)`);
  }
  throw new Error('Chrome CDP port 9222 never became available');
}

async function scrapeCarGurusNewEv9() {
  console.log('================================================================');
  console.log('🚗 CARGURUS NEW KIA EV9 SCRAPER (CDP ARCHITECTURE)');
  console.log('================================================================\n');

  // Ensure Chrome is running with CDP
  await ensureChromeDebug();

  // Now connect via Playwright
  console.log('[Playwright] Connecting to Chrome via CDP...');
  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0];
  const page = await context.newPage();

  const capturedApiData: any[] = [];

  // Intercept XHR responses for listing/inventory data
  page.on('response', async (res) => {
    const url = res.url();
    try {
      if (url.includes('/api/') || url.includes('inventory') || url.includes('search') || url.includes('listing')) {
        const text = await res.text();
        if (text.includes('daysOnMarket') || text.includes('days_on_market')) {
          console.log(`\n[XHR Intercept] Found Days on Market data in: ${url.substring(0, 100)} (${text.length} bytes)`);
          capturedApiData.push({ url, data: text.substring(0, 2000) });
        }
      }
    } catch (e) {}
  });

  try {
    // Try multiple URL patterns for new Kia EV9 on CarGurus
    const urls = [
      'https://www.cargurus.com/Cars/new/nl-New-Kia-EV9-sp102035#zip=78665&distance=50',
      'https://www.cargurus.com/Cars/l-Used-Kia-EV9-sp102035#zip=78665&distance=50',
      'https://www.cargurus.com/shop#inventoryType=NEW&make=Kia&model=EV9&zip=78665&distance=50',
    ];

    for (const url of urls) {
      console.log(`\n--- Navigating: ${url} ---`);
      try {
        const resp = await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
        const status = resp?.status();
        const title = await page.title();
        const finalUrl = page.url();
        console.log(`[HTTP ${status}] Title: "${title}"`);
        console.log(`[Final URL] ${finalUrl}`);

        // Check if we landed on an EV9 page
        const bodyText = await page.evaluate(() => document.body.innerText.substring(0, 500));
        const hasEv9 = bodyText.toLowerCase().includes('ev9') || title.toLowerCase().includes('ev9');
        console.log(`[Body Preview] ${bodyText.substring(0, 200)}`);
        console.log(`[Contains EV9 content?] ${hasEv9}`);

        if (hasEv9) {
          // Scroll to trigger lazy loading
          await page.evaluate(() => window.scrollBy(0, 2000));
          await page.waitForTimeout(3000);
          await page.evaluate(() => window.scrollBy(0, 2000));
          await page.waitForTimeout(2000);

          // Extract listings from DOM
          const results = await page.evaluate(() => {
            const listings: any[] = [];
            
            // Try multiple selector strategies
            const selectors = [
              'a[href*="/Cars/detail/"]',
              'a[href*="vehicledetail"]',
              '[data-cg-ft="srp-listing-blade"]',
              'article',
              '[class*="listing"]',
              '[class*="result"]',
            ];
            
            const elements = new Set<Element>();
            for (const sel of selectors) {
              document.querySelectorAll(sel).forEach(el => elements.add(el));
            }

            for (const el of elements) {
              const text = el.textContent || '';
              const href = (el as HTMLAnchorElement).href || el.querySelector('a')?.href || '';
              
              if (text.toLowerCase().includes('ev9') || href.toLowerCase().includes('ev9')) {
                const daysMatch = text.match(/(\d+)\s+days?\s+on\s+(market|lot|cargurus)/i);
                const priceMatch = text.match(/\$([0-9]{2,3},[0-9]{3})/);
                const yearTrimMatch = text.match(/(202[4-6])\s+Kia\s+EV9\s*([A-Za-z\s\-]*)/i);
                
                listings.push({
                  title: text.replace(/\s+/g, ' ').trim().substring(0, 120),
                  url: href,
                  daysOnMarket: daysMatch ? Number(daysMatch[1]) : null,
                  price: priceMatch ? priceMatch[0] : null,
                  year: yearTrimMatch ? yearTrimMatch[1] : null,
                  trim: yearTrimMatch ? yearTrimMatch[2]?.trim() : null,
                });
              }
            }

            // Also search for "days on market" mentions globally
            const allText = document.body.innerText;
            const globalDays = [...allText.matchAll(/(\d+)\s+days?\s+on\s+(market|lot|cargurus)/gi)];
            
            return { listings, globalDaysMatches: globalDays.map(m => m[0]), totalElements: elements.size };
          });

          console.log(`\n📊 DOM EXTRACTION RESULTS:`);
          console.log(`  Total selector-matched elements: ${results.totalElements}`);
          console.log(`  EV9-specific listings found: ${results.listings.length}`);
          console.log(`  "Days on Market" text instances: ${results.globalDaysMatches.length}`);

          if (results.globalDaysMatches.length > 0) {
            console.log(`  Days on Market matches: ${JSON.stringify(results.globalDaysMatches)}`);
          }

          for (const [idx, listing] of results.listings.entries()) {
            console.log(`\n  [Car #${idx + 1}]`);
            console.log(`    Title: ${listing.title}`);
            console.log(`    Price: ${listing.price || 'N/A'}`);
            console.log(`    Days on Market: ${listing.daysOnMarket !== null ? listing.daysOnMarket + ' days ✅' : 'Not in card'}`);
            console.log(`    Year/Trim: ${listing.year || 'N/A'} ${listing.trim || ''}`);
            console.log(`    Link: ${listing.url}`);
          }

          if (results.listings.length > 0) {
            console.log(`\n✅ SUCCESS: Found ${results.listings.length} Kia EV9 listings on CarGurus`);
            break;
          }
        }
      } catch (err: any) {
        console.log(`  Error on this URL: ${err.message}`);
      }
    }

    // Print captured API data
    if (capturedApiData.length > 0) {
      console.log(`\n📡 CAPTURED API RESPONSES WITH DAYS ON MARKET:`);
      for (const item of capturedApiData) {
        console.log(`  URL: ${item.url}`);
        console.log(`  Data: ${item.data}`);
      }
    }

  } catch (err: any) {
    console.error(`Fatal error: ${err.message}`);
  } finally {
    await page.close().catch(() => {});
  }

  console.log('\n================================================================');
  console.log('DONE');
  console.log('================================================================');
}

scrapeCarGurusNewEv9();
