import streamlit as st
import streamlit.components.v1 as components
import httpx
import asyncio
import re
import pandas as pd
import json
import os
import subprocess
import logging
from datetime import datetime
import base64

def get_secret_safe(key):
    try:
        if key in st.secrets:
            return st.secrets[key]
    except Exception:
        pass
    env_key = f"STREAMLIT_{key}"
    if env_key in os.environ:
        return os.environ[env_key]
    return None

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
st.set_page_config(page_title="IPTV Playlist Analytics", layout="wide", page_icon="📡")

# --- COMMITTED DATASET LOGIC ---
COMMITTED_FILE = "committed.json"

def load_committed_data():
    if os.path.exists(COMMITTED_FILE):
        with open(COMMITTED_FILE, "r", encoding="utf-8") as f:
            try:
                return json.load(f)
            except json.JSONDecodeError:
                return []
    return []

def pull_committed_data(force=False):
    """ 
    Fetches the latest JSON from GitHub, writes to local disk, 
    and returns (data_list, sha). 
    If no GITHUB_TOKEN or fetch fails, returns local load_committed_data() and None.
    """
    local_data = load_committed_data()
    token = get_secret_safe("GITHUB_TOKEN")
    if not token:
        return local_data, None
        
    try:
        import base64
        repo_slug = "Fragger7/personal-repo" 
        file_path = "project-strong/committed.json"
        url = f"https://api.github.com/repos/{repo_slug}/contents/{file_path}"
        headers = {
            "Authorization": f"token {token}",
            "Accept": "application/vnd.github.v3+json"
        }
        
        r = httpx.get(url, headers=headers)
        if r.status_code == 200:
            resp_json = r.json()
            sha = resp_json.get("sha")
            content_b64 = resp_json.get("content", "")
            if content_b64:
                try:
                    remote_json_str = base64.b64decode(content_b64).decode("utf-8")
                    remote_data = json.loads(remote_json_str)
                    
                    # Merge local data into remote data so no unpushed local records are lost
                    merged_data = list(remote_data)
                    for l_rec in local_data:
                        exists = any(
                            (r_rec.get("base_url") == l_rec.get("base_url")) and 
                            (r_rec.get("username", "") == l_rec.get("username", "")) and 
                            (r_rec.get("mac", "") == l_rec.get("mac", ""))
                            for r_rec in remote_data
                        )
                        if not exists:
                            merged_data.append(l_rec)
                    
                    # Update local state if remote has changing contents
                    with open(COMMITTED_FILE, "w", encoding="utf-8") as f:
                        f.write(json.dumps(merged_data, indent=4))
                    return merged_data, sha
                except Exception as merge_err:
                    logger.error(f"Failed to parse remote JSON: {merge_err}")
    except Exception as e:
        logger.error(f"Failed to pull remote data: {e}")
        
    return load_committed_data(), None

def save_committed_data(data_list, sha=None):
    """
    Writes data_list to local disk immediately and forces a push to GitHub 
    with the exact dataset, using the provided SHA.
    """
    json_str = json.dumps(data_list, indent=4)
    with open(COMMITTED_FILE, "w", encoding="utf-8") as f:
        f.write(json_str)
        
    token = get_secret_safe("GITHUB_TOKEN")
    if token:
        try:
            import base64
            repo_slug = "Fragger7/personal-repo" 
            file_path = "project-strong/committed.json"
            url = f"https://api.github.com/repos/{repo_slug}/contents/{file_path}"
            headers = {
                "Authorization": f"token {token}",
                "Accept": "application/vnd.github.v3+json"
            }
            
            r = httpx.get(url, headers=headers)
            if r.status_code == 200:
                resp_json = r.json()
                sha = resp_json.get("sha")
                content_b64 = resp_json.get("content", "")
                if content_b64:
                    try:
                        remote_json_str = base64.b64decode(content_b64).decode("utf-8")
                        remote_data = json.loads(remote_json_str)
                        if json.dumps(remote_data, sort_keys=True) == json.dumps(data_list, sort_keys=True):
                            logger.info("Remote data matches local exactly. Skipping push.")
                            return
                    except Exception as parse_err:
                        pass
            
            payload = {
                "message": "Update: committed.json (Streamlit Cloud Auto-Push)",
                "content": base64.b64encode(json_str.encode("utf-8")).decode("utf-8"),
                "branch": "main"
            }
            if sha:
                payload["sha"] = sha
                
            put_r = httpx.put(url, headers=headers, json=payload)
            if put_r.status_code not in [200, 201]:
                logger.error(f"GitHub API push failed: {put_r.text}")
        except Exception as e:
            logger.error(f"Failed to auto-push to GitHub API: {e}")
    else:
        logger.warning("GITHUB_TOKEN not found in st.secrets. Skipping remote push.")

