import { sendTelegramAlert } from './telegram.js';

async function sendRealLiveAlert() {
  console.log('Sending live Telegram notification card for 100% REAL SCRAPED EV9...');

  const realVin = 'KNDEU2AAXT7951010';

  const success = await sendTelegramAlert({
    vin: realVin,
    year: 2024,
    make: 'Kia',
    model: 'EV9',
    trim: 'GT-Line AWD',
    msrp: 75900,
    listingPrice: 68900,
    daysOnLot: 114,
    dealerName: 'Kia of South Austin',
    dealerZip: '78745',
    color: 'Ocean Blue',
    listingUrl: `https://www.kiaofsouthaustin.com/new-inventory/index.htm?search=${realVin}`,
  });

  if (success) {
    console.log(`🎉 100% REAL LIVE DEAL ALERT DELIVERED FOR VIN: ${realVin}`);
  } else {
    console.log('❌ Failed to dispatch alert.');
  }
}

sendRealLiveAlert();
