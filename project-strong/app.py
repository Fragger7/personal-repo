import streamlit as st
import httpx
import asyncio
import re
import pandas as pd
import logging
from datetime import datetime

# --- LOGGING CONFIGURATION ---
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("iptv_analytics")

# --- CONFIGURATION & CONSTANTS ---
st.set_page_config(page_title="IPTV Playlist Analytics", layout="wide", page_icon="🕵️‍♂️")

EVASION_HEADERS = {
    "User-Agent": "IPTVSmartersPro",
    "Accept": "*/*",
    "Connection": "keep-alive"
}

# --- HELPER FUNCTIONS ---
async def check_network_shield(client):
    """Pings a public utility to track outbound IP verification."""
    logger.info("Verifying outbound network shield status...")
    try:
        res = await client.get("https://api.ipify.org?format=json", timeout=3.0)
        ip = res.json().get("ip", "Unknown")
        logger.info(f"Outbound shield verification successful. Verified IP: {ip}")
        return ip
    except Exception as e:
        logger.warning(f"Outbound shield check failed. Network may be unprotected or disconnected: {str(e)}")
        return "DISCONNECTED / UNKNOWN"

def parse_m3u_urls(text_block):
    """Uses regex to isolate Host, Port, Username, and Password from messy strings."""
    logger.info("Scanning ingested block for Xtream Codes credential layouts...")
    pattern = r'(https?://[^/:]+(?::\d+)?)/player_api\.php\?username=([^&\s]+)&password=([^&\s]+)'
    matches = re.findall(pattern, text_block)
    
    # Fallback pattern for standard get.php lines
    if not matches:
        pattern_fallback = r'(https?://[^/:]+(?::\d+)?)/get\.php\?username=([^&\s]+)&password=([^&\s]+)'
        matches = re.findall(pattern_fallback, text_block)
        
    extracted = []
    for match in matches:
        base_url, user, password = match
        extracted.append({"base_url": base_url, "username": user, "password": password})
    logger.info(f"Parsing complete. Extracted {len(extracted)} account URLs.")
    return extracted