def commit_record(record, tab_type):
    # Fetch from Git first to eliminate race conditions
    current, sha = pull_committed_data()
    
    # Normalize record
    base_url = record.get("base_url", "")
    username = record.get("username", "")
    mac = record.get("mac", "")
    
    # Check duplicates and delete latest/existing ensuring 1 record is kept.
    # User rule: "if duplicate records are selected, the just delete the latest one, ensuring only 1 record is kept and date is traced from the original selection."
    existing = None
    for r in current:
        if tab_type == "Xtream" and r.get("base_url") == base_url and r.get("username") == username:
            existing = r
            break
        elif tab_type == "Stalker" and r.get("base_url") == base_url and r.get("mac") == mac:
            existing = r
            break
            
    if existing:
        # Keep the original selection date and notes
        orig_date = existing.get("Date Selected", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        orig_notes = existing.get("Notes", "")
        # Remove old to replace with updated metadata, but persist original datetimes
        current.remove(existing)
        new_rec = {**record, "Source": tab_type, "Date Selected": orig_date, "Notes": orig_notes}
    else:
        new_rec = {**record, "Source": tab_type, "Date Selected": datetime.now().strftime("%Y-%m-%d %H:%M:%S"), "Notes": ""}
        
    current.append(new_rec)
    save_committed_data(current, sha=sha)
    return True

def delete_committed_record(tgt_base_url, tgt_user, tgt_mac):
    current, sha = pull_committed_data()
    to_remove = None
    for r in current:
        if r.get("base_url") == tgt_base_url and r.get("username", "") == tgt_user and r.get("mac", "") == tgt_mac:
            to_remove = r
            break
            
    if to_remove:
        current.remove(to_remove)
        save_committed_data(current, sha=sha)
        return True
    return False

def update_committed_notes(tgt_base_url, tgt_user, tgt_mac, notes_val):
    current, sha = pull_committed_data()
    updated = False
    for r in current:
        if r.get("base_url") == tgt_base_url and r.get("username", "") == tgt_user and r.get("mac", "") == tgt_mac:
            r["Notes"] = notes_val
            updated = True
            break
            
    if updated:
        save_committed_data(current, sha=sha)
        return True
    return False

# --- COMMITTED DATASET SYNC ---
if "has_pulled_initially" not in st.session_state:
    st.session_state["has_pulled_initially"] = True
    pull_committed_data()

PROVIDER_INTEL_FILE = "provider_intelligence.json"

def load_provider_intel():
    if os.path.exists(PROVIDER_INTEL_FILE):
        with open(PROVIDER_INTEL_FILE, "r", encoding="utf-8") as f:
            try:
                return json.load(f)
            except json.JSONDecodeError:
                return {}
    return {}

def pull_provider_intel(force=False):
    local_data = load_provider_intel()
    token = get_secret_safe("GITHUB_TOKEN")
    if not token:
        return local_data, None
        
    try:
        import base64
        repo_slug = "Fragger7/personal-repo" 
        file_path = "project-strong/provider_intelligence.json"
        url = f"https://api.github.com/repos/{repo_slug}/contents/{file_path}"
        headers = {
            "Authorization": f"token {token}",
            "Accept": "application/vnd.github.v3+json"
        }
        
        r = httpx.get(url, headers=headers)
        if r.status_code == 200:
            resp_json = r.json()
            sha = resp_json.get("sha")
            content_b64 = resp_json.get("content", "")
            if content_b64:
                try:
                    remote_json_str = base64.b64decode(content_b64).decode("utf-8")
                    remote_data = json.loads(remote_json_str)
                    
                    merged_data = dict(remote_data)
                    for k, v in local_data.items():
                        if k not in merged_data:
                            merged_data[k] = v
                        else:
                            merged_data[k].update(v)
                    
                    with open(PROVIDER_INTEL_FILE, "w", encoding="utf-8") as f:
                        f.write(json.dumps(merged_data, indent=4))
                    return merged_data, sha
                except Exception as merge_err:
                    logger.error(f"Failed to parse remote provider intel JSON: {merge_err}")
    except Exception as e:
        logger.error(f"Failed to pull remote provider intel: {e}")
        
    return load_provider_intel(), None

def save_provider_intel(data_dict, sha=None):
    json_str = json.dumps(data_dict, indent=4)
    with open(PROVIDER_INTEL_FILE, "w", encoding="utf-8") as f:
        f.write(json_str)
        
    token = get_secret_safe("GITHUB_TOKEN")
    if token:
        try:
            import base64
            repo_slug = "Fragger7/personal-repo" 
            file_path = "project-strong/provider_intelligence.json"
            url = f"https://api.github.com/repos/{repo_slug}/contents/{file_path}"
            headers = {
                "Authorization": f"token {token}",
                "Accept": "application/vnd.github.v3+json"
            }
            
            r = httpx.get(url, headers=headers)
            if r.status_code == 200:
                resp_json = r.json()
                sha = resp_json.get("sha")
                content_b64 = resp_json.get("content", "")
                if content_b64:
                    try:
                        remote_json_str = base64.b64decode(content_b64).decode("utf-8")
                        remote_data = json.loads(remote_json_str)
                        if json.dumps(remote_data, sort_keys=True) == json.dumps(data_dict, sort_keys=True):
                            logger.info("Remote provider data matches local exactly. Skipping push.")
                            return
                    except Exception as parse_err:
                        pass
            
            payload = {
                "message": "Update: provider_intelligence.json (Auto-Learning)",
                "content": base64.b64encode(json_str.encode("utf-8")).decode("utf-8"),
                "branch": "main"
            }
            if sha:
                payload["sha"] = sha
                
            put_r = httpx.put(url, headers=headers, json=payload)
            if put_r.status_code not in [200, 201]:
                logger.error(f"GitHub API push failed for intel: {put_r.text}")
        except Exception as e:
            logger.error(f"Failed to auto-push intel to GitHub API: {e}")

if "provider_intel_loaded" not in st.session_state:
    st.session_state["provider_intel_loaded"] = True
    pull_provider_intel()

import urllib.parse
def update_provider_intelligence_from_results(results):
    local_intel = load_provider_intel()
    dirty = False
    new_learnings_count = 0
    
    for r in results:
        fp = r.get("fingerprint", {})
        if not fp:
            continue
            
        base_url = r.get("base_url", "")
        try:
            parsed = urllib.parse.urlparse(base_url)
            domain = parsed.netloc
            if not domain:
                domain = base_url
        except Exception:
            domain = base_url
            
        existing = local_intel.get(domain, {})
        needs_update = False
        
        provider_name = existing.get("provider_name")
        if not provider_name or not provider_name.startswith("🎯"):
            provider_name = f"👤 Host: {domain}"
            
        merged_fp = dict(existing)
        merged_fp["provider_name"] = provider_name
        
        for k, v in fp.items():
            if v and str(v).lower() not in ["none", "unknown", ""]:
                if str(existing.get(k)) != str(v):
                    needs_update = True
                    merged_fp[k] = v
                    
        if needs_update or not existing:
            if not existing:
                merged_fp["first_seen"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            merged_fp["last_seen"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            
            # Simple identity hinting based on server value if it's bespoke
            srv = fp.get("server", "")
            msg = fp.get("metadata_message", "")
            
            identity_hint = None
            if srv and srv not in ["Unknown", "nginx", "Apache", "LiteSpeed", "cloudflare"]:
                identity_hint = srv
            elif msg and len(msg) > 5 and "Welcome" not in msg:
                # Some panels set the welcome message to their provider name
                identity_hint = msg[:30]
                
            if identity_hint and "🎯 Identified" not in merged_fp["provider_name"]:
                merged_fp["provider_name"] = f"🎯 Identified: {identity_hint}"
                    
            local_intel[domain] = merged_fp
            dirty = True
            new_learnings_count += 1
            
    if dirty:
        _, sha = pull_provider_intel() # Refresh against cloud before overwrite
        save_provider_intel(local_intel, sha=sha)
        
    return dirty, new_learnings_count

def mine_provider_branding_from_payloads(base_url, categories_data, streams_data):
    """
    Looks for recurring branding patterns in Tier 2 channels/categories, e.g. '### Strong 8K ###', 
    Telegram/Discord links, etc.
    """
    import re
    import urllib.parse
    
    try:
        parsed = urllib.parse.urlparse(base_url)
        domain = parsed.netloc if parsed.netloc else base_url
    except Exception:
        domain = base_url
        
    branding_names = {}
    
    # 1. Inspect telegram / discord / whatsapp / general URLs in names
    contact_pattern = re.compile(r'(t\.me/\w+|discord\.gg/\w+|wa\.me/\d+|https?://[^\s]+)', re.IGNORECASE)
    
    # 2. Inspect decorated dummy channels
    deco_pattern = re.compile(r'^[#=~\|\*]{2,}\s*(.+?)\s*[#=~\|\*]{2,}$')
    
    def analyze_item(name):
        if not name: return
        name = str(name).strip()
        
        # Contacts
        contacts = contact_pattern.findall(name)
        for c in contacts:
            brand = f"Link: {c}"
            branding_names[brand] = branding_names.get(brand, 0) + 15
            
        # decorated names like ### Provider ###
        match = deco_pattern.search(name)
        if match:
            brand = match.group(1).strip()
            # filter out common false positives
            if len(brand) > 3 and not bool(re.search(r'^[-_*~=#|]+$', brand)) and brand.lower() not in ['vip', 'vod', 'series', 'movies', 'live', 'channels', 'sport', 'sports', 'kids', 'news', 'catchup', 'all', 'xxx']:
                branding_names[brand] = branding_names.get(brand, 0) + 2
                
        # Known giants
        if "Strong 8K" in name or "Strong8K" in name:
            branding_names["Strong 8K"] = branding_names.get("Strong 8K", 0) + 20
        if "Trex" in name or "Trexiptv" in name.lower():
            branding_names["Trex IPTV"] = branding_names.get("Trex IPTV", 0) + 20
        if "Mega" in name and "IPTV" in name:
            branding_names["Mega IPTV"] = branding_names.get("Mega IPTV", 0) + 15
        if "Cobra" in name:
            branding_names["Cobra IPTV"] = branding_names.get("Cobra IPTV", 0) + 15
        if "Xtreme HD" in name or "XtremeHD" in name:
            branding_names["Xtreme HD"] = branding_names.get("Xtreme HD", 0) + 15
        if "Apollo" in name and "Group" in name:
            branding_names["Apollo Group TV"] = branding_names.get("Apollo Group TV", 0) + 15

    if isinstance(categories_data, list):
        for c in categories_data:
            analyze_item(c.get("category_name", ""))
            
    if isinstance(streams_data, list):
        for s in streams_data:
            analyze_item(s.get("name", ""))
            
    if branding_names:
        best_brand = max(branding_names, key=branding_names.get)
        if branding_names[best_brand] > 0:
            local_intel = load_provider_intel()
            existing = local_intel.get(domain, {})
            current_name = existing.get("provider_name", "")
            
            new_name = f"🎯 Identified: {best_brand}"
            
            # Allow upgrading identification if we found a better clue (higher weight >= 15)
            # Contact links are strong clues.
            if new_name != current_name and ("Link:" in new_name or "🎯 Identified" not in current_name or branding_names[best_brand] >= 15):
                existing["provider_name"] = new_name
                existing["last_seen"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                if "first_seen" not in existing:
                    existing["first_seen"] = existing["last_seen"]
                    
                local_intel[domain] = existing
                _, sha = pull_provider_intel()
                save_provider_intel(local_intel, sha=sha)
                return best_brand
    return None

# --- CUSTOM UI / UX THEMES ---
if "app_theme" not in st.session_state:
    st.session_state.app_theme = "Midnight Purple (Focus)"

def get_theme_css(theme_name):
    # Default variables (Midnight Purple)
    bg_col = "#0C0714"
    text_col = "#E9E3F4"
    text_muted = "#A79BBF"
    primary1 = "#A855F7"
    primary2 = "#7E22CE"
    panel_bg = "rgba(43, 20, 60, 0.4)"
    panel_hover = "rgba(43, 20, 60, 0.6)"
    code_bg = "rgba(0,0,0,0.3)"
    code_text = "#C084FC"

    if theme_name == "Ocean Blue (Glass)":
        bg_col = "#050F17"
        text_col = "#E0F2FE"
        text_muted = "#7DD3FC"
        primary1 = "#0EA5E9"
        primary2 = "#0284C7"
        panel_bg = "rgba(14, 30, 45, 0.5)"
        panel_hover = "rgba(14, 30, 45, 0.7)"
        code_text = "#38BDF8"
    elif theme_name == "Crimson Red (Dark)":
        bg_col = "#11070A"
        text_col = "#FCE7F3"
        text_muted = "#F472B6"
        primary1 = "#E11D48"
        primary2 = "#BE123C"
        panel_bg = "rgba(45, 14, 25, 0.5)"
        panel_hover = "rgba(45, 14, 25, 0.7)"
        code_text = "#FDA4AF"
    elif theme_name == "Clean Light Mode":
        bg_col = "#F1F5F9"
        text_col = "#0F172A"
        text_muted = "#475569"
        primary1 = "#3B82F6"
        primary2 = "#2563EB"
        panel_bg = "rgba(255, 255, 255, 0.8)"
        panel_hover = "rgba(255, 255, 255, 1.0)"
        code_bg = "rgba(241,245,249,0.8)"
        code_text = "#0369A1"

    return f"""
<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap');

/* Main Typography & Base */
html, body, [class*="css"] {{
    font-family: 'Inter', sans-serif !important;
    background-color: {bg_col} !important;
    color: {text_col} !important;
}}

/* Main container sleek spacing */
.main .block-container {{
    padding-top: 1.5rem !important;
    padding-bottom: 4rem !important;
    max-width: 1200px;
}}
.stApp {{
    background-color: {bg_col} !important;
}}

/* Cool gradient title styling */
.hero-title {{
    font-family: 'Inter', sans-serif;
    font-weight: 800;
    font-size: 3rem;
    letter-spacing: -0.03em;
    background: linear-gradient(135deg, {primary1} 0%, {primary2} 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    margin-bottom: 0px;
    padding-bottom: 0px;
}}
.hero-subtitle {{
    font-family: 'Inter', sans-serif;
    color: {text_muted};
    font-size: 1.1rem;
    font-weight: 400;
    margin-top: 5px;
    margin-bottom: 25px;
}}

/* Tabs Redesign */
div[data-testid="stTabs"] button {{
    font-size: 1.05rem;
    font-weight: 600;
    color: {text_muted};
    padding: 14px 20px;
    background: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    transition: all 0.2s ease;
}}
div[data-testid="stTabs"] button[data-baseweb="tab"][aria-selected="true"] {{
    color: {primary1};
    border-bottom: 2px solid {primary1} !important;
    background-color: rgba(128, 128, 128, 0.05);
    border-radius: 6px 6px 0 0;
}}
div[data-testid="stTabs"] button:hover {{
    color: {text_col};
}}

/* Sleek Buttons */
div.stButton > button {{
    border-radius: 8px;
    font-weight: 600;
    padding: 0.6rem 1.2rem;
    border: 1px solid rgba(128, 128, 128, 0.2);
    background: {panel_bg};
    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    color: {text_col};
}}
div.stButton > button:hover {{
    border-color: {primary1};
    color: {primary1};
    transform: translateY(-2px);
    box-shadow: 0 6px 12px rgba(0,0,0,0.1);
}}
div.stButton > button[kind="primary"] {{
    background: linear-gradient(135deg, {primary1} 0%, {primary2} 100%);
    border: none;
    color: white;
    box-shadow: 0 4px 12px rgba(0,0,0,0.2);
}}
div.stButton > button[kind="primary"]:hover {{
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(0,0,0,0.3);
    color: white;
}}

/* Glassmorphism DataFrames */
div[data-testid="stDataFrame"] {{
    border: 1px solid rgba(128,128,128,0.15) !important;
    border-radius: 12px !important;
    overflow: hidden;
    background: {panel_bg};
    backdrop-filter: blur(10px);
    box-shadow: 0 8px 32px rgba(0,0,0,0.1);
}}

/* Beautiful Expanders */
.stExpander {{
    border: 1px solid rgba(128,128,128,0.15) !important;
    border-radius: 10px !important;
    background: {panel_bg} !important;
    backdrop-filter: blur(5px);
    box-shadow: 0 4px 16px rgba(0,0,0,0.05);
    transition: all 0.3s ease;
}}
.stExpander:hover {{
    border-color: {primary1} !important;
    background: {panel_hover} !important;
}}
.stExpander summary {{
    font-weight: 600;
    color: {text_col};
    padding: 12px;
}}

/* Code blocks & Texts */
code {{
    font-family: 'JetBrains Mono', monospace !important;
    background-color: {code_bg} !important;
    border: 1px solid rgba(128,128,128,0.1);
    border-radius: 6px;
    padding: 2px 6px;
    color: {code_text} !important;
}}
.stCodeBlock {{
    border-radius: 10px !important;
    border: 1px solid rgba(128,128,128,0.2);
    background-color: {bg_col} !important;
}}

/* Metrics */
div[data-testid="stMetricValue"] {{
    font-weight: 800;
    font-size: 2.2rem;
    letter-spacing: -0.02em;
    color: {text_col};
}}
div[data-testid="stMetricLabel"] {{
    color: {text_muted};
    font-weight: 500;
    font-size: 0.9rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}}

/* Input Fields */
.stTextArea textarea, .stTextInput input {{
    background-color: {panel_bg} !important;
    border: 1px solid rgba(128,128,128,0.2) !important;
    border-radius: 8px !important;
    color: {text_col} !important;
    font-family: 'JetBrains Mono', monospace;
    transition: all 0.2s ease;
}}
.stTextArea textarea:focus, .stTextInput input:focus {{
    border-color: {primary1} !important;
    box-shadow: 0 0 0 1px {primary1} !important;
}}
.stRadio label, .stCheckbox label {{
    color: {text_col} !important;
}}

div[data-testid="stVerticalBlock"] > div:first-child {{
    padding-top: 0 !important;
}}
</style>
"""

# Inject Dynamic CSS
st.markdown(get_theme_css(st.session_state.app_theme), unsafe_allow_html=True)

# --- SIDEBAR: DASHBOARD CONFIGURATION ---
with st.sidebar:
    st.markdown("### ⚙️ Engine Settings")
    
    st.selectbox(
        "Appearance Theme", 
        ["Midnight Purple (Focus)", "Ocean Blue (Glass)", "Crimson Red (Dark)", "Clean Light Mode"],
        key="app_theme",
        help="Select the UI aesthetic. Note: Will trigger a quick refresh."
    )
    st.write("---")

# --- HERO HEADER ---
col_head, col_action = st.columns([4, 1])
with col_head:
    st.markdown('<div class="hero-title">IPTV Analytics Dashboard</div>', unsafe_allow_html=True)
    st.markdown('<div class="hero-subtitle">High-performance manifest discovery, validation, and analytics engine</div>', unsafe_allow_html=True)

# --- SECURE ACCESS CHECK ---
def check_password():
    """Returns `True` if the user entered the correct password, or if no password is configured."""
    access_password = get_secret_safe("ACCESS_PASSWORD")
    if not access_password:
        return True

    def password_entered():
        """Checks whether a password entered by the user is correct."""
        if st.session_state["password"] == access_password:
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

    # Combo Pattern (e.g. host:port \s+ user:pass OR host:port:user:pass OR host:port \s+ user OR host:port \s+ :pass)
    pattern_combo = r'((?:https?://)?[a-zA-Z0-9\.-]+(?::\d+)?)(?:\s+|:)([^:\s]*:\S+|[^:\s]+)'
    for match in re.findall(pattern_combo, text_block):
        base_url = match[0]
        if not base_url.startswith("http"):
            base_url = "http://" + base_url
            
        cred = match[1]
        if ":" in cred:
            parts = cred.split(":", 1)
            user = parts[0]
            password = parts[1]
        else:
            user = cred
            password = ""
            
        # Skip if it's actually a MAC address disguised as user:pass
        if re.match(r'^[0-9a-fA-F]{2}$', user) and re.match(r'^(?:[0-9a-fA-F]{2}:){4}[0-9a-fA-F]{2}$', password):
            continue
            
        # Skip if cred is just a common keyword like 'MAC:' or 'Active' etc.
        if cred.lower() in ["mac", "active", "expired", "http", "https"]:
            continue
            
        if not any(a["base_url"] == base_url and a["username"] == user for a in extracted):
            extracted.append({"type": "Xtream", "base_url": base_url, "username": user, "password": password})

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
    """Tier 1: High-speed handshake verification workflow with Provider Intelligence Extraction."""
    
    def extract_fingerprint(res=None, s_info=None, u_info=None):
        s_info = s_info or {}
        u_info = u_info or {}
        fp = {
            "server": "Unknown",
            "x_powered_by": "Unknown",
            "cors": "Unknown",
            "cloudflare": "No",
            "timezone": s_info.get("timezone", "Unknown"),
            "metadata_message": u_info.get("message", ""),
            "server_protocol": s_info.get("server_protocol", "Unknown"),
            "server_version": str(s_info.get("version", "Unknown")),
            "https_port": str(s_info.get("https_port", "Unknown")),
            "rtmp_port": str(s_info.get("rtmp_port", "Unknown")),
            "allowed_formats": str(u_info.get("allowed_output_formats", "Unknown"))
        }
        if res and hasattr(res, 'headers'):
            fp["server"] = res.headers.get("Server", "Unknown")
            fp["x_powered_by"] = res.headers.get("X-Powered-By", "Unknown")
            fp["cors"] = res.headers.get("Access-Control-Allow-Origin", "Unknown")
            fp["cloudflare"] = "Yes" if "CF-RAY" in res.headers else "No"
            if fp["timezone"] == "Unknown":
                fp["timezone"] = res.headers.get("Date", "Unknown")
        return fp

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
                return {**account, "Status": "🛡️ Cloud Blocked (HTTP 403)", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res)}
            elif res.status_code == 521:
                return {**account, "Status": "🔴 Offline (Server Dead)", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res)}
            elif res.status_code != 200:
                return {**account, "Status": "🛡️ Firewalled / Blocked", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res)}
                
            try:
                data = res.json()
                if "error" in data or ("js" in data and isinstance(data["js"], dict) and "error" in data["js"]):
                    return {**account, "Status": "🟡 Expired / Invalid MAC", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res)}
                
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
                    "VODs": "N/A",
                    "Server Timezone": res.headers.get("Server", "Unknown"),
                    "Server Time": "Unknown",
                    "fingerprint": extract_fingerprint(res)
                }
            except Exception:
                # Output might not be standard JSON if blocked by WAF challenge
                return {**account, "Status": "🛡️ Firewalled / Blocked / Captcha", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res)}
                
        except Exception as e:
            logger.error(f"Failed to connect to Stalker host {account['base_url']}: {str(e)}")
            return {**account, "Status": "🛡️ Firewalled / Blocked", "Expires": "N/A", "Days Left": 0, "Max Conn": "N/A", "Active Conn": "N/A", "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint()}

    # Default Xtream Handshake Workflow
    target_url = f"{account['base_url']}/player_api.php?username={account['username']}&password={account['password']}"
    logger.info(f"Evaluating connection status for: {account['base_url']} (User: {account['username']})")
    try:
        res = await client.get(target_url, timeout=6.0)
        
        # Check HTTP Status Code first to handle firewall/cloud blockades explicitly
        if res.status_code == 403:
            logger.warning(f"Cloud Blocked (HTTP 403) for: {account['base_url']}")
            return {**account, "Status": "🛡️ Cloud Blocked (HTTP 403)", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res)}
        elif res.status_code == 521:
            logger.warning(f"Server dead (HTTP 521) for: {account['base_url']}")
            return {**account, "Status": "🔴 Offline (Server Dead)", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res)}
        elif res.status_code != 200:
            logger.warning(f"Unexpected status code {res.status_code} for: {account['base_url']}")
            return {**account, "Status": "🛡️ Firewalled / Blocked", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res)}
        
        # Catch plain authorization rejections
        if "Unauthorized" in res.text:
            logger.warning(f"Connection rejected (Unauthorized response body) for: {account['base_url']}")
            return {**account, "Status": "🟡 Expired / Invalid", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res)}
            
        data = res.json()
        user_info = data.get("user_info", {})
        server_info = data.get("server_info", {})
        
        if user_info.get("auth") == 0 or user_info.get("status") == "Expired":
            logger.warning(f"Authentication failed or account expired for: {account['base_url']}")
            return {**account, "Status": "🟡 Expired / Invalid", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint(res, server_info, user_info)}
        
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
            "VODs": "N/A",
            "Server Timezone": server_info.get("timezone", "Unknown"),
            "Server Time": server_info.get("time_now", "Unknown"),
            "fingerprint": extract_fingerprint(res, server_info, user_info)
        }
        
    except Exception as e:
        logger.error(f"Failed to connect to host {account['base_url']}: {str(e)}")
        return {**account, "Status": "🛡️ Firewalled / Blocked", "Expires": "N/A", "Days Left": 0, "Max Conn": 0, "Active Conn": 0, "Channels": "N/A", "VODs": "N/A", "Server Timezone": "N/A", "Server Time": "N/A", "fingerprint": extract_fingerprint()}

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

