import { chromium } from 'playwright';

async function screenshotCarGurus() {
  console.log('Taking screenshot of CarGurus Kia EV9 search...');
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 800 }
  });
  const page = await context.newPage();

  try {
    await page.goto('https://www.cargurus.com/Cars/l-Used-Kia-EV9-d3356', { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(4000);

    const title = await page.title();
    console.log(`Page Title: ${title}`);

    await page.screenshot({ path: 'cargurus-debug.png', fullPage: false });
    console.log('Screenshot saved as cargurus-debug.png!');
  } catch (e: any) {
    console.error(`Screenshot error: ${e.message}`);
  } finally {
    await browser.close();
  }
}

screenshotCarGurus();
