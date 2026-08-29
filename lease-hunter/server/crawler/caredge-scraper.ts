import fs from 'fs';
import path from 'path';

export async function scrapeCarEdgeEv9(options: { zip?: string, radius?: number, make?: string, model?: string, trim?: string } = {}) {
  const { zip = '78665', radius = 50, make = 'Kia', model = 'EV9', trim = '' } = options;
  console.log('==================================================');
  console.log(`🚗 CAREDGE LIVE API: ${make.toUpperCase()} ${model.toUpperCase()} DAYS ON MARKET EXTRACTOR`);
  console.log(`Target: BRAND NEW ${make} ${model} within ${radius}mi of ZIP ${zip}`);
  console.log('==================================================\n');

  try {
    const url = `https://cs2.caredge.com/api/search?condition=new&make=${make}&model=${model}&page=1&radius=${radius}&zip=${zip}&clean_title=false&one_owner=false&include_in_transit=true&partner_only=false&per_page=50`;

    
    console.log(`Fetching from CarEdge API: ${url}`);
    
    const response = await fetch(url, {
      headers: {
        'Accept': 'application/json, text/plain, */*',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Origin': 'https://my.caredge.com',
        'Referer': 'https://my.caredge.com/',
      }
    });

    if (!response.ok) {
      throw new Error(`CarEdge API responded with status: ${response.status}`);
    }

    const data = await response.json();
    
    if (!data.hits || data.hits.length === 0) {
      console.log('No EV9s found in this radius on CarEdge.');
      return [];
    }

    console.log(`\n[API Scan] Found ${data.hits.length} EV9s on CarEdge!`);
    
    const parsedCars = data.hits.map((car: any) => ({
      source: 'CarEdge',
      title: `${car.year} ${car.make} ${car.model} ${car.trim}`,
      year: car.year,
      trim: car.trim,
      vin: car.vin,
      daysOnLot: car.dos_active, // Exact Days on Market!
      listingPrice: car.seller_price || car.price,
      msrp: car.price,
      dealerName: car.dealer_name,
      dealerZip: car.zip || zip,
      listingUrl: car.dealer_vdp_url || `https://my.caredge.com/buy?radius=${radius}&zip=${zip}&make=Kia&model=EV9`,
      inTransit: car.in_transit
    }));

    // Output top 3 for verification
    parsedCars.slice(0, 3).forEach((c: any, i: number) => {
      console.log(`\n[Car #${i + 1}] ${c.title}`);
      console.log(`   VIN: ${c.vin}`);
      console.log(`   Price: $${c.listingPrice}`);
      console.log(`   Days on Market: ${c.daysOnLot} days`);
      console.log(`   Dealer: ${c.dealerName}`);
    });

    // Save to inventory
    const inventoryPath = path.join(process.cwd(), 'data', 'inventory.json');
    if (!fs.existsSync(path.dirname(inventoryPath))) {
      fs.mkdirSync(path.dirname(inventoryPath), { recursive: true });
    }
    fs.writeFileSync(inventoryPath, JSON.stringify(parsedCars, null, 2));
    console.log(`\n✅ Saved ${parsedCars.length} LIVE listings to data/inventory.json!`);

    return parsedCars;

  } catch (err: any) {
    console.error(`Error scraping CarEdge: ${err.message}`);
    return [];
  }
}

scrapeCarEdgeEv9();
