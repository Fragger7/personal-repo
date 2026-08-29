import { chromium } from 'playwright';

async function inspectAttachedTabs() {
  console.log('Connecting to attached Chrome on 127.0.0.1:9222...');
  const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  const contexts = browser.contexts();
  const pages = contexts[0].pages();

  console.log(`Found ${pages.length} active pages in attached Chrome.`);
  
  for (const page of pages) {
    const title = await page.title();
    const url = page.url();
    if (url.includes('cargurus.com') || url.includes('cars.com')) {
      console.log(`\n==================================================`);
      console.log(`📄 INSPECTING TAB: "${title}"`);
      console.log(`🔗 URL: ${url}`);
      console.log(`==================================================`);

      // Extract cards from DOM
      const domDetails = await page.evaluate(() => {
        const text = document.body.innerText || '';
        const priceMatches = text.match(/\$([0-9]{2,3},[0-9]{3})/g) || [];
        const vinMatches = text.match(/([5K][A-HJ-NPR-Z0-9]{16})/g) || [];
        const daysMatches = text.match(/(\d+)\s+days?\s+(on\s+market|on\s+lot|ago)/gi) || [];

        // Check for specific listing cards
        const links = Array.from(document.querySelectorAll('a[href*="/vehicledetail/"], a[href*="/Cars/detail/"], a[href*="/Cars/link/"]')).map(a => (a as HTMLAnchorElement).href);

        return {
          bodySnippet: text.substring(0, 300).replace(/\s+/g, ' '),
          prices: priceMatches.slice(0, 10),
          vins: Array.from(new Set(vinMatches)).slice(0, 10),
          days: daysMatches.slice(0, 10),
          directLinks: links.slice(0, 5)
        };
      });

      console.log('Body Preview:', domDetails.bodySnippet);
      console.log('Prices Extracted:', domDetails.prices);
      console.log('VINs Extracted:', domDetails.vins);
      console.log('Days on Market:', domDetails.days);
      console.log('Direct Listing Links:', domDetails.directLinks);
    }
  }
}

inspectAttachedTabs();