with st.sidebar:
    st.markdown("### 🛡️ Network Shield")
    if "UNKNOWN" in current_ip.upper() or current_ip.startswith("DISCONNECTED"):
        st.error(f"🔴 **SHIELD INACTIVE**\n\nIP: {current_ip}")
    else:
        st.success(f"🟢 **NETWORK ACTIVE**\n\n**IP:** {current_ip}\n\n**ISP:** {current_isp}")
        if is_cloud:
            st.warning("⚠️ **Cloud Node Detected**\nMany IPTV targets block cloud IP ranges.")

# Cloud warning banner to assist users deploying on public platforms (kept in main for visibility if cloud)
if is_cloud:
    st.info(
        f"⚠️ **Public Cloud/Hosting Environment Detected:** You are running on **{current_isp}** ({current_org}). "
        f"If connections fail with **'HTTP 403'** or **'Blocked'**, this hosting network is likely banned by the target server."
    )


def render_xtream_details(row, key_idx, show_commit_button=True):
    col_title, col_action = st.columns([4, 1])
    with col_title:
        if "Active" in row["Status"]:
            st.markdown(f"### 🔍 Deep-Dive Discovery: `{row['base_url']}`")
    with col_action:
        if show_commit_button:
            if st.button("💾 Commit Line", key=f"c_x_{key_idx}", use_container_width=True):
                commit_record(row.to_dict() if hasattr(row, "to_dict") else row, "Xtream")
                st.success("Saved!")
    
    if "Active" in row["Status"]:
        st.caption("Use the copy icon on the top right of the code blocks to quickly copy to your clipboard.")
        
        st.markdown("**🔐 Discrete Login Credentials**")
        st.info("💡 **Pro Tip:** If the M3U link doesn't work in your player but discrete credentials do, your host likely disabled standard M3U `/get.php` downloads to force users onto API-based player apps.")
        col_h, col_u, col_p = st.columns(3)
        with col_h:
            st.markdown("**🌐 Server Host**")
            st.code(row['base_url'], language="text")
        with col_u:
            st.markdown("**👤 Username**")
            st.code(row['username'], language="text")
        with col_p:
            st.markdown("**🔑 Password**")
            st.code(row['password'], language="text")
        
        st.markdown("**🔗 M3U Playlist URL**")
        m3u_url = f"{row['base_url']}/get.php?username={row['username']}&password={row['password']}&type=m3u_plus&output=ts"
        st.code(m3u_url, language="text")
        
        st.write("---")
        st.write("📡 **Query Target Asset Classifications**")
        
        tier2_key = f"t2_{row['base_url']}_{row['username']}"
        
        if tier2_key not in st.session_state:
            if st.button("Fetch Live Stream Catalogs", key=f"f_b_{key_idx}", type="primary", use_container_width=True):
                with st.spinner("Fetching stream catalogs from IPTV host. Please wait..."):
                    async def fetch_tier2_data(r):
                        return await asyncio.gather(
                            fetch_lazy_details(r['base_url'], r['username'], r['password'], "get_live_categories"),
                            fetch_lazy_details(r['base_url'], r['username'], r['password'], "get_live_streams"),
                            return_exceptions=True
                        )
                    
                    lazy_results = asyncio.run(fetch_tier2_data(row))
                    st.session_state[tier2_key] = lazy_results
                    
                    if not isinstance(lazy_results[0], Exception) and not isinstance(lazy_results[1], Exception):
                        brand_intel = mine_provider_branding_from_payloads(row['base_url'], lazy_results[0], lazy_results[1])
                        if brand_intel:
                            st.session_state["show_brand_toast"] = f"🎯 Provider Branding Discovered in payloads: {brand_intel}"

                    st.rerun()
        else:
            if "show_brand_toast" in st.session_state:
                st.toast(st.session_state["show_brand_toast"], icon="🧠")
                del st.session_state["show_brand_toast"]
                
            col_ref1, col_ref2 = st.columns([4, 1])
            with col_ref2:
                if st.button("🔄 Refresh", key=f"r_b_{key_idx}", use_container_width=True):
                    del st.session_state[tier2_key]
                    st.rerun()

            results = st.session_state[tier2_key]
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
                        key=f"s_x_{key_idx}"
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
                                use_container_width=True, hide_index=True,
                                key=f"d_c_s_{key_idx}"
                            )
                        else:
                            st.info("No channels found in this group.")
            else:
                st.error("Payload context restricted or limited by provider container setup. Status verified, but deep mining disabled.")
    else:
        st.warning("⚠️ Deep-Dive Discovery is only available for Active connections. The selected node is offline or blocked.")

