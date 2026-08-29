import { chromium } from 'playwright';

const CDP_URL = 'http://127.0.0.1:9222';

async function inspectCarsComDom() {
  console.log('--- INSPECTING CARS.COM EV9 SEARCH DOM STRUCTURE ---');

  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0];
  const page = await context.newPage();

  try {
    const targetUrl = 'https://www.cars.com/shopping/results/?stock_type=new&makes[]=kia&models[]=kia-ev9&zip=78665&maximum_distance=50';
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 35000 });
    await page.waitForTimeout(6000);

    const data = await page.evaluate(() => {
      // Find all anchors linking to vehicle detail pages
      const links = Array.from(document.querySelectorAll('a[href*="/vehicledetail/"]')) as HTMLAnchorElement[];
      const titles = Array.from(document.querySelectorAll('h2, .title, .vehicle-card__title')).map(el => el.textContent?.trim());
      
      const cards = links.map(a => {
        const parent = a.closest('.vehicle-card, div[class*="vehicle"], div[class*="card"]') || a;
        return {
          text: parent.textContent?.replace(/\s+/g, ' ').trim().substring(0, 120),
          url: a.href,
        };
      });

      return { linkCount: links.length, titles: titles.slice(0, 10), cards: cards.slice(0, 5) };
    });

    console.log(`\n🎉 Found ${data.linkCount} vehicle detail links on Cars.com!`);
    console.log('Sample Titles:', data.titles);
    console.log('Sample Cards:');
    data.cards.forEach((c, idx) => console.log(`Card #${idx + 1}: ${c.text} -> ${c.url}`));

  } catch (err: any) {
    console.error(err.message);
  } finally {
    await page.close().catch(() => {});
  }
}

inspectCarsComDom();
