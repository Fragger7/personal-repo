import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

async function extractCarGurusState() {
  console.log('Attaching to Chrome on 127.0.0.1:9222...');
  const browser = await chromium.connectOverCDP('http://127.0.0.1:9222');
  const context = browser.contexts()[0];
  const page = context.pages().find(p => p.url().includes('cargurus.com')) || await context.newPage();

  console.log(`Page URL: ${page.url()}`);

  // Extract all script JSON contents or global objects from the DOM
  const stateData = await page.evaluate(() => {
    const scripts = Array.from(document.querySelectorAll('script'));
    let nextData: any = null;
    let otherJson: any[] = [];

    // Check window variables
    const win = window as any;
    const globalVars = {
      __NEXT_DATA__: win.__NEXT_DATA__ || null,
      __INITIAL_STATE__: win.__INITIAL_STATE__ || null,
      carGurusData: win.carGurusData || null,
      cgData: win.cg || null,
    };

    // Inspect script tags
    scripts.forEach((s) => {
      const id = s.id || '';
      const text = s.textContent || '';
      if (id === '__NEXT_DATA__' || text.includes('listings') || text.includes('daysOnMarket')) {
        if (text.startsWith('{') || text.includes('"props"')) {
          try {
            const parsed = JSON.parse(text);
            otherJson.push({ id, parsed });
          } catch {}
        }
      }
    });

    return { globalVars, otherJsonCount: otherJson.length, sampleJson: otherJson[0] || null };
  });

  console.log('Global Variables Found:', Object.keys(stateData.globalVars).filter(k => (stateData.globalVars as any)[k]));
  console.log('Script JSON blocks extracted:', stateData.otherJsonCount);

  if (stateData.sampleJson) {
    const dumpPath = path.join(process.cwd(), 'data', 'cargurus-dehydrated-state.json');
    fs.writeFileSync(dumpPath, JSON.stringify(stateData.sampleJson, null, 2));
    console.log(`Saved dehydrated state to ${dumpPath}`);
  }

  process.exit(0);
}

extractCarGurusState();
