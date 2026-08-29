import fs from 'fs';

const rawText = fs.readFileSync('scratch/cargurus-live-payload.json', 'utf8');

console.log('Payload length:', rawText.length);

// Find where "daysOnMarket" or "daysOnLot" appears
const idx = rawText.indexOf('daysOnMarket');
if (idx !== -1) {
  console.log('\n--- SNIPPET AROUND daysOnMarket ---');
  console.log(rawText.substring(Math.max(0, idx - 200), Math.min(rawText.length, idx + 400)));
} else {
  console.log('daysOnMarket not found, looking for daysOnLot...');
  const idxLot = rawText.indexOf('daysOnLot');
  if (idxLot !== -1) {
    console.log('\n--- SNIPPET AROUND daysOnLot ---');
    console.log(rawText.substring(Math.max(0, idxLot - 200), Math.min(rawText.length, idxLot + 400)));
  }
}
