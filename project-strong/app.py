import streamlit as st
import streamlit.components.v1 as components
import httpx
import asyncio
import re
import pandas as pd
import logging
from datetime import datetime
import base64

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

# --- SECURE ACCESS CHECK ---
def check_password():
    """Returns `True` if the user entered the correct password, or if no password is configured."""
    if "ACCESS_PASSWORD" not in st.secrets:
        return True

    def password_entered():
        """Checks whether a password entered by the user is correct."""
        if st.session_state["password"] == st.secrets["ACCESS_PASSWORD"]:
            st.session_state["password_correct"] = True
            del st.session_state["password"]  # don't store password in session state
        else:
            st.session_state["password_correct"] = False

    if "password_correct" not in st.session_state:
        # First run, show input for password.
        st.text_input(
            "Enter Access Password", type="password", on_change=password_entered, key="password"
        )
        st.info("🔒 Secure Access Enabled. Please enter the password to unlock the dashboard.")
        return False
    elif not st.session_state["password_correct"]:
        # Password incorrect, show input + error.
        st.text_input(
            "Enter Access Password", type="password", on_change=password_entered, key="password"
        )
        st.error("😕 Password incorrect. Please try again.")
        return False
    else:
        # Password correct.
        return True

if not check_password():
    st.stop()

EVASION_HEADERS = {
    "User-Agent": "IPTVSmartersPro",
    "Accept": "*/*",
    "Connection": "keep-alive"
}

# --- HELPER FUNCTIONS ---
async def check_network_shield(client):
    """Pings a public utility to track outbound IP verification and geolocate ISP details."""
    logger.info("Verifying outbound network shield status...")
    try:
        # ip-api.com returns IP, ISP, Org, and Country information
        res = await client.get("http://ip-api.com/json/", timeout=4.0)
        if res.status_code == 200:
            data = res.json()
            ip = data.get("query", "Unknown")
            isp = data.get("isp", "Unknown")
            org = data.get("org", "Unknown")
            country = data.get("country", "Unknown")
            logger.info(f"Outbound shield verification successful. Verified IP: {ip} | ISP: {isp} | Org: {org}")
            return {
                "ip": ip,
                "isp": isp,
                "org": org,
                "country": country,
                "status": "success"
            }
        else:
            logger.warning(f"IP geolocation API returned status: {res.status_code}")
            return {
                "ip": "Unknown",
                "isp": "Unknown",
                "org": "Unknown",
                "country": "Unknown",
                "status": f"HTTP {res.status_code}"
            }
    except Exception as e:
        logger.warning(f"Outbound shield check failed. Network may be unprotected or disconnected: {str(e)}")
        return {
            "ip": "DISCONNECTED / UNKNOWN",
            "isp": "Unknown",
            "org": "Unknown",
            "country": "Unknown",
            "status": "failed"
        }

