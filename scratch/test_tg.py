import sys
sys.path.insert(0, r"C:\Development\Apps\WS Deal Hunter\scratch\personal_repo_push\ws-deal-hunter")

from dotenv import load_dotenv
load_dotenv(r"C:\Development\Apps\WS Deal Hunter\.env")

from notifier import TelegramNotifier

notifier = TelegramNotifier()
print(f"Testing Telegram with token: {notifier.bot_token[:10]}... and chat_id: {notifier.chat_id}")
res = notifier.send_system_message(
    "Bot Heartbeat Active",
    "⚡ <b>Workstation Deal Hunter Online</b>\n\n"
    "💓 Always-On Hourly Heartbeat enabled.\n"
    "🔍 Scrapers expanded across r/hardwareswap, r/appleswap, r/homelabsales, r/LaptopDeals, r/thinkpad, and tech syndicated deal streams.\n\n"
    '<a href="https://wsdealhunter.streamlit.app/"><b>[OPEN WEB DASHBOARD]</b></a>'
)
print("Telegram Dispatch Result:", res)
