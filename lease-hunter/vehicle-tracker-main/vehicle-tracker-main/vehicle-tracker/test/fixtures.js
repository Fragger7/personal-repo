// Realistic payloads matching the exact shapes verified live during this build.
const PLAID_VIN = "5YJSA1E60TF999001";

function teslaPage(offset, condition, opts={}){
  const total = condition === "new" ? 0 : 111;
  const results = [];
  if(condition === "used" && !opts.trimFiltered && opts.includePlaid && offset===0){
    results.push({VIN:PLAID_VIN, Year:2026, TrimName:"Plaid", Price:112000, Odometer:3200,
                  City:"Los Angeles", StateProvince:"CA"});
  }
  if(condition === "used" && !opts.trimFiltered){
    for(let i=offset; i<Math.min(offset+24,total); i++){
      results.push({VIN:"5YJSA1E00TF"+String(100000+i), Year: 2021+(i%3), TrimName:"Model S Long Range",
                    Price: 40000+i, Odometer: 30000+i, City:"Austin", StateProvince:"TX"});
    }
  }
  if(condition === "used" && opts.trimFiltered && opts.includePlaid && offset===0){
    results.push({VIN:PLAID_VIN, Year:2026, TrimName:"Plaid", Price:112000, Odometer:3200,
                  City:"Los Angeles", StateProvince:"CA"});
  }
  return JSON.stringify({results, total_matches_found: opts.trimFiltered ? results.length : total});
}

function carmaxHtml(includePlaid){
  let body = '"totalCount":'+(includePlaid?21:20)+',';
  for(let i=0;i<20;i++){
    body += `"stockNumber":${7000000+i},"vin":"5YJSA1E11MF${String(200000+i)}","year":${2021+(i%4)},"trim":"Plaid","mileage":${20000+i},"basePrice":${60000+i},`;
    body += `"stockNumber":${7000000+i},"vin":"5YJSA1E11MF${String(200000+i)}","year":${2021+(i%4)},"trim":"Plaid","mileage":${20000+i},"basePrice":${60000+i},`; // dup: CarMax embeds twice
  }
  if(includePlaid){
    body += `"stockNumber":7099999,"vin":"5YJSA1E22NF300001","year":2026,"trim":"Plaid","mileage":1500,"basePrice":118000,`;
    body += `"stockNumber":7099999,"vin":"5YJSA1E22NF300001","year":2026,"trim":"Plaid","mileage":1500,"basePrice":118000,`;
  }
  return "<html><body>"+body+"</body></html>";
}

function carvanaHtml(includePlaid, pending){
  // Carvana embeds ESCAPED json
  let body = '\\"totalMatchedPages\\":1,\\"pageSize\\":24,';
  for(let i=0;i<11;i++){
    body += `\\"stockNumber\\":${2000000+i},\\"vehicleId\\":${4000000+i},\\"vin\\":\\"5YJSA1E33PF${String(400000+i)}\\",\\"year\\":${2021+(i%3)},\\"trim\\":\\"Plaid\\",\\"mileage\\":${15000+i},\\"total\\":${70000+i},\\"isPurchasePending\\":false,\\"vdpSlug\\":\\"tesla-model-s\\",`;
  }
  if(includePlaid){
    body += `\\"stockNumber\\":2099999,\\"vehicleId\\":4616568,\\"vin\\":\\"5YJSA1E44NF500001\\",\\"year\\":2026,\\"trim\\":\\"Plaid\\",\\"mileage\\":5374,\\"total\\":124990,\\"isPurchasePending\\":${pending?"true":"false"},\\"vdpSlug\\":\\"2026-tesla-model-s-plaid\\",`;
  }
  return "<html><body>"+body+"</body></html>";
}

function carvanaDetail(available, holdMinutes){
  if(holdMinutes){
    const exp=new Date(Date.now()+holdMinutes*60000).toISOString();
    return `<html><body>\\"isAvailableForPurchase\\":false,\\"shouldShowTimer\\":true,\\"isLockedByThisUser\\":false,\\"expires\\":\\"$D${exp}\\"</body></html>`;
  }
  return `<html><body>\\"isAvailableForPurchase\\":${available?"true":"false"},\\"shouldShowTimer\\":false,\\"saleStatus\\":\\"Available\\"</body></html>`;
}
module.exports={teslaPage,carmaxHtml,carvanaHtml,carvanaDetail,PLAID_VIN};