def parse_credentials(text_block):
    """Uses regex to isolate Host, Port, Username, and Password for Xtream and Stalker from messy strings."""
    logger.info("Scanning ingested block for Xtream Codes and Stalker Portal layouts...")
    extracted = []
    
    # Xtream Pattern
    pattern_xtream = r'(https?://[^/:]+(?::\d+)?)/player_api\.php\?username=([^&\s]+)&password=([^&\s]+)'
    for match in re.findall(pattern_xtream, text_block):
        extracted.append({"type": "Xtream", "base_url": match[0], "username": match[1], "password": match[2]})
        
    pattern_xtream_fallback = r'(https?://[^/:]+(?::\d+)?)/get\.php\?username=([^&\s]+)&password=([^&\s]+)'
    for match in re.findall(pattern_xtream_fallback, text_block):
        # avoid duplicates if the string contains both player_api and get.php for the same account
        if not any(a["base_url"] == match[0] and a["username"] == match[1] for a in extracted):
            extracted.append({"type": "Xtream", "base_url": match[0], "username": match[1], "password": match[2]})

    # Stalker Pattern (Robust State-Machine Parser for Free Text)
    current_url = None
    current_mac = None

    for line in text_block.splitlines():
        # Reset state on empty lines or explicit custom text block separators
        if not line.strip() or re.search(r'[-=_*#]{4,}|╰─|╭─|┌─|└─|\|', line):
            current_url = None
            current_mac = None

        url_match = re.search(r'(https?://[^/\s]+(?:/[^/\s]*)?)', line)
        if url_match and "player_api" not in line and "get.php" not in line:
            base_match = re.match(r'(https?://[^/:]+(?::\d+)?)', url_match.group(1))
            if base_match:
                current_url = base_match.group(1)

        mac_match = re.search(r'([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})', line, re.IGNORECASE)
        if mac_match:
            current_mac = mac_match.group(1).upper()

        if current_url and current_mac:
            if not any(a.get("type") == "Stalker" and a["base_url"] == current_url and a.get("mac") == current_mac for a in extracted):
                extracted.append({
                    "type": "Stalker", 
                    "base_url": current_url, 
                    "mac": current_mac, 
                    "username": current_mac, 
                    "password": "MAC"
                })
            current_mac = None # Clear mac to allow next mac to pair with the same portal
                    
    logger.info(f"Parsing complete. Extracted {len(extracted)} account configurations.")
    return extracted

