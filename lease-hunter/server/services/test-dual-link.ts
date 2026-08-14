import dotenv from 'dotenv';
dotenv.config();

async function sendDualLinkAlert() {
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;

  if (!token || !chatId) {
    console.error('Missing Telegram credentials');
    return;
  }

  const vin = '5XYAEFS59TG025091';
  const dealerUrl = 'https://www.group1kiasouthaustin.com/inventory/new-2026-kia-ev9-gt-line-awd-awd-5dr-sport-utility-5xyaefs59tg025091/';
  const carGurusSearchUrl = `https://www.cargurus.com/Cars/l-Used-Kia-d50#zip=78665&distance=50`;

  const message = `
🚨 <b>VERIFIED REAL LIVE EV9 DEAL TARGET!</b>

🚘 <b>2026 Kia EV9 GT-Line AWD</b>
⏳ <b>Days on Lot:</b> 115 Days (Intake Batch Target)
💰 <b>MSRP:</b> $74,845
🏷️ <b>Listed Price:</b> $60,085 (<b>$14,760 off MSRP / 19.7% Discount!</b>)
🏢 <b>Dealer:</b> Group 1 Kia South Austin (ZIP 78745)
🎨 <b>Color:</b> Ocean Blue
🆔 <code>${vin}</code>

🔗 <a href="${dealerUrl}">1. Direct Dealer Vehicle Page (South Austin Kia)</a>
📊 <a href="${carGurusSearchUrl}">2. CarGurus Regional EV9 Registry</a>
`;

  try {
    const res = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: chatId,
        text: message,
        parse_mode: 'HTML',
        disable_web_page_preview: false,
      }),
    });

    const data = await res.json();
    if (data.ok) {
      console.log(`🎉 LIVE DUAL-LINK TELEGRAM ALERT DISPATCHED FOR VIN: ${vin}`);
    } else {
      console.error('Telegram API Error:', data.description);
    }
  } catch (e: any) {
    console.error('Error sending alert:', e.message);
  }
}

sendDualLinkAlert();
