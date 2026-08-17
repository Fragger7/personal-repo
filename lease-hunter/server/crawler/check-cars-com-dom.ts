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

export async function checkCarsComDOM(zip: string = '78665', distance: number = 50) {
  await ensureChromeDebugRunning();
  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0] || await browser.newContext();
  const page = await context.newPage();

  try {
    const targetUrl = `https://www.cars.com/shopping/results/?stock_type=new&makes[]=kia&models[]=kia-ev9&zip=${zip}&maximum_distance=${distance}&years_min[]=2025`;
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 35000 });
    await page.waitForTimeout(10000); // Wait for React to mount

    const tags = await page.evaluate(() => {
      const allTags = Array.from(document.querySelectorAll('*')).map(x => x.tagName.toLowerCase());
      const uniqueTags = [...new Set(allTags)];
      return uniqueTags.filter(t => t.includes('car') || t.includes('vehicle') || t.includes('list') || t.includes('item'));
    });

    console.log('\n[DOM Tags found containing car/vehicle/list/item]:');
    console.log(tags);

    // Also try to find PRELOADED_STATE
    const state = await page.evaluate(() => {
        const script = Array.from(document.querySelectorAll('script')).find(s => s.textContent?.includes('PRELOADED_STATE') || s.textContent?.includes('window.__'));
        return script ? script.textContent?.substring(0, 500) : 'No preloaded state script found';
    });
    console.log('\n[Preloaded State]:\n' + state);

  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await page.close().catch(() => {});
  }
}
checkCarsComDOM();
