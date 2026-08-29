import { sendTelegramAlert } from './telegram.js';

async function testLiveAlert() {
  console.log('Sending live Telegram test alert card...');
  const success = await sendTelegramAlert({
    vin: 'KNDET3B37R6019281',
    year: 2024,
    make: 'Kia',
    model: 'EV9',
    trim: 'GT-Line AWD',
    msrp: 75900,
    listingPrice: 68200,
    daysOnLot: 187,
    dealerName: 'Round Rock Kia',
    dealerZip: '78665',
    color: 'Ocean Blue',
    listingUrl: 'https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?zip=78665&distance=50#shoppingList/search?search=KNDET3B37R6019281',
  });

  if (success) {
    console.log('🎉 TEST ALERT SENT SUCCESSFULLY TO TELEGRAM!');
  } else {
    console.log('❌ Failed to send alert.');
  }
}

testLiveAlert();