def render_stalker_details(row, key_idx, show_commit_button=True):
    col_title, col_action = st.columns([4, 1])
    with col_title:
        if "Active" in row["Status"]:
            st.markdown(f"### 🔍 Detailed Credentials: `{row['base_url']}`")
    with col_action:
        if show_commit_button:
            if st.button("💾 Commit Line", key=f"c_s_{key_idx}", use_container_width=True):
                commit_record(row.to_dict() if hasattr(row, "to_dict") else row, "Stalker")
                st.success("Saved!")

    if "Active" in row["Status"]:
        st.caption("Use the copy icon on the top right of the code blocks to quickly copy to your clipboard.")
        
        st.markdown("**🔐 Stalker Login Configuration**")
        col_sh, col_sm = st.columns(2)
        with col_sh:
            st.markdown("**🌐 Portal URL**")
            st.code(row['base_url'], language="text")
        with col_sm:
            st.markdown("**🏷️ MAC Address**")
            st.code(row['mac'], language="text")
        st.info("⚠️ **Deep-Dive Discovery is constrained for Stalker Portals.**\n\nStalker Portals (Ministra) use dynamic, MAC-authenticated MAC schemas rather than standard Xtream flat lists. Fetching massive stream categories without full device emulation can trigger security bans on the host. Status verification is complete, but deep channel mining is restricted.")
    else:
        st.warning("⚠️ Valid credentials are only available for Active Stalker portals. This connection was verified as degraded or offline.")

