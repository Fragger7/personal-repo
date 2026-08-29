import dotenv from 'dotenv';
dotenv.config();

export interface AlertVehicle {
  vin: string;
  year: number;
  make: string;
  model: string;
  trim: string;
  msrp: number;
  listingPrice: number;
  daysOnLot: number;
  dealerName: string;
  dealerZip?: string;
  listingUrl: string;
  color?: string;
}

export async function sendTelegramAlert(vehicle: AlertVehicle): Promise<boolean> {
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;

  if (!token || !chatId) {
    console.log(`[Telegram Alert Skipped] TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not configured in .env for VIN: ${vehicle.vin}`);
    return false;
  }

  const savings = vehicle.msrp > vehicle.listingPrice ? vehicle.msrp - vehicle.listingPrice : 0;
  const discountPercent = vehicle.msrp > 0 ? ((savings / vehicle.msrp) * 100).toFixed(1) : '0';

  const message = `
🚨 <b>HIGH-VALUE EV9 DEAL DETECTED!</b>

🚘 <b>${vehicle.year} ${vehicle.make} ${vehicle.model} ${vehicle.trim}</b>
⏳ <b>Days on Lot:</b> ${vehicle.daysOnLot} Days (Aged Inventory Target!)
💰 <b>MSRP:</b> $${vehicle.msrp.toLocaleString()}
🏷️ <b>Listed Price:</b> $${vehicle.listingPrice.toLocaleString()} (${discountPercent}% off MSRP)
🏢 <b>Dealer:</b> ${vehicle.dealerName} (${vehicle.dealerZip || 'Local Area'})
🎨 <b>Color:</b> ${vehicle.color || 'N/A'}
🆔 <code>${vehicle.vin}</code>

🔗 <a href="${vehicle.listingUrl}">View Exact Vehicle Listing (${vehicle.dealerName})</a>
🔍 <a href="https://www.google.com/search?q=${vehicle.vin}">Search VIN ${vehicle.vin} on Web</a>
`;

  try {
    const response = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: chatId,
        text: message,
        parse_mode: 'HTML',
        disable_web_page_preview: false,
      }),
    });

    const data = await response.json();
    if (data.ok) {
      console.log(`[Telegram Alert Sent Successfully] Alert delivered for VIN: ${vehicle.vin}`);
      return true;
    } else {
      console.error('[Telegram API Error]:', data.description);
      return false;
    }
  } catch (err: any) {
    console.error('[Telegram Alert Failed]:', err.message);
    return false;
  }
}

export interface BaselineAlertData {
  make: string;
  model: string;
  trim: string;
  year: string | number;
  zipCode: string;
  termMonths?: number;
  inquiryText: string;
  edmundsUrl: string;
  sourceNotes?: string;
}

export async function sendBaselineVerificationAlert(data: BaselineAlertData): Promise<boolean> {
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;

  if (!token || !chatId) {
    console.log(`[Telegram Alert Skipped] TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not configured for Baseline Alert.`);
    return false;
  }

  const message = `
⚠️ <b>ACTION REQUIRED: LEASE BASELINE VERIFICATION</b>

The Lease Hunter engine is evaluating <b>${data.year} ${data.make} ${data.model} (${data.trim})</b> in ZIP <b>${data.zipCode}</b>. Current captive lender (KFA) buy-rate MF and RV need live verification from Edmunds moderators or Leasehackr.

📝 <b>Copy & Paste Forum Inquiry:</b>
<code>${data.inquiryText}</code>

🔗 <b>Post directly in the Edmunds Thread:</b>
<a href="${data.edmundsUrl}">Open 2026 Kia EV9 Edmunds Forum Discussion</a>

<i>Once moderators respond, the scraper will ingest the verified rate on the next cycle.</i>
`;

  try {
    const response = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: chatId,
        text: message,
        parse_mode: 'HTML',
        disable_web_page_preview: false,
      }),
    });

    const resData = await response.json();
    if (resData.ok) {
      console.log(`[Telegram Alert Sent] Baseline verification prompt delivered for ${data.trim} (${data.zipCode})`);
      return true;
    } else {
      console.error('[Telegram API Error]:', resData.description);
      return false;
    }
  } catch (err: any) {
    console.error('[Telegram Alert Failed]:', err.message);
    return false;
  }
}

