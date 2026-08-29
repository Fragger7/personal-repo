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

async function queryCargurusApi() {
  console.log('--- QUERYING CARGURUS API VIA PAGE CONTEXT ---');
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
    console.log('Navigating to CarGurus homepage to establish session...');
    await page.goto('https://www.cargurus.com', { waitUntil: 'domcontentloaded', timeout: 30000 });
    
    console.log('Executing fetch to suggestions API...');
    const data = await page.evaluate(async () => {
        try {
            const res = await fetch('https://www.cargurus.com/Cars/api/search/suggestions?query=Kia+EV9');
            return await res.json();
        } catch(e: any) {
            return { error: e.toString() };
        }
    });

    console.log('API Response:', JSON.stringify(data, null, 2));

  } catch (err: any) {
    console.error(`Error: ${err.message}`);
  } finally {
    await page.close().catch(() => {});
  }
}

queryCargurusApi();