xt_count = 0
st_count = 0
if st.session_state.get("playlist_results"):
    xt_count = len([x for x in st.session_state["playlist_results"] if x.get("type", "Xtream") == "Xtream"])
    st_count = len([x for x in st.session_state["playlist_results"] if x.get("type") == "Stalker"])

tab_tools, tab_scanner, tab_xtream, tab_stalker, tab_committed = st.tabs([
    "🛠️ Base64 Decoder",
    "📡 Multi-Payload Scanner", 
    f"📺 Xtream Codes ({xt_count})", 
    f"🛸 Stalker Portals ({st_count})",
    "💾 Committed Data"
])

with tab_tools:
    st.markdown("### 🛠️ Data Translation & Base64 Decoder")
    st.caption("Extract hidden URLs or encapsulate raw data payloads before processing.")
    
    if "b64_input" not in st.session_state:
        st.session_state["b64_input"] = ""
        
    def clear_b64_input():
        st.session_state["b64_input"] = ""

    b64_action = st.radio("Select Operation:", ["Decode (Base64 -> Text)", "Encode (Text -> Base64)"], horizontal=True)
    b64_input = st.text_area("Input Payload:", height=180, key="b64_input", label_visibility="collapsed")
    
    col_tools_1, col_tools_2, col_tools_3, _ = st.columns([2, 1, 2, 2])
    with col_tools_1:
        trans_pressed = st.button("🔄 Execute Translation", type="primary", use_container_width=True)
    with col_tools_2:
        st.button("🧹 Clear", on_click=clear_b64_input, key="clear_b64_btn", use_container_width=True)
    with col_tools_3:
        if st.button("Next ➡️", key="next_b64", use_container_width=True):
            components.html("<script>window.parent.document.querySelectorAll('button[data-baseweb=\"tab\"]')[1].click();</script>", height=0)

    if trans_pressed:
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
                        st.success("✅ Successfully decoded. (Use the copy button in the top right of the block)")
                    else:
                        raise ValueError("No valid Base64 encoded strings found in the payload.")
                else:
                    result = base64.b64encode(b64_input.encode('utf-8')).decode('utf-8')
                    st.success("✅ Successfully encoded. (Use the copy button in the top right of the block)")
                    
                st.code(result, language="text")
                
                # If it's a URL, give a link button
                if result.strip().startswith("http://") or result.strip().startswith("https://"):
                    st.link_button("🌐 Launch Converted URL in New Tab", result.strip())
                    
            except Exception as e:
                st.error(f"Failed to process payload: {str(e)}")

