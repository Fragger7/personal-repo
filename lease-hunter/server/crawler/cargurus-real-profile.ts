import { chromium } from 'playwright';
import path from 'path';
import os from 'os';

export async function testRealChromeProfile() {
  console.log('--- LAUNCHING PLAYWRIGHT WITH REAL CHROME PROFILE CONTEXT ---');

  // Use local AppData temp user profile dir to preserve real cookies
  const userDataDir = path.join(os.homedir(), 'AppData', 'Local', 'Temp', 'lease_hunter_chrome_profile');

  const context = await chromium.launchPersistentContext(userDataDir, {
    headless: false, // Visible browser window ensures Cloudflare sees real user interaction
    viewport: { width: 1280, height: 800 },
    args: [
      '--disable-blink-features=AutomationControlled',
      '--no-sandbox',
    ],
  });

  const page = context.pages()[0] || await context.newPage();
  const capturedVehicles: any[] = [];

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory') || url.includes('uic-filter-data')) {
      try {
        const text = await res.text();
        if (text.includes('EV9') || text.includes('KND') || text.includes('listings')) {
          console.log(`🎉 CAPTURED REAL BACKEND JSON FROM: ${url.substring(0, 110)}`);
          // Extract real VINs
          const vins = text.match(/KND[A-HJ-NPR-Z0-9]{14}/g) || [];
          vins.forEach(vin => {
            if (!capturedVehicles.some(v => v.vin === vin)) {
              console.log(`🔥 REAL LIVE VIN CAPTURED: ${vin}`);
              capturedVehicles.push({ vin, source: 'Real Profile Interceptor' });
            }
          });
        }
      } catch (e) {}
    }
  });

  try {
    console.log('Navigating to CarGurus Kia EV9 Search Page...');
    await page.goto('https://www.cargurus.com/Cars/l-Used-Kia-d50', { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(6000);

    const title = await page.title();
    console.log(`Current Page Title: "${title}"`);
  } catch (e: any) {
    console.error(`Error: ${e.message}`);
  } finally {
    await context.close();
  }

  console.log(`\n=== REAL LIVE VEHICLES CAPTURED: ${capturedVehicles.length} ===`);
  return capturedVehicles;
}

if (process.argv[1] && process.argv[1].includes('cargurus-real-profile')) {
  testRealChromeProfile();
}
