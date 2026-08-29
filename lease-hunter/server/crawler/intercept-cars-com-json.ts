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

export async function interceptCarsComJson(zip: string = '78665', distance: number = 50) {
  console.log('==================================================');
  console.log('🔍 CARS.COM XHR/JSON INTERCEPTOR');
  console.log('==================================================\n');

  await ensureChromeDebugRunning();

  let browser;
  try {
    browser = await chromium.connectOverCDP(CDP_URL);
  } catch (e) {
    console.error(`[Browser] Failed to connect to CDP: ${e}`);
    return;
  }

  const context = browser.contexts()[0] || await browser.newContext();
  const page = await context.newPage();

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('graphql') || url.includes('api') || url.includes('listings') || url.includes('search')) {
      try {
        const text = await res.text();
        if (text.includes('vin') || text.includes('EV9') || text.includes('price')) {
           console.log(`\n[JSON Intercepted] URL: ${url}`);
           console.log(text.substring(0, 1000));
        }
      } catch (e) {}
    }
  });

  try {
    const targetUrl = `https://www.cars.com/shopping/results/?stock_type=new&makes[]=kia&models[]=kia-ev9&zip=${zip}&maximum_distance=${distance}&years_min[]=2025`;
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 35000 });
    await page.waitForTimeout(10000); // Wait for XHRs
  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await page.close().catch(() => {});
  }
}

interceptCarsComJson();
