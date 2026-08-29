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

async function resolveViaSearchBar() {
  console.log('================================================================');
  console.log('🔍 RESOLVING CARGURUS KIA EV9 ENTITY VIA INTERACTIVE SEARCH BAR');
  console.log('================================================================\n');

  await ensureChromeDebugRunning();

  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0];
  const page = await context.newPage();

  const capturedUrls: string[] = [];

  // Intercept ALL network requests to find the autocomplete/suggestion endpoint
  page.on('request', (req) => {
    const url = req.url();
    if (url.includes('suggest') || url.includes('auto') || url.includes('search') || url.includes('entity') || url.includes('typeahead')) {
      console.log(`[Request Intercepted] ${req.method()} ${url}`);
      capturedUrls.push(url);
    }
  });

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('suggest') || url.includes('auto') || url.includes('typeahead') || url.includes('entity')) {
      try {
        const text = await res.text();
        if (text.length > 10) {
          console.log(`[Response Intercepted] ${url} (${text.length} bytes)`);
          console.log(text.substring(0, 500));
        }
      } catch (e) {}
    }
  });

  try {
    // Step 1: Go to CarGurus homepage
    console.log('[Step 1] Navigating to CarGurus homepage...');
    await page.goto('https://www.cargurus.com/', { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(3000);

    // Step 2: Find and click the search input, then type "Kia EV9"
    console.log('[Step 2] Typing "Kia EV9" into search bar to trigger autocomplete...');
    const searchInput = await page.$('input[placeholder*="Search"], input[name="vendor-search-handler"], input[type="search"]');
    if (searchInput) {
      await searchInput.click();
      await page.waitForTimeout(500);
      await searchInput.type('Kia EV9', { delay: 150 }); // Human-like typing speed
      await page.waitForTimeout(3000); // Wait for autocomplete suggestions

      // Step 3: Look for suggestion dropdown items
      console.log('\n[Step 3] Scanning for autocomplete suggestion elements...');
      const suggestions = await page.evaluate(() => {
        // Look for dropdown/suggestion elements
        const candidates = Array.from(document.querySelectorAll(
          '[role="option"], [role="listbox"] li, .suggestion, .search-suggestion, ' +
          '[class*="suggest"], [class*="autocomplete"], [class*="dropdown"] a, ' +
          '[class*="dropdown"] li, [class*="result"] a, [data-testid*="suggest"]'
        ));
        return candidates.map(el => ({
          text: el.textContent?.trim().substring(0, 80),
          href: (el as HTMLAnchorElement).href || '',
          tag: el.tagName,
          classes: el.className,
        }));
      });

      console.log(`Found ${suggestions.length} suggestion elements:`);
      suggestions.forEach(s => console.log(`  • [${s.tag}] "${s.text}" -> ${s.href} (class: ${s.classes?.substring(0, 50)})`));

      // Step 4: Try clicking the first EV9-related suggestion
      if (suggestions.length > 0) {
        const ev9Suggestion = suggestions.find(s => 
          (s.text || '').toLowerCase().includes('ev9') || (s.href || '').toLowerCase().includes('ev9')
        );
        if (ev9Suggestion && ev9Suggestion.href) {
          console.log(`\n[Step 4] Found EV9 suggestion! Navigating to: ${ev9Suggestion.href}`);
          await page.goto(ev9Suggestion.href, { waitUntil: 'domcontentloaded', timeout: 30000 });
          await page.waitForTimeout(3000);
        }
      }

      // If no dropdown suggestions, try pressing Enter
      if (suggestions.length === 0) {
        console.log('\n[Step 4] No dropdown suggestions found. Pressing Enter to search...');
        await searchInput.press('Enter');
        await page.waitForTimeout(5000);
      }
    } else {
      console.log('Could not find search input. Trying direct URL approach...');
    }

    // Step 5: Capture the final URL and title
    const finalUrl = page.url();
    const finalTitle = await page.title();
    console.log(`\n[Step 5] Final Page:`);
    console.log(`  Title: ${finalTitle}`);
    console.log(`  URL: ${finalUrl}`);

    // Extract entity ID from URL
    const entityMatch = finalUrl.match(/[d-](\d{3,5})/);
    if (entityMatch) {
      console.log(`\n✅ ENTITY ID CANDIDATE: d${entityMatch[1]}`);
    }

    // Step 6: Look for EV9 links on whatever page we landed on
    const ev9Links = await page.evaluate(() => {
      const links = Array.from(document.querySelectorAll('a'));
      return links
        .filter(a => {
          const text = (a.textContent || '').toLowerCase();
          const href = (a.href || '').toLowerCase();
          return text.includes('ev9') || href.includes('ev9');
        })
        .map(a => ({ text: a.textContent?.trim().substring(0, 60), href: a.href }))
        .slice(0, 10);
    });

    console.log(`\n[Step 6] Found ${ev9Links.length} EV9-related links on current page:`);
    ev9Links.forEach(l => console.log(`  • "${l.text}" -> ${l.href}`));

    // Step 7: Check all captured network URLs
    console.log(`\n[Step 7] All captured network URLs with search/suggest/entity/typeahead:`);
    [...new Set(capturedUrls)].forEach(u => console.log(`  • ${u}`));

  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await page.close().catch(() => {});
  }
}

resolveViaSearchBar();
