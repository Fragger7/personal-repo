import { chromium } from 'playwright';

async function dumpCarGurusPageContent() {
  console.log('Attaching to Chrome on 127.0.0.1:9222...');
  const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  const context = browser.contexts()[0];
  const page = context.pages().find(p => p.url().includes('cargurus.com')) || context.pages()[0];

  console.log(`Current URL: ${page.url()}`);
  console.log(`Page Title: "${await page.title()}"`);

  await page.waitForTimeout(4000);
  await page.evaluate(() => window.scrollBy(0, 1200));
  await page.waitForTimeout(3000);

  const data = await page.evaluate(() => {
    // Collect all links and cards
    const links = Array.from(document.querySelectorAll('a')).map(a => ({
      text: a.innerText.trim().replace(/\s+/g, ' '),
      href: a.href
    })).filter(l => l.href.includes('detail') || l.href.includes('link') || l.href.includes('inventory') || l.text.includes('EV9') || l.text.includes('Kia'));

    // Text snippet
    const bodyText = (document.body.innerText || '').substring(0, 1500).replace(/\s+/g, ' ');

    return { totalLinksFound: links.length, sampleLinks: links.slice(0, 15), bodyPreview: bodyText };
  });

  console.log('\n--- BODY PREVIEW ---');
  console.log(data.bodyPreview);

  console.log('\n--- LISTING LINKS FOUND: ' + data.totalLinksFound + ' ---');
  data.sampleLinks.forEach((l, i) => {
    console.log(`[#${i + 1}] ${l.text} -> ${l.href}`);
  });

  process.exit(0);
}

dumpCarGurusPageContent();
