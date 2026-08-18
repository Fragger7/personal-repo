import { chromium } from 'playwright';

async function testCarGurusUiSearch() {
  console.log('Connecting to running Chrome on 127.0.0.1:9222...');
  const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  const context = browser.contexts()[0];
  const page = context.pages()[0] || await context.newPage();

  console.log('Navigating to CarGurus homepage...');
  await page.goto('https://www.cargurus.com', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);

  // Monitor network responses
  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('search') || url.includes('inventory') || url.includes('listings')) {
      try {
        const text = await res.text();
        if (text.includes('5XY') || text.includes('EV9') || text.includes('daysOnMarket')) {
          console.log(`\n🎉 [XHR INTERCEPTED STREAM]: ${url.substring(0, 100)} (${text.length} bytes)`);
        }
      } catch {}
    }
  });

  try {
    // Try to find the search widget elements
    console.log('Interacting with search widget...');
    
    // Look for New tab if present
    const newTab = await page.$('text="New Cars"') || await page.$('text="New"') || await page.$('button:has-text("New")');
    if (newTab) {
      await newTab.click();
      console.log('Clicked "New Cars" tab.');
      await page.waitForTimeout(1000);
    }

    // Try navigating directly to the new search form
    const directSearchUrl = 'https://www.cargurus.com/Cars/new/searchresults.action?zip=78665&distance=50';
    console.log(`Navigating to: ${directSearchUrl}`);
    await page.goto(directSearchUrl, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(4000);
    console.log(`Destination Title: "${await page.title()}"`);
    console.log(`Destination URL: "${page.url()}"`);

    // Extract all make/model links or filters on page
    const filterOptions = await page.evaluate(() => {
      const links = Array.from(document.querySelectorAll('a, button, select option'));
      return links
        .map(el => el.textContent?.trim() || '')
        .filter(t => t.toLowerCase().includes('kia') || t.toLowerCase().includes('ev9'));
    });

    console.log('Found Kia/EV9 matches on page:', filterOptions);

  } catch (err: any) {
    console.error('Error:', err.message);
  }
}

testCarGurusUiSearch();
