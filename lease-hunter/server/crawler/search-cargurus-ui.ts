import { chromium } from 'playwright';
import { spawn } from 'child_process';
import path from 'path';
import fs from 'fs';

const CDP_URL = 'http://127.0.0.1:9222';

async function ensureChromeDebugRunning(): Promise<boolean> {
  try {
    const res = await fetch(`${CDP_URL}/json/version`, { signal: AbortSignal.timeout(2000) });
    if (res.ok) return true;
  } catch (e) {}

  console.log('[CDP Setup] Chrome debug port 9222 is not active. Launching genuine Chrome instance...');
  const chromePaths = [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  ];
  const chromeBin = chromePaths.find(p => fs.existsSync(p));
  if (!chromeBin) return false;

  const profileDir = path.join(process.cwd(), 'chrome-debug-profile');
  if (!fs.existsSync(profileDir)) fs.mkdirSync(profileDir, { recursive: true });
  
  const lockFile = path.join(profileDir, 'Default', 'LOCK');
  if (fs.existsSync(lockFile)) {
    try { fs.unlinkSync(lockFile); } catch (e) {}
  }
  const lockFile2 = path.join(profileDir, 'LOCK');
  if (fs.existsSync(lockFile2)) {
    try { fs.unlinkSync(lockFile2); } catch (e) {}
  }

  spawn(chromeBin, [
    '--remote-debugging-port=9222',
    '--remote-allow-origins=*',
    `--user-data-dir=${profileDir}`,
    '--no-first-run',
    '--no-default-browser-check',
    'https://www.cargurus.com',
  ], { detached: true, stdio: 'ignore' }).unref();

  for (let i = 0; i < 10; i++) {
    await new Promise(r => setTimeout(r, 1000));
    try {
      const res = await fetch(`${CDP_URL}/json/version`, { signal: AbortSignal.timeout(2000) });
      if (res.ok) return true;
    } catch (e) {}
  }
  return false;
}

async function searchCargurusUI() {
  console.log('--- UI DRIVEN CARGURUS SEARCH ---');
  await ensureChromeDebugRunning();
  let browser;
  try {
    browser = await chromium.connectOverCDP(CDP_URL);
  } catch (err: any) {
    console.error(`CDP Connection failed: ${err.message}`);
    return;
  }

  const context = browser.contexts()[0] || await browser.newContext();
  const page = await context.newPage();

  try {
    console.log('Navigating to CarGurus New Cars...');
    await page.goto('https://www.cargurus.com/Cars/new', { waitUntil: 'domcontentloaded', timeout: 40000 });
    
    console.log('Waiting for Make dropdown...');
    // The dropdown might be a select element or a custom div dropdown
    // Let's try to find the combobox or select for make
    await page.waitForTimeout(3000);
    
    // Instead of doing UI clicks which can be flaky, let's use the search bar or typeahead
    // CarGurus often has a hybrid search input
    const searchInput = await page.$('input[placeholder*="Search make"]');
    if (searchInput) {
        console.log('Found search input, typing Kia EV9...');
        await searchInput.fill('Kia EV9');
        await page.waitForTimeout(2000);
        await page.keyboard.press('Enter');
    } else {
        console.log('No search input, trying select dropdowns...');
        const makeSelect = await page.$('select[name="make"]');
        if (makeSelect) {
            await makeSelect.selectOption({ label: 'Kia' });
            await page.waitForTimeout(2000);
            const modelSelect = await page.$('select[name="model"]');
            if (modelSelect) {
                await modelSelect.selectOption({ label: 'EV9' });
            }
            await page.waitForTimeout(1000);
            const zipInput = await page.$('input[name="zip"]');
            if (zipInput) await zipInput.fill('78665');
            const submitBtn = await page.$('button[type="submit"], input[type="submit"]');
            if (submitBtn) await submitBtn.click();
        } else {
            console.log('Could not find standard inputs. Dumping HTML to see structure...');
            const html = await page.content();
            console.log(html.substring(0, 1000));
        }
    }

    console.log('Waiting for search results...');
    await page.waitForTimeout(10000);
    
    const finalUrl = page.url();
    console.log(`\n[SUCCESS] Final URL: ${finalUrl}`);
    
    const title = await page.title();
    console.log(`[SUCCESS] Page Title: ${title}`);

    // Try to extract cards
    const vehicleCards = await page.evaluate(() => {
      const cards = Array.from(document.querySelectorAll('a[href*="/Cars/detail/"], a[href*="/Cars/link/"], [data-cg-ft="srp-listing-blade"]'));
      return cards.map(c => {
        const text = c.textContent || '';
        const href = (c as HTMLAnchorElement).href || (c.querySelector('a') as HTMLAnchorElement)?.href || '';
        const vinMatch = text.match(/([5K][A-HJ-NPR-Z0-9]{16})/);
        const daysMatch = text.match(/(\d+)\s+days?\s+on\s+(market|lot|cargurus)/i);
        const priceMatch = text.match(/\$([0-9]{2,3},[0-9]{3})/);
        return {
          title: text.replace(/\s+/g, ' ').trim().substring(0, 80),
          vin: vinMatch ? vinMatch[1] : null,
          daysOnMarket: daysMatch ? Number(daysMatch[1]) : null,
          price: priceMatch ? priceMatch[0] : null,
          url: href,
        };
      }).filter(i => i.url.length > 0 && i.title.length > 5);
    });

    console.log(`Found ${vehicleCards.length} potential listings on UI`);
    vehicleCards.slice(0,3).forEach(c => console.log(c));

  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await page.close().catch(() => {});
  }
}

searchCargurusUI();