with tab_scanner:
    st.markdown("### 📡 Payload Ingestion Engine")
    st.caption("Drop messy text, diagnostic blocks, or standard M3U configurations. The engine will auto-detect Xtream Codes and Stalker Portal formats.")

    if "raw_input" not in st.session_state:
        st.session_state["raw_input"] = ""

    def clear_input():
        st.session_state["raw_input"] = ""

    pasted_data = st.text_area("Ingestion Area:", height=200, key="raw_input", label_visibility="collapsed")

    # Initialize session state for analysis results
    if "playlist_results" not in st.session_state:
        st.session_state["playlist_results"] = None
    
    col_scan_1, col_scan_2, col_scan_3, _ = st.columns([2, 1, 2, 2])
    with col_scan_1:
        analyze_pressed = st.button("🚀 Analyze Discovered Nodes", type="primary", disabled=("UNKNOWN" in current_ip.upper() or current_ip.startswith("DISCONNECTED")), use_container_width=True)
    with col_scan_2:
        st.button("🧹 Clear", on_click=clear_input, key="clear_btn", use_container_width=True)
    with col_scan_3:
        if st.button("Next ➡️", key="next_scan", use_container_width=True):
            components.html("<script>window.parent.document.querySelectorAll('button[data-baseweb=\"tab\"]')[2].click();</script>", height=0)

    st.write("---")


    if analyze_pressed:
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
            
            # Fire intelligence gathering
            dirty, learnings_count = update_provider_intelligence_from_results(results)
            if dirty:
                st.session_state["show_intel_toast"] = True
                
            st.rerun()

    if "show_intel_toast" in st.session_state and st.session_state["show_intel_toast"]:
        st.toast("🧠 Dirty state of provider knowledge detected! Core intelligence updated.", icon="🔄")
        st.toast("✅ Success! New provider learnings committed to cloud.", icon="✅")
        st.session_state["show_intel_toast"] = False

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
    import urllib.parse
    local_intel = load_provider_intel()
    for acc in st.session_state["playlist_results"]:
        base_url = acc.get("base_url", "")
        try:
            parsed = urllib.parse.urlparse(base_url)
            domain = parsed.netloc
            if not domain:
                domain = base_url
        except Exception:
            domain = base_url
            
        existing = local_intel.get(domain, {})
        acc["Provider"] = existing.get("provider_name", "Unknown Provider")

    df = pd.DataFrame(st.session_state["playlist_results"])
    
    xtream_df = df[df["type"] == "Xtream"]
    stalker_df = df[df["type"] == "Stalker"]
    
    with tab_xtream:
        col_x_head, col_x_action = st.columns([3, 1])
        with col_x_head:
            st.markdown("### 📺 Xtream Codes Manifest")
        
        if xtream_df.empty:
            st.info("No Xtream Codes accounts discovered in the recent scan.")
        else:
            col_filter, col_counts = st.columns([2, 1])
            with col_filter:
                show_only_active_x = st.toggle("Show only Active Connections", value=False, key="xtream_active")
            
            display_xtream = xtream_df.copy()
            if show_only_active_x:
                display_xtream = display_xtream[display_xtream["Status"].str.contains("Active|🟢", case=False, na=False)]
            
            # Add M3U Copy column
            display_xtream["M3U Link"] = display_xtream.apply(
                lambda row: f"{row['base_url']}/get.php?username={row['username']}&password={row['password']}&type=m3u_plus&output=ts",
                axis=1
            )
            
            # Sort by Channels desc
            display_xtream["_sort_ch"] = pd.to_numeric(display_xtream["Channels"], errors='coerce').fillna(-1)
            display_xtream = display_xtream.sort_values(by="_sort_ch", ascending=False).drop(columns=["_sort_ch"])

            # Reorder columns: Move Days Left after VODs
            cols = list(display_xtream.columns)
            if "Days Left" in cols and "VODs" in cols:
                cols.remove("Days Left")
                vod_idx = cols.index("VODs")
                cols.insert(vod_idx + 1, "Days Left")
                display_xtream = display_xtream[cols]

            st.caption(f"Showing **{len(display_xtream)}** records.")
            event_x = st.dataframe(
                display_xtream.drop(columns=["M3U Link", "fingerprint"], errors='ignore'),
                use_container_width=True,
                selection_mode="single-row",
                on_select="rerun",
                key="xtream_table",
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
            if selected_x_idx is not None:
                row = display_xtream.iloc[selected_x_idx]
                render_xtream_details(row, selected_x_idx, show_commit_button=True)
            else:
                st.info("👆 Select a row in the table above to reveal Master-Detail deep insight panel, copy easy credentials, and browse channels!")
            
            st.write("---")
            col_x_next1, col_x_next2 = st.columns([8, 2])
            with col_x_next2:
                if st.button("Next ➡️", key="next_xtream", use_container_width=True):
                    components.html("<script>window.parent.document.querySelectorAll('button[data-baseweb=\"tab\"]')[3].click();</script>", height=0)

    with tab_stalker:
        col_s_head, col_s_action = st.columns([3, 1])
        with col_s_head:
            st.markdown("### 🛸 Stalker Portals Manifest")
            
        if stalker_df.empty:
            st.info("No Stalker accounts discovered in the recent scan.")
        else:
            show_only_active_s = st.toggle("Show only Active Connections", value=False, key="stalker_active")
            
            display_stalker = stalker_df.copy()
            if show_only_active_s:
                display_stalker = display_stalker[display_stalker["Status"].str.contains("Active|🟢", case=False, na=False)]
            
            # Sort by Channels desc
            display_stalker["_sort_ch"] = pd.to_numeric(display_stalker["Channels"], errors='coerce').fillna(-1)
            display_stalker = display_stalker.sort_values(by="_sort_ch", ascending=False).drop(columns=["_sort_ch"])

            # Reorder columns: Move Days Left after VODs
            cols_s = list(display_stalker.columns)
            if "Days Left" in cols_s and "VODs" in cols_s:
                cols_s.remove("Days Left")
                vod_idx = cols_s.index("VODs")
                cols_s.insert(vod_idx + 1, "Days Left")
                display_stalker = display_stalker[cols_s]

            st.caption(f"Showing **{len(display_stalker)}** records.")
            event_s = st.dataframe(
                display_stalker.drop(columns=["fingerprint"], errors='ignore'),
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
            if selected_s_idx is not None:
                row = display_stalker.iloc[selected_s_idx]
                render_stalker_details(row, selected_s_idx, show_commit_button=True)
            else:
                st.info("👆 Select a row in the table above to open the easy-copy credentials panel for Stalker portals!")
            
            st.write("---")
            col_s_next1, col_s_next2 = st.columns([8, 2])
            with col_s_next2:
                if st.button("Next ➡️", key="next_stalker", use_container_width=True):
                    components.html("<script>window.parent.document.querySelectorAll('button[data-baseweb=\"tab\"]')[4].click();</script>", height=0)

with tab_committed:
    st.markdown("### 💾 Committed Data Repository")
    col_c_text, col_c_btn = st.columns([4, 1])
    with col_c_text:
        st.caption("Locally saved verification records pushing directly to remote GitHub repository.")
    with col_c_btn:
        if st.button("🔄 Reload from Cloud", use_container_width=True):
            with st.spinner("Downloading and merging cloud updates..."):
                # Simulating a progress bar to show some visual execution, as sync can be fast and missable
                progress_bar = st.progress(0)
                for percent in range(100):
                    import time
                    time.sleep(0.01)
                    progress_bar.progress(percent + 1)
                
                pull_committed_data(force=True)
                
            st.toast("✅ Cloud records fetched and merged successfully!", icon="🎉")
            st.rerun()
    
    committed_records = load_committed_data()
    
    if not committed_records:
        st.info("No records committed yet. Analyze a payload and select a row in Xtream or Stalker tabs, then hit 'Commit Line' to save it here.")
    else:
        import urllib.parse
        local_intel = load_provider_intel()
        for c_rec in committed_records:
            base_url = c_rec.get("base_url", "")
            try:
                parsed = urllib.parse.urlparse(base_url)
                domain = parsed.netloc if parsed.netloc else base_url
            except:
                domain = base_url
            existing = local_intel.get(domain, {})
            c_rec["Provider"] = existing.get("provider_name", "Unknown Provider")

        comm_df = pd.DataFrame(committed_records)
        comm_df = comm_df.drop(columns=["fingerprint"], errors="ignore")
        
        # Reorder columns slightly for presentation
        # Move mostly needed items to left
        cols = comm_df.columns.tolist()
        if "Date Selected" in cols:
            cols.insert(0, cols.pop(cols.index("Date Selected")))
            # Default sort by Date Selected descending
            comm_df = comm_df.sort_values(by="Date Selected", ascending=False)
        
        comm_df = comm_df[cols]
        
        st.caption(f"Showing **{len(comm_df)}** committed records.")
        event_c = st.dataframe(
            comm_df,
            use_container_width=True,
            selection_mode="single-row",
            on_select="rerun",
            key="committed_table",
            hide_index=True,
            column_config={
                "Notes": st.column_config.TextColumn(
                    "Notes",
                    width=500,
                )
            }
        )
        
        selected_c_idx = None
        if event_c and hasattr(event_c, "selection") and event_c.selection.rows:
            selected_c_idx = event_c.selection.rows[0]
        elif isinstance(event_c, dict) and "selection" in event_c and "rows" in event_c["selection"] and event_c["selection"]["rows"]:
            selected_c_idx = event_c["selection"]["rows"][0]
            
        if selected_c_idx is not None:
            st.write("---")
            row = comm_df.iloc[selected_c_idx]
            st.markdown(f"### ✏️ Edit Record: `{row.get('base_url')}`")
            
            notes_val = st.text_area("📝 Free Form Notes", value=row.get("Notes", ""), key="notes_input", height=100)
            
            col_action1, col_action2, _ = st.columns([2, 2, 4])
            with col_action1:
                if st.button("💾 Save Notes", key="save_notes_btn", use_container_width=True):
                    tgt_base_url = row.get("base_url")
                    tgt_user = row.get("username", "")
                    tgt_mac = row.get("mac", "")
                    
                    update_committed_notes(tgt_base_url, tgt_user, tgt_mac, notes_val)
                    st.success("Notes saved successfully!")
                    st.rerun()
            with col_action2:
                if st.button("🗑️ Delete Record", type="primary", key="del_commit_btn", use_container_width=True):
                    tgt_base_url = row.get("base_url")
                    tgt_user = row.get("username", "")
                    tgt_mac = row.get("mac", "")
                    
                    delete_committed_record(tgt_base_url, tgt_user, tgt_mac)
                    st.success("Record deleted successfully!")
                    st.rerun()
            
            st.write("---")
            source_type = row.get("Source", "")
            if source_type == "Xtream" or (not source_type and not row.get("mac", "")):
                render_xtream_details(row, f"c_view_{selected_c_idx}", show_commit_button=False)
            else:
                render_stalker_details(row, f"c_view_{selected_c_idx}", show_commit_button=False)

