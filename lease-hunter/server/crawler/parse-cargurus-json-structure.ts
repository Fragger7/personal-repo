import { chromium } from 'playwright-extra';
import stealthPlugin from 'puppeteer-extra-plugin-stealth';
import fs from 'fs';

chromium.use(stealthPlugin());

async function parseCarGurusJsonStructure() {
  console.log('--- PARSING CARGURUS REAL 571KB JSON PAYLOAD STRUCTURE ---');

  const executablePath = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

  const browser = await chromium.launch({
    headless: false,
    executablePath: fs.existsSync(executablePath) ? executablePath : undefined,
    args: ['--disable-blink-features=AutomationControlled'],
  });

  const page = await browser.newPage();
  let capturedJson: any = null;

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory') || url.includes('uic-filter-data')) {
      try {
        const text = await res.text();
        if (text.length > 50000) {
          console.log(`🎉 CAPTURED 500KB+ JSON PAYLOAD (${text.length} bytes) from: ${url.substring(0, 100)}`);
          try {
            capturedJson = JSON.parse(text);
          } catch (e) {
            // Regex save snippet
            fs.writeFileSync('cargurus-payload-sample.json', text.substring(0, 20000));
            console.log('Saved raw JSON snippet to cargurus-payload-sample.json');
          }
        }
      } catch (e) {}
    }
  });

  try {
    const targetUrl = 'https://www.cargurus.com/Cars/l-Used-Kia-d50#zip=78665&distance=50';
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(6000);
  } catch (e: any) {
    console.error(`Error: ${e.message}`);
  } finally {
    await browser.close();
  }

  if (capturedJson) {
    console.log('\n--- TOP-LEVEL KEYS IN CARGURUS JSON ---');
    console.log(Object.keys(capturedJson));
  }
}

parseCarGurusJsonStructure();