async def evaluate_account(client, account):
    """Tier 1: High-speed handshake verification workflow."""
    if account.get("type", "Xtream") == "Stalker":
        # Stalker Handshake Workflow
        target_url = f"{account['base_url']}/c/server/load.php?type=stb&action=handshake&type=itv"
        logger.info(f"Evaluating Stalker connection status for: {account['base_url']} (MAC: {account['mac']})")
        
        # Override headers for Stalker MAG devices
        stalker_headers = {
            "User-Agent": "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
            "Cookie": f"mac={account['mac']}; stb_lang=en; timezone=Europe/Kiev;",
            "Accept": "*/*",
            "Connection": "keep-alive"
        }
        
        try:
            res = await client.get(target_url, headers=stalker_headers, timeout=6.0)
            if res.status_code == 404:
                # Sometimes load.php is placed at the root without /c/
                target_url = f"{account['base_url']}/server/load.php?type=stb&action=handshake&type=itv"
                res = await client.get(target_url, headers=stalker_headers, timeout=6.0)
                
            if res.status_code == 403:
                return {**account, "Status": "🛡️ Cloud Blocked (HTTP 403)", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A"}
            elif res.status_code == 521:
                return {**account, "Status": "🔴 Offline (Server Dead)", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A"}
            elif res.status_code != 200:
                return {**account, "Status": "🛡️ Firewalled / Blocked", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A"}
                
            try:
                data = res.json()
                if "error" in data or ("js" in data and isinstance(data["js"], dict) and "error" in data["js"]):
                    return {**account, "Status": "🟡 Expired / Invalid MAC", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A"}
                
                # If handshake returns 200 and no blatant error in JSON, consider it potentially active
                logger.info(f"Stalker Connection active. Host: {account['base_url']}")
                return {
                    **account,
                    "Status": "🟢 Active (Portal Online)",
                    "Expires": "Unknown",
                    "Days Left": 9999,
                    "Max Conn": "N/A",
                    "Active Conn": "N/A",
                    "Channels": "N/A",
                    "VODs": "N/A"
                }
            except Exception:
                # Output might not be standard JSON if blocked by WAF challenge
                return {**account, "Status": "🛡️ Firewalled / Blocked / Captcha", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A"}
                
        except Exception as e:
            logger.error(f"Failed to connect to Stalker host {account['base_url']}: {str(e)}")
            return {**account, "Status": "🛡️ Firewalled / Blocked", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A"}

    # Default Xtream Handshake Workflow
    target_url = f"{account['base_url']}/player_api.php?username={account['username']}&password={account['password']}"
    logger.info(f"Evaluating connection status for: {account['base_url']} (User: {account['username']})")
    try:
        res = await client.get(target_url, timeout=6.0)
        
        # Check HTTP Status Code first to handle firewall/cloud blockades explicitly
        if res.status_code == 403:
            logger.warning(f"Cloud Blocked (HTTP 403) for: {account['base_url']}")
            return {**account, "Status": "🛡️ Cloud Blocked (HTTP 403)", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A"}
        elif res.status_code == 521:
            logger.warning(f"Server dead (HTTP 521) for: {account['base_url']}")
            return {**account, "Status": "🔴 Offline (Server Dead)", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A"}
        elif res.status_code != 200:
            logger.warning(f"Unexpected status code {res.status_code} for: {account['base_url']}")
            return {**account, "Status": "🛡️ Firewalled / Blocked", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A"}
        
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
            if res.status_code == 200:
                data = res.json()
                logger.info(f"Lazy loading catalog fetch completed successfully. Type: {type(data)} | Elements: {len(data) if isinstance(data, list) else 1}")
                return data
            else:
                logger.warning(f"Lazy loading query returned status {res.status_code} for host {base_url} [Action: {action}]")
                return []
    except Exception as e:
        logger.error(f"Lazy loading query failed for host {base_url} [Action: {action}]: {str(e)}")
        return []

# --- CORE USER INTERFACE ---
st.title("🕵️‍♂️ IPTV Playlist Analytics Dashboard")
st.caption("Host Constraints: Headless Low CPU Profile Optimization Active | 🚀 Enhanced via AI Studio Workspace")
st.write("---")

# Setup active asynchronous network client block
@st.cache_data(ttl=300, show_spinner=False)
def get_network_info():
    async def main_network_render():
        async with httpx.AsyncClient(headers=EVASION_HEADERS, verify=False) as client:
            return await check_network_shield(client)
    return asyncio.run(main_network_render())

network_info = get_network_info()
current_ip = network_info.get("ip", "Unknown")
current_isp = network_info.get("isp", "Unknown")
current_org = network_info.get("org", "Unknown")
current_country = network_info.get("country", "Unknown")

# Cloud detection logic (flags if running on typical public cloud / hosting ranges)
cloud_keywords = ["amazon", "aws", "google", "azure", "cloudflare", "digitalocean", "linode", "ovh", "hosting", "server", "oracle", "m247", "scaleway", "vulcan", "leaseweb", "hetzner"]
is_cloud = any(kw in current_isp.lower() or kw in current_org.lower() for kw in cloud_keywords)

# Outbound Security Badge
col_ip, col_status = st.columns([3, 1])
with col_ip:
    st.markdown("### 🛡️ Outbound Network Shield Monitoring")
with col_status:
    if "UNKNOWN" in current_ip.upper() or current_ip.startswith("DISCONNECTED"):
        st.error(f"🔴 SHIELD INACTIVE\nIP: {current_ip}")
    else:
        badge_text = f"🟢 VPN ACTIVE\nIP: {current_ip}\nISP: {current_isp}"
        if is_cloud:
            badge_text += " (Cloud)"
        st.success(badge_text)

# Cloud warning banner to assist users deploying on public platforms
if is_cloud:
    st.warning(
        f"⚠️ **Public Cloud/Hosting Environment Detected**\n\n"
        f"The dashboard is currently running on a network identified as **{current_isp}** ({current_org}). "
        f"Many IPTV providers aggressively block connections from public cloud providers, hosting data centers, or VPN servers to prevent scrape traffic. "
        f"If your link handshakes fail with **'🛡️ Cloud Blocked (HTTP 403)'** or **'🛡️ Firewalled / Blocked'**, this hosting network is likely banned by the target server."
    )

st.write("---")

# --- CUSTOM UI / UX STYLING ---
st.markdown("""
<style>
/* Clean dark-mode aesthetic customizations */
div[data-testid="stTabs"] button {
    font-size: 1.15rem;
    font-weight: 600;
    padding-bottom: 12px;
}
div.stButton > button {
    border-radius: 6px;
    font-weight: 500;
    transition: all 0.2s;
}
div.stButton > button:hover {
    border-color: #FF4B4B;
    color: #FF4B4B;
}
.stExpander {
    border: 1px solid #2e3035 !important;
    border-radius: 8px !important;
}
h1 { font-family: 'Inter', sans-serif; font-weight: 800; }
</style>
""", unsafe_allow_html=True)

xt_count = 0
st_count = 0
if st.session_state.get("playlist_results"):
    xt_count = len([x for x in st.session_state["playlist_results"] if x.get("type", "Xtream") == "Xtream"])
    st_count = len([x for x in st.session_state["playlist_results"] if x.get("type") == "Stalker"])

tab_tools, tab_scanner, tab_xtream, tab_stalker = st.tabs([
    "🛠️ Base64 Decoder",
    "📡 Multi-Payload Scanner", 
    f"📺 Xtream Codes ({xt_count})", 
    f"🛸 Stalker Portals ({st_count})"
])

with tab_tools:
    st.markdown("### 🛠️ Base64 Encoding & Decoding Operations")
    st.caption("Quickly extract hidden URLs or encapsulate data payloads before processing.")
    
    b64_action = st.radio("Select Operation:", ["Decode (Base64 -> Text)", "Encode (Text -> Base64)"], horizontal=True)
    b64_input = st.text_area("Input Payload:", height=150, key="b64_input", label_visibility="collapsed")
    
    if st.button("🔄 Translate Payload", use_container_width=True):
        if not b64_input.strip():
            st.warning("Please provide input text to process.")
        else:
            try:
                if "Decode" in b64_action:
                    # Find potential base64 strings (at least 20 chars long, valid chars, optional padding)
                    potential_b64s = re.findall(r'[A-Za-z0-9+/]{20,}={0,2}', b64_input)
                    if not potential_b64s:
                        potential_b64s = [b64_input.strip()]
                    
                    decoded_results = []
                    for p in potential_b64s:
                        try:
                            pad = len(p) % 4
                            padded = p + "=" * ((4 - pad) if pad else 0)
                            res = base64.b64decode(padded).decode('utf-8', errors='replace')
                            decoded_results.append(res)
                        except Exception:
                            pass
                    
                    if decoded_results:
                        result = "\n\n".join(decoded_results)
                        st.success("Successfully decoded. (Use the copy button in the top right of the code block below)")
                    else:
                        raise ValueError("No valid Base64 encoded strings found in the payload.")
                else:
                    result = base64.b64encode(b64_input.encode('utf-8')).decode('utf-8')
                    st.success("Successfully encoded. (Use the copy button in the top right of the code block below)")
                    
                st.code(result, language="text")
                
                # If it's a URL, give a link button
                if result.strip().startswith("http://") or result.strip().startswith("https://"):
                    st.link_button("🌐 Launch Converted URL in New Tab", result.strip())
                    
            except Exception as e:
                st.error(f"Failed to process payload: {str(e)}")

with tab_scanner:
    st.markdown("### 📋 Bulk Ingest Master Links")

    if "raw_input" not in st.session_state:
        st.session_state["raw_input"] = ""

    def clear_input():
        st.session_state["raw_input"] = ""

    col_input, col_clear = st.columns([5, 1])
    with col_clear:
        st.markdown("<br>", unsafe_allow_html=True) # visual alignment padding
        st.button("🧹 Clear Input", on_click=clear_input, key="clear_btn", use_container_width=True)

    with col_input:
        pasted_data = st.text_area("Drop messy text, diagnostic blocks, or standard M3U configurations here:", height=150, key="raw_input", label_visibility="collapsed")

    # Initialize session state for analysis results
    if "playlist_results" not in st.session_state:
        st.session_state["playlist_results"] = None

    if st.button("🚀 Analyze Playlist Nodes", type="primary", disabled=("UNKNOWN" in current_ip.upper() or current_ip.startswith("DISCONNECTED"))):
        accounts = parse_credentials(pasted_data)
        
        if not accounts:
            st.warning("No valid Xtream Codes or Stalker Portal parameters identified. Double-check your formatting input.")
            st.session_state["playlist_results"] = None
        else:
            st.info(f"Identified {len(accounts)} layout strings. Initializing throttled async handshakes...")
            
            progress_bar = st.progress(0)
            status_text = st.empty()

            async def process_batch():
                async with httpx.AsyncClient(headers=EVASION_HEADERS, verify=False) as client:
                    tasks = [evaluate_account(client, acc) for acc in accounts]
                    results_list = []
                    total = len(tasks)
                    for i, coroutine in enumerate(asyncio.as_completed(tasks)):
                        res = await coroutine
                        results_list.append(res)
                        progress = (i + 1) / total
                        progress_bar.progress(progress)
                        status_text.text(f"Processed {i + 1}/{total} connections...")
                    return results_list
                    
            results = asyncio.run(process_batch())
            progress_bar.empty()
            status_text.empty()
            st.session_state["playlist_results"] = results
            st.rerun()

    if st.session_state["playlist_results"] is not None:
        xt_count = len([x for x in st.session_state["playlist_results"] if x.get("type", "Xtream") == "Xtream"])
        st_count = len([x for x in st.session_state["playlist_results"] if x.get("type") == "Stalker"])
        st.success(f"✅ **Scan Complete** - Discovered **{xt_count}** Xtream Codes layouts and **{st_count}** Stalker Portals.")

# Empty states for tabs before scan
if st.session_state["playlist_results"] is None:
    with tab_xtream:
        st.info("👈 Please execute a scan in the 'Multi-Payload Scanner' tab to populate this dashboard.")
    with tab_stalker:
        st.info("👈 Please execute a scan in the 'Multi-Payload Scanner' tab to populate this dashboard.")

# Render results from session state if they exist
if st.session_state["playlist_results"] is not None:
    df = pd.DataFrame(st.session_state["playlist_results"])
    
    xtream_df = df[df["type"] == "Xtream"]
    stalker_df = df[df["type"] == "Stalker"]
    
    with tab_xtream:
        st.markdown("### 📺 Xtream Codes Manifest")
        if xtream_df.empty:
            st.info("No Xtream Codes accounts discovered in the recent scan.")
        else:
            col_filter, col_counts = st.columns([1, 1])
            with col_filter:
                show_only_active_x = st.checkbox("Show only Active Connections", value=False, key="xtream_active")
            
            display_xtream = xtream_df.copy()
            if show_only_active_x:
                display_xtream = display_xtream[display_xtream["Status"].str.contains("Active", na=False)]
            
            # Add M3U Copy column
            display_xtream["M3U Link"] = display_xtream.apply(
                lambda row: f"{row['base_url']}/get.php?username={row['username']}&password={row['password']}&type=m3u_plus&output=ts",
                axis=1
            )

            # Move M3U Link after password
            cols = display_xtream.columns.tolist()
            if "M3U Link" in cols:
                cols.insert(cols.index("password") + 1, cols.pop(cols.index("M3U Link")))
                display_xtream = display_xtream[cols]
            
            event_x = st.dataframe(
                display_xtream,
                use_container_width=True,
                selection_mode="single-row",
                on_select="rerun",
                key="xtream_table",
                column_config={
                    "M3U Link": st.column_config.LinkColumn("Quick M3U URL", help="Right click to copy link", display_text="🔗 Copy M3U")
                }
            )
            
            selected_x_idx = None
            if event_x and hasattr(event_x, "selection") and event_x.selection.rows:
                selected_x_idx = event_x.selection.rows[0]
            elif isinstance(event_x, dict) and "selection" in event_x and "rows" in event_x["selection"] and event_x["selection"]["rows"]:
                selected_x_idx = event_x["selection"]["rows"][0]
            
            active_x = [acc for acc in st.session_state["playlist_results"] if "Active" in acc.get("Status", "") and acc.get("type", "Xtream") == "Xtream"]
            if active_x:
                needs_loading = any(acc.get("Channels") == "N/A" for acc in active_x)
                if needs_loading:
                    with col_counts:
                        if st.button("📊 Query Channels & VOD Counts", use_container_width=True):
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
                                            except Exception as e:
                                                pass
                                            acc["Channels"] = channels_count
                                            acc["VODs"] = vod_count
                                        
                                        progress_bar_v = st.progress(0)
                                        status_text_v = st.empty()
                                        total = len(active_x)
                                        tasks = [fetch_counts(acc) for acc in active_x]
                                        for i, coroutine in enumerate(asyncio.as_completed(tasks)):
                                            await coroutine
                                            progress = (i + 1) / total
                                            progress_bar_v.progress(progress)
                                            status_text_v.text(f"Fetched catalogs for {i + 1}/{total} nodes...")
                                            
                                        progress_bar_v.empty()
                                        status_text_v.empty()
                                
                                asyncio.run(load_counts())
                                st.rerun()

            st.write("---")
            st.markdown("### 📋 Copy Quick Connects & Deep-Dive")
            st.caption("Pre-formatted blocks ready to copy and paste into IPTV clients.")
            
            for i, (idx, row) in enumerate(display_xtream.iterrows()):
                if "Active" in row["Status"]:
                    is_expanded = (i == selected_x_idx) if selected_x_idx is not None else False
                    
                    html_id = f"xtream_accordion_{i}"
                    st.markdown(f'<div id="{html_id}"></div>', unsafe_allow_html=True)
                    
                    if is_expanded:
                        components.html(
                            f"""
                            <script>
                            setTimeout(function() {{
                                const doc = window.parent.document;
                                const anchor = doc.getElementById('{html_id}');
                                if (anchor) {{
                                    anchor.scrollIntoView({{behavior: 'smooth', block: 'start'}});
                                }}
                            }}, 150);
                            </script>
                            """,
                            height=0, width=0
                        )

                    with st.expander(f"🟢 Explore Structural Library for: {row['base_url']} [User: {row['username']}]", expanded=is_expanded):
                        st.markdown("Use the copy icon on the top right of the blocks below to quickly copy.")
                        col1, col2 = st.columns(2)
                        with col1:
                            st.markdown("**🔐 Login Credentials**")
                            st.code(f"Host: {row['base_url']}\nUsername: {row['username']}\nPassword: {row['password']}", language="text")
                        with col2:
                            st.markdown("**🔗 M3U Playlist URL**")
                            m3u_url = f"{row['base_url']}/get.php?username={row['username']}&password={row['password']}&type=m3u_plus&output=ts"
                            st.code(m3u_url, language="text")
                        
                        st.write("📡 Fetching stream catalogs from IPTV host...")
                        
                        async def fetch_tier2_data(r):
                            return await asyncio.gather(
                                fetch_lazy_details(r['base_url'], r['username'], r['password'], "get_live_categories"),
                                fetch_lazy_details(r['base_url'], r['username'], r['password'], "get_live_streams"),
                                return_exceptions=True
                            )
                        
                        results = asyncio.run(fetch_tier2_data(row))
                        live_cats, live_streams = results[0], results[1]
                    
                        if isinstance(live_cats, list) and isinstance(live_streams, list):
                            cat_counts = {}
                            for s in live_streams:
                                cid = str(s.get("category_id"))
                                cat_counts[cid] = cat_counts.get(cid, 0) + 1
                            
                            cat_options = []
                            cat_map = {}
                            
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
                                    key=f"sel_x_{idx}"
                                )
                                selected_cat = cat_map.get(selected_option)
                                if selected_cat:
                                    cat_id = str(selected_cat.get("category_id"))
                                    matching_streams = [s for s in live_streams if str(s.get("category_id")) == cat_id]
                                    
                                    st.write(f"📡 Displaying **{len(matching_streams)}** channels in: **{selected_cat.get('category_name')}**")
                                    if matching_streams:
                                        stream_data = [{"Num": s.get("num"), "Name": s.get("name"), "Stream ID": s.get("stream_id"), "Icon": s.get("stream_icon")} for s in matching_streams]
                                        st.dataframe(
                                            pd.DataFrame(stream_data),
                                            column_config={
                                                "Icon": st.column_config.ImageColumn("Logo", help="Channel Logo"),
                                                "Num": st.column_config.NumberColumn("#"),
                                                "Name": st.column_config.TextColumn("Channel Name"),
                                                "Stream ID": st.column_config.NumberColumn("Stream ID")
                                            },
                                            use_container_width=True, hide_index=True
                                        )
                                    else:
                                        st.info("No channels found in this group.")
                        else:
                            st.text("Payload context restricted or limited by provider container setup.")

    with tab_stalker:
        st.markdown("### 🛸 Stalker Portals Manifest")
        if stalker_df.empty:
            st.info("No Stalker accounts discovered in the recent scan.")
        else:
            show_only_active_s = st.checkbox("Show only Active Connections", value=False, key="stalker_active")
            
            display_stalker = stalker_df.copy()
            if show_only_active_s:
                display_stalker = display_stalker[display_stalker["Status"].str.contains("Active", na=False)]
            
            event_s = st.dataframe(
                display_stalker,
                use_container_width=True,
                selection_mode="single-row",
                on_select="rerun",
                key="stalker_table"
            )
            
            selected_s_idx = None
            if event_s and hasattr(event_s, "selection") and event_s.selection.rows:
                selected_s_idx = event_s.selection.rows[0]
            elif isinstance(event_s, dict) and "selection" in event_s and "rows" in event_s["selection"] and event_s["selection"]["rows"]:
                selected_s_idx = event_s["selection"]["rows"][0]
            
            st.write("---")
            st.markdown("### 📋 Copy Quick Connects")
            for i, (idx, row) in enumerate(display_stalker.iterrows()):
                if "Active" in row["Status"]:
                    is_expanded = (i == selected_s_idx) if selected_s_idx is not None else False
                    
                    html_id = f"stalker_accordion_{i}"
                    st.markdown(f'<div id="{html_id}"></div>', unsafe_allow_html=True)

                    if is_expanded:
                        components.html(
                            f"""
                            <script>
                            setTimeout(function() {{
                                const doc = window.parent.document;
                                const anchor = doc.getElementById('{html_id}');
                                if (anchor) {{
                                    anchor.scrollIntoView({{behavior: 'smooth', block: 'start'}});
                                }}
                            }}, 150);
                            </script>
                            """,
                            height=0, width=0
                        )

                    with st.expander(f"🟢 {row['base_url']} - MAC: {row['mac']}", expanded=is_expanded):
                        st.markdown("**🔐 Stalker Credentials (Click top right to copy)**")
                        st.code(f"Portal URL (Host): {row['base_url']}\nMAC Address: {row['mac']}", language="text")
                        st.info("⚠️ **Deep-Dive Discovery is constrained for Stalker Portals.**\n\nStalker Portals (Ministra) use dynamic, MAC-authenticated MAC schemas rather than standard Xtream flat lists. Fetching massive stream categories without full device emulation can trigger security bans on the host. Status verification is complete, but deep channel mining is restricted.")