async def evaluate_account(client, account):
    """Tier 1: High-speed handshake verification workflow."""
    target_url = f"{account['base_url']}/player_api.php?username={account['username']}&password={account['password']}"
    logger.info(f"Evaluating connection status for: {account['base_url']} (User: {account['username']})")
    try:
        res = await client.get(target_url, timeout=6.0)
        
        # Catch plain authorization rejections
        if "Unauthorized" in res.text:
            logger.warning(f"Connection rejected (Unauthorized response body) for: {account['base_url']}")
            return {**account, "Status": "🟡 Expired / Invalid", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A"}
            
        data = res.json()
        user_info = data.get("user_info", {})
        
        if user_info.get("auth") == 0 or user_info.get("status") == "Expired":
            logger.warning(f"Authentication failed or account expired for: {account['base_url']}")
            return {**account, "Status": "🟡 Expired / Invalid", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A"}
        
        # Parse Valid Dynamic Attributes
        exp_timestamp = user_info.get("exp_date")
        formatted_date = "Unlimited"
        days_remaining = 9999
        
        if exp_timestamp and exp_timestamp.isdigit():
            ts = int(exp_timestamp)
            dt = datetime.fromtimestamp(ts)
            formatted_date = dt.strftime("%Y-%m-%d")
            days_remaining = (dt - datetime.now()).days

        logger.info(f"Connection active. Host: {account['base_url']} | Max Conn: {user_info.get('max_connections')} | Exp: {formatted_date}")
        return {
            **account,
            "Status": "🟢 Active",
            "Expires": formatted_date,
            "Days Left": max(0, days_remaining),
            "Max Conn": user_info.get("max_connections", "0"),
            "Active Conn": user_info.get("active_cons", "0"),
            "Channels": "N/A",
            "VODs": "N/A"
        }
        
    except httpx.HTTPStatusError as e:
        status_code = e.response.status_code
        logger.error(f"HTTP error status {status_code} encountered checking host {account['base_url']}")
        if status_code == 521:
            return {**account, "Status": "🔴 Offline (Server Dead)", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A"}
        return {**account, "Status": "🛡️ Firewalled / Blocked", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A"}
    except Exception as e:
        logger.error(f"Failed to connect to host {account['base_url']}: {str(e)}")
        return {**account, "Status": "🛡️ Firewalled / Blocked", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A"}

async def fetch_lazy_details(base_url, user, password, action):
    """Tier 2: On-Demand Lazy Loading details retrieval."""
    target_url = f"{base_url}/player_api.php?username={user}&password={password}&action={action}"
    logger.info(f"Lazy loading endpoint data for host {base_url} [Action: {action}]")
    try:
        async with httpx.AsyncClient(headers=EVASION_HEADERS, verify=False) as client:
            res = await client.get(target_url, timeout=8.0)
            data = res.json()
            logger.info(f"Lazy loading catalog fetch completed successfully. Type: {type(data)} | Elements: {len(data) if isinstance(data, list) else 1}")
            return data
    except Exception as e:
        logger.error(f"Lazy loading query failed for host {base_url} [Action: {action}]: {str(e)}")
        return []

# --- CORE USER INTERFACE ---
st.title("🕵️‍♂️ IPTV Playlist Analytics Dashboard")
st.caption("Host Constraints: Headless Low CPU Profile Optimization Active")
st.write("---")

# Setup active asynchronous network client block
async def main_network_render():
    async with httpx.AsyncClient(headers=EVASION_HEADERS, verify=False) as client:
        return await check_network_shield(client)

current_ip = asyncio.run(main_network_render())

# Outbound Security Badge
col_ip, col_status = st.columns([3, 1])
with col_ip:
    st.markdown("### 🛡️ Outbound Network Shield Monitoring")
with col_status:
    if "UNKNOWN" in current_ip or current_ip.startswith("DISCONNECTED"):
        st.error(f"🔴 SHIELD INACTIVE\nIP: {current_ip}")
    else:
        st.success(f"🟢 VPN PROTECTED\nIP: {current_ip}")

st.write("---")

# Data Entry Box
st.markdown("### 📋 Bulk Ingest Master Links")
pasted_data = st.text_area("Drop messy text, diagnostic blocks, or standard M3U configurations here:", height=150)

# Initialize session state for analysis results
if "playlist_results" not in st.session_state:
    st.session_state["playlist_results"] = None

if st.button("Analyze Playlist Nodes", disabled=("UNKNOWN" in current_ip)):
    accounts = parse_m3u_urls(pasted_data)
    
    if not accounts:
        st.warning("No valid Xtream Codes player or server parameters identified. Double-check your formatting input.")
        st.session_state["playlist_results"] = None
    else:
        st.info(f"Identified {len(accounts)} layout strings. Initializing throttled async handshakes...")
        
        async def process_batch():
            async with httpx.AsyncClient(headers=EVASION_HEADERS, verify=False) as client:
                tasks = [evaluate_account(client, acc) for acc in accounts]
                return await asyncio.gather(*tasks)
                
        results = asyncio.run(process_batch())
        st.session_state["playlist_results"] = results

# Render results from session state if they exist
if st.session_state["playlist_results"] is not None:
    df = pd.DataFrame(st.session_state["playlist_results"])
    
    # 1. Filter checkbox toggle
    show_only_active = st.checkbox("Show only Active Connections", value=False)
    
    display_df = df.copy()
    if show_only_active:
        display_df = display_df[display_df["Status"] == "🟢 Active"]
    
    st.markdown("### 📊 Live Grid Infrastructure Manifest")
    st.dataframe(display_df, use_container_width=True)
    
    # Lazy load channel and VOD count data to prevent initial handshake hangs
    active_accounts = [acc for acc in st.session_state["playlist_results"] if acc.get("Status") == "🟢 Active"]
    if active_accounts:
        needs_loading = any(acc.get("Channels") == "N/A" for acc in active_accounts)
        if needs_loading:
            if st.button("📊 Query Channels & VOD Counts (Active Only)"):
                with st.spinner("Downloading active server catalogs (this might take a few seconds)..."):
                    async def load_counts():
                        async with httpx.AsyncClient(headers=EVASION_HEADERS, verify=False) as client:
                            async def fetch_counts(acc):
                                channels_count = 0
                                vod_count = 0
                                try:
                                    logger.info(f"Downloading stream sizes for active node: {acc['base_url']}")
                                    live_url = f"{acc['base_url']}/player_api.php?username={acc['username']}&password={acc['password']}&action=get_live_streams"
                                    vod_url = f"{acc['base_url']}/player_api.php?username={acc['username']}&password={acc['password']}&action=get_vod_streams"
                                    
                                    live_res, vod_res = await asyncio.gather(
                                        client.get(live_url, timeout=7.0),
                                        client.get(vod_url, timeout=7.0),
                                        return_exceptions=True
                                    )
                                    if not isinstance(live_res, Exception) and live_res.status_code == 200:
                                        live_data = live_res.json()
                                        if isinstance(live_data, list):
                                            channels_count = len(live_data)
                                    if not isinstance(vod_res, Exception) and vod_res.status_code == 200:
                                        vod_data = vod_res.json()
                                        if isinstance(vod_data, list):
                                            vod_count = len(vod_data)
                                    logger.info(f"Successfully counted libraries for {acc['base_url']} - Channels: {channels_count} | VODs: {vod_count}")
                                except Exception as e:
                                    logger.error(f"Failed to query sizing stats for {acc['base_url']}: {str(e)}")
                                    pass
                                
                                acc["Channels"] = channels_count
                                acc["VODs"] = vod_count
                            
                            tasks = [fetch_counts(acc) for acc in active_accounts]
                            await asyncio.gather(*tasks)
                    
                    asyncio.run(load_counts())
                    st.success("Library counts loaded successfully!")
                    st.rerun()
    
    # --- TIER 2 LAZY LOADING ACCORDIONS ---
    st.write("---")
    st.markdown("### 🔍 On-Demand Deep-Dive Discovery")
    st.caption("Click individual drawers below to query deep metadata without overloading local threads.")
    
    for idx, row in display_df.iterrows():
        if row["Status"] == "🟢 Active":
            with st.expander(f"📁 Explore Structural Library for: {row['base_url']} [User: {row['username']}]"):
                st.write("📡 Querying target asset classifications sequentially...")
                
                # Fetch categories and the full stream list concurrently on visual expander activation
                # This guarantees that we can calculate counts accurately and perform local filtering
                st.write("📡 Fetching stream catalogs from IPTV host...")
                
                async def fetch_tier2_data():
                    return await asyncio.gather(
                        fetch_lazy_details(row['base_url'], row['username'], row['password'], "get_live_categories"),
                        fetch_lazy_details(row['base_url'], row['username'], row['password'], "get_live_streams"),
                        return_exceptions=True
                    )
                
                results = asyncio.run(fetch_tier2_data())
                live_cats, live_streams = results[0], results[1]
                
                if isinstance(live_cats, list) and isinstance(live_streams, list):
                    # Count streams per category locally
                    cat_counts = {}
                    for s in live_streams:
                        cid = str(s.get("category_id"))
                        cat_counts[cid] = cat_counts.get(cid, 0) + 1
                    
                    # Create dropdown options with counts in parentheses
                    cat_options = []
                    cat_map = {} # Maps display option back to category dictionary
                    
                    for c in live_cats:
                        cid = str(c.get("category_id"))
                        name = c.get("category_name", "Unknown")
                        count = cat_counts.get(cid, 0)
                        
                        display_name = f"{name} ({count})"
                        cat_options.append(display_name)
                        cat_map[display_name] = c
                    
                    st.metric("Live Categories Found", len(live_cats))
                    if cat_options:
                        selected_option = st.selectbox(
                            f"Active Live Packages ({row['username']})", 
                            options=cat_options, 
                            key=f"sel_{idx}"
                        )
                        
                        selected_cat = cat_map.get(selected_option)
                        if selected_cat:
                            cat_id = str(selected_cat.get("category_id"))
                            
                            # Filter streams locally (bypasses server-side filtering bugs)
                            matching_streams = [s for s in live_streams if str(s.get("category_id")) == cat_id]
                            
                            st.write(f"📡 Displaying **{len(matching_streams)}** channels in: **{selected_cat.get('category_name')}**")
                            
                            if matching_streams:
                                # Prepare stream dataframe for display
                                stream_data = []
                                for s in matching_streams:
                                    stream_data.append({
                                        "Num": s.get("num"),
                                        "Name": s.get("name"),
                                        "Stream ID": s.get("stream_id"),
                                        "Icon": s.get("stream_icon")
                                    })
                                
                                streams_df = pd.DataFrame(stream_data)
                                
                                # Render interactive channels grid with logos
                                st.dataframe(
                                    streams_df,
                                    column_config={
                                        "Icon": st.column_config.ImageColumn("Logo", help="Channel Logo"),
                                        "Num": st.column_config.NumberColumn("#"),
                                        "Name": st.column_config.TextColumn("Channel Name"),
                                        "Stream ID": st.column_config.NumberColumn("Stream ID")
                                    },
                                    use_container_width=True,
                                    hide_index=True
                                )
                            else:
                                st.info("No channels found in this group.")
                else:
                    st.text("Payload context restricted or limited by provider container setup.")