import { chromium } from 'playwright';

async function inspectCarEdgeScripts() {
  console.log('Inspecting CarEdge DOM script tags & state variables...');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  page.on('response', async (res) => {
    const url = res.url();
    if (url.includes('api') || url.includes('graphql') || url.includes('search') || url.includes('cars')) {
      try {
        const text = await res.text();
        if (text.includes('daysOnLot') || text.includes('5XY') || text.includes('EV9')) {
          console.log(`🎉 CAREDGE XHR API INTERCEPTED (${text.length} bytes): ${url.substring(0, 110)}`);
        }
      } catch (e) {}
    }
  });

  try {
    await page.goto('https://caredge.com/cars?make=Kia&model=EV9&zip=78665&radius=50', { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(4000);

    const scriptsInfo = await page.evaluate(() => {
      const scripts = Array.from(document.querySelectorAll('script'));
      return scripts
        .map(s => ({ id: s.id, src: s.src, snippet: s.innerText.substring(0, 100) }))
        .filter(s => s.snippet.length > 0 || s.src.length > 0);
    });

    console.log('\n--- DOM SCRIPT TAGS FOUND ---');
    console.log(scriptsInfo.slice(0, 10));
  } catch (e: any) {
    console.error(e.message);
  } finally {
    await browser.close();
  }
}

inspectCarEdgeScripts();
