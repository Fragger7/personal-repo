import { chromium } from 'playwright';

async function testCleanUrl() {
  console.log('Testing clean CarGurus VIN search URL...');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  const testVin = 'KNDET3B37R6019281';
  const cleanUrl = `https://www.cargurus.com/Cars/s?q=${testVin}`;

  console.log(`Navigating to: ${cleanUrl}`);
  try {
    await page.goto(cleanUrl, { waitUntil: 'domcontentloaded', timeout: 15000 });
    const title = await page.title();
    console.log(`Page Title: "${title}"`);
    
    const content = await page.content();
    if (content.includes('Captcha') || content.includes('Attention Required')) {
      console.log('❌ BOT BLOCK DETECTED!');
    } else {
      console.log('✅ CLEAN LOAD! No bot block detected.');
    }
  } catch (e: any) {
    console.error(`Error: ${e.message}`);
  } finally {
    await browser.close();
  }
}

testCleanUrl();
