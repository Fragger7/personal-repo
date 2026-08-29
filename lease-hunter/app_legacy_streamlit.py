# app.py (Universal Lease Hacker Engine)
import streamlit as st
import pandas as pd
import os
from dotenv import load_dotenv
from google import genai
from google.genai import types
from pydantic import BaseModel, Field

# Load environment variables for local development
load_dotenv()

st.set_page_config(page_title="Universal Lease Engine", layout="wide", page_icon="🏎️", initial_sidebar_state="expanded")

# --- CASCADE VEHICLE DATABASE OF LEASEABLE NORTH AMERICAN CARS ---
VEHICLE_DATABASE = {
    "BMW": {
        "i4 Coupe/Sedan": ["eDrive35", "eDrive40", "xDrive40", "M50"],
        "iX SUV": ["xDrive50", "M60"],
        "3 Series Active": ["330i", "330e Hybrid", "M340i Performance"],
        "5 Series Luxury": ["530i Premium", "i5 eDrive40 Electric", "i5 M60 Sport"],
        "X5 Active SUV": ["sDrive40i Classic", "xDrive40i AWD", "xDrive50e Hybrid"]
    },
    "Audi": {
        "Q4 e-tron Electric": ["Premium 40", "Premium Plus 40 S-Line", "Premium Plus 50 Quattro"],
        "Q8 e-tron Electric SUV": ["Premium S AWD", "Premium Plus S S-Line", "Prestige luxury"],
        "e-tron GT Electric": ["Premium Plus e-Quattro", "Prestige luxury S", "RS Performance GT"],
        "Q5 Luxury Crossover": ["40 TFSI Premium", "45 TFSI S Line", "55 TFSI e plug-in hybrid"]
    },
    "Mercedes-Benz": {
        "EQE Sedan/SUV": ["EQE 350+ Base", "EQE 350 4MATIC AWD", "EQE 500 Luxury"],
        "EQS Sedan/SUV": ["EQS 450+ Luxury", "EQS 580 Executive 4MATIC"],
        "C-Class Executive": ["C300 Premium S", "C300 4MATIC Sport"],
        "GLC SUV Crossover": ["GLC300 Premium", "GLC300 4MATIC Sport Edition"]
    },
    "Tesla": {
        "Model 3 Electric": ["Standard Range RWD", "Long Range AWD", "Performance Track AWD"],
        "Model Y SUV": ["Rear-Wheel Drive Base", "Long Range AWD Utility", "Performance Track Sport"],
        "Model S Sport Sedan": ["Dual Motor AWD Long Range", "Plaid Tri-Motor Performance"],
        "Model X Sport Utility": ["Dual Motor AWD", "Plaid Tri-Motor Ultimate"]
    },
    "Hyundai": {
        "IONIQ 5 Crossover": ["SE Standard", "SEL Executive", "Limited Ultimate Edition", "D100 Platinum"],
        "IONIQ 6 Streamliner": ["SE Long Range", "SEL Mid Range", "Limited Sport"],
        "Tucson Compact": ["SE Base", "SEL Comfort", "N Line Sport", "Limited Premium", "Plug-In Hybrid Sport"],
        "Santa Fe Full SUV": ["SE Base", "SEL Adventure", "XRT Rugged", "Limited Luxury", "Calligraphy Ultimate"]
    },
    "Kia": {
        "EV6 crossover": ["Light Long Range", "Wind AWD", "GT-Line Sport", "GT Track Performance"],
        "EV9 Fullsize SUV": ["Light Long Range RWD", "Wind Utility AWD", "Land AWD Premium", "GT-Line Ultimate AWD"],
        "Sportage Crossover": ["LX Standard", "EX Comfort", "SX Sport Utility", "Sportage Plug-In Hybrid"],
        "Telluride Utility": ["LX Base", "S Premium", "EX Comfort Luxury", "SX Active Ride", "SX Prestige Nightfall"]
    },
    "Chevrolet": {
        "Blazer EV Sport": ["LT Standard AWD", "RS Sport Edition AWD", "SS High Output Track"],
        "Equinox EV Compact": ["2LT Standard RWD", "3LT Luxury", "2RS Active", "3RS Ultimate Performance"],
        "Silverado EV Truck": ["4WT Work Truck AWD", "3WT Practical AWD", "RST First Edition Dynamic"]
    },
    "Ford": {
        "Mustang Mach-E SUV": ["Select Entry AWD", "Premium Extended Range", "GT Performance Edition", "Rally Off-Road Sport"],
        "F-150 Lightning Truck": ["Pro Commercial AWD", "XLT Active Ride", "Flash Utility Truck", "Lariat Premium AWD", "Platinum Ultimate"]
    },
    "Lexus": {
        "RZ Electric Crossover": ["RZ 300e Premium RWD", "RZ 450e Premium AWD", "RZ 450e Luxury AWD"],
        "RX Luxury Performance": ["RX 350 Premium F-Sport", "RX 350h Fuel-Efficient AWD", "RX 500h F Sport Performance"],
        "NX Compact Premium": ["NX 250 Base FWD", "NX 350h Premium Hybrid", "NX 450h+ Plug-In Ultimate"]
    },
    "Toyota": {
        "bZ4X Electric": ["XLE Standard AWD", "Limited High Grade Utility"],
        "RAV4 Prime Sport": ["SE Mid Grade AWD", "XSE Sport Premium PHEV"],
        "Prius Prime Hatch": ["SE Efficient PHEV", "XSE Sport PHEV", "XSE Premium Ultimate"]
    },
    "Custom / Other Brand": {
        "Custom Model": ["Custom Trim"]
    }
}

# --- SYSTEM HELPER ENGINES & GRAPHIC LOGS ---
def estimate_msrp(make, model, trim):
    base = 35000  # Default fallback
    make_lower = make.lower()
    model_lower = model.lower()
    trim_lower = trim.lower()
    
    if "bmw" in make_lower:
        base = 52000
        if "ix" in model_lower: base = 87000
        elif "5 s" in model_lower: base = 60000
        elif "i4" in model_lower: base = 57900
    elif "audi" in make_lower:
        base = 54000
        if "gt" in model_lower: base = 106000
        elif "q8" in model_lower: base = 75000
    elif "mercedes" in make_lower:
        base = 56000
        if "eqs" in model_lower: base = 104000
        elif "eqe" in model_lower: base = 78000
    elif "tesla" in make_lower:
        base = 42000
        if "model s" in model_lower: base = 75000
        elif "model x" in model_lower: base = 80000
    elif "hyundai" in make_lower:
        base = 32000
        if "ioniq 5" in model_lower: base = 44000
        elif "ioniq 6" in model_lower: base = 42000
    elif "kia" in make_lower:
        base = 31000
        if "ev6" in model_lower: base = 45000
        elif "ev9" in model_lower: base = 56000
    elif "chevrolet" in make_lower:
        base = 34000
        if "silverado" in model_lower: base = 78000
    elif "ford" in make_lower:
        base = 36000
        if "lightning" in model_lower: base = 58000
    elif "lexus" in make_lower:
        base = 46000
        if "rz" in model_lower: base = 60000
    elif "toyota" in make_lower:
        base = 28000
        if "bz4x" in model_lower: base = 43000
        elif "prime" in model_lower: base = 35000
        
    # Trim configuration modifiers
    if any(w in trim_lower for w in ["performance", "plaid", "ultimate", "m50", "m60", "gt", "rs"]):
        base += 12000
    elif any(w in trim_lower for w in ["long range", "premium", "luxury", "executive", "prestige"]):
        base += 5500
    elif any(w in trim_lower for w in ["awd", "4matic", "quattro", "xdrive"]):
        base += 2500
        
    return base

def get_mock_inventory(make, model, trim, zip_code):
    base_price = estimate_msrp(make, model, trim)
    zip_prefix = zip_code[:2] if zip_code else "78"
    
    # Locate regional dealers based on ZIP prefix
    if zip_prefix in ['75', '76', '77', '78', '79']:
         dealers = ["Lone Star Lease Hub (Austin)", "North Texas Premium Auto (Dallas)", "Gulf Coast Capital Vehicles (Houston)"]
    elif zip_prefix in ['10', '11', '12', '13', '14']:
         dealers = ["Manhattan Luxury Guild", "Empire State EV Central (Brooklyn)", "Tri-State Select Imports"]
    elif zip_prefix in ['90', '91', '92', '93', '94', '95']:
         dealers = ["Pacific Coast Auto Gallery (LA)", "Silicon Valley Green Motors", "Southern Cal Car Club"]
    elif zip_prefix in ['30', '31', '32', '33', '34']:
         dealers = ["Sun Coast Imports (Miami)", "Peach State Premium Auto (Atlanta)", "Orlando EV Direct"]
    else:
         dealers = ["Midwest Auto Exchange", "National Fleet Brokerage", "Premium Direct Network"]
         
    # Manufacturer VIN Prefixes
    make_lower = make.lower()
    if "bmw" in make_lower:
        vin_prefix = "WBA"
        colors = ["Alpine White", "Black Sapphire Metallic", "Phytonic Blue", "Dravit Grey"]
    elif "audi" in make_lower:
        vin_prefix = "WA1"
        colors = ["Glacier White Metallic", "Mythos Black Metallic", "Nardo Gray", "Navarra Blue"]
    elif "mercedes" in make_lower:
        vin_prefix = "W1K"
        colors = ["Polar White", "Obsidian Black Metallic", "Selenite Grey", "Manufaktur Alpine Grey"]
    elif "tesla" in make_lower:
        vin_prefix = "5YJ" if "model 3" in model.lower() or "model s" in model.lower() else "7SA"
        colors = ["Pearl White Multi-Coat", "Solid Black", "Midnight Silver Metallic", "Ultra Red"]
    elif "hyundai" in make_lower:
        vin_prefix = "KM8"
        colors = ["Atlas White", "Abyss Black Pearl", "Cyber Gray Metallic", "Shooting Star (Matte)"]
    elif "kia" in make_lower:
        vin_prefix = "KND"
        colors = ["Snow White Pearl", "Aurora Black Pearl", "Interstellar Gray", "Yacht Blue"]
    elif "chevrolet" in make_lower:
        vin_prefix = "1G1"
        colors = ["Summit White", "Black", "Red Hot", "Sterling Gray Metallic"]
    elif "ford" in make_lower:
        vin_prefix = "1FT"
        colors = ["Oxford White", "Shadow Black", "Rapid Red Metallic", "Carbonized Gray"]
    else:
        vin_prefix = "1YV"
        colors = ["Crystal White", "Jet Black", "Graphite Metallic", "Deep Crimson Blue"]
        
    # Generate unique pseudo-random seed based on properties to align consistent displays per selection
    import hashlib
    seed_hash = hashlib.sha256(f"{make}-{model}-{trim}-{zip_code}".encode()).hexdigest()
    seed_val = int(seed_hash[:8], 16) % 10000
    
    units = []
    for idx, dealer in enumerate(dealers[:3]):
        msrp_offset = ((seed_val + idx * 37) % 19) * 100 + ((seed_val + idx * 2) % 3) * 50
        days_on_lot = (seed_val + idx * 43) % 85 + 5
        vin_suffix = f"{((seed_val + idx * 832) % 900000) + 100000}"
        
        full_vin = f"{vin_prefix}8A{idx}K{seed_val % 10}FA{vin_suffix}"
        color = colors[(seed_val + idx) % len(colors)]
        
        units.append({
            "Dealer": dealer,
            "MSRP": int(base_price + msrp_offset),
            "Color": color,
            "Days on Lot": int(days_on_lot),
            "VIN": full_vin
        })
        
    return units

# --- DATA SCHEMAS & UTILITIES ---
class LeaseProgramDetails(BaseModel):
    money_factor: float = Field(description="The buy-rate Money Factor (MF) for this vehicle lease, e.g., 0.00210")
    residual_percentage: int = Field(description="The residual value percentage for the selected lease term (24 or 36 months), e.g., 64")
    lease_cash: float = Field(description="Total national/regional lease cash, EV incentives, and rebates stacked together in dollars, e.g., 7500.00")
    source_citation: str = Field(description="Brief explanation of where this data was gathered or estimated from.")

def fetch_live_lease_program(year, make, model, trim, zip_code, term=36):
    api_key = st.secrets.get("GEMINI_API_KEY") or os.environ.get("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY not found in Streamlit Secrets or Environment Variables.")
        
    client = genai.Client(api_key=api_key)
    
    # Step 1: Query Gemini with Google Search tool to find raw data
    search_prompt = f"""
    Find the official captive lender lease program details (residual percentage and subvented money factor) for the following vehicle:
    Year: {year}
    Make: {make}
    Model: {model}
    Trim: {trim} (Note: If this exact trim name is slightly descriptive or formatted differently in manufacturer lists, do loose/fuzzy matching to find the closest match, such as basic standard AWD/RWD equivalents, rather than failing.)
    ZIP Code: {zip_code}
    Lease Term: {term} months
    
    Search for the current month's subvented/buy-rate Money Factor (MF), Residual Percentage, and standard national or regional rebate/lease cash incentives. If this exact trim is not found, list or match closely related trims.
    """
    
    search_response = client.models.generate_content(
        model='gemini-2.5-flash',
        contents=search_prompt,
        config=types.GenerateContentConfig(
            tools=[types.Tool(google_search=types.GoogleSearch())],
            temperature=0.0
        )
    )
    
    raw_search_text = search_response.text
    
    # Step 2: Use a separate schema-based call without tools to parse raw text into Pydantic model
    parsing_prompt = f"""
    You are an expert financial data extractor. Parse the following raw research text and extract the lease program parameters:
    
    Research Text:
    {raw_search_text}
    
    Instructions:
    - Money Factor (MF): MUST be a small decimal (e.g. 0.00210). If only expressed as an interest rate or APR (e.g. 5.04% APR), convert to MF by dividing the percentage by 2400 (e.g. 5.04 / 2400 = 0.00210).
    - Residual Percentage: MUST be an integer representing percentage (e.g., 64).
    - Lease Cash: Total national/regional lease cash, manufacturer rebates, and stackable EV incentives in dollars (e.g. 7500.0).
    - Source Citation: A brief description of the source site or method of estimation.
    """
    
    parsed_response = client.models.generate_content(
        model='gemini-2.5-flash',
        contents=parsing_prompt,
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=LeaseProgramDetails,
            temperature=0.0
        )
    )
    return parsed_response.parsed

# --- PARAMETRIC INITIALIZATION ---
st.title("🏎️ Universal AI Lease Broker Engine")
st.caption("Parametric Acquisition Dashboard | Cross-Brand Inventory & Rate Matrix")

# --- SIDEBAR: THE LIFECYCLE CONTROLLER ---
with st.sidebar:
    st.header("🎯 Deal Parameters")
    
    # 1. Vehicle Selection - Cascading Dropdowns from Public North America Database
    manufacturers = list(VEHICLE_DATABASE.keys())
    selected_make_index = 0
    if 'active_make' in st.session_state and st.session_state.active_make in manufacturers:
        selected_make_index = manufacturers.index(st.session_state.active_make)
        
    selected_make = st.selectbox("Manufacturer (Real-time DB)", manufacturers, index=selected_make_index)
    
    if selected_make == "Custom / Other Brand":
        make = st.text_input("Manufacturer", st.session_state.get('active_make', "Porsche"))
        model = st.text_input("Model Line", st.session_state.get('active_model', "Taycan"))
        trim = st.text_input("Trim Configuration", st.session_state.get('active_trim', "Taycan 4S"))
    else:
        make = selected_make
        models = list(VEHICLE_DATABASE[make].keys())
        selected_model_index = 0
        if 'active_model' in st.session_state and st.session_state.active_model in models:
            selected_model_index = models.index(st.session_state.active_model)
            
        selected_model = st.selectbox("Model Line", models, index=selected_model_index)
        model = selected_model
        
        trims = VEHICLE_DATABASE[make][model]
        selected_trim_index = 0
        if 'active_trim' in st.session_state and st.session_state.active_trim in trims:
            selected_trim_index = trims.index(st.session_state.active_trim)
            
        selected_trim = st.selectbox("Trim Configuration", trims, index=selected_trim_index)
        trim = selected_trim
        
    year = st.selectbox("Model Year", [2026, 2025], index=0 if st.session_state.get('active_year') == 2026 else 1)
    
    st.markdown("---")
    # 2. Geography & Radius - Replaced Radius with an Interactive Slider
    zip_code = st.text_input("Residential ZIP Code", st.session_state.get('active_zip', "78664"))
    
    radius_val = st.slider("Search Radius (Miles)", min_value=10, max_value=500, value=200, step=10)
    search_radius = "Nationwide" if radius_val == 500 else f"{radius_val} Miles"
    st.caption(f"Active Sweep Range: **{search_radius}**")
    
    st.markdown("---")
    # 3. Credit Profile
    credit_score = st.slider("Credit Score Range", 600, 850, 750)
    tier = "Tier 1 (Top)" if credit_score >= 740 else "Tier 2/3 (Standard Rate)"
    st.caption(f"Estimated Profile: **{tier}**")

    st.markdown("---")
    # 4. Parameters Commit State and Active Session Lock Button
    if st.button("🚀 Lock & Register Deal Parameters", use_container_width=True):
        st.session_state.params_locked = True
        st.session_state.active_make = make
        st.session_state.active_model = model
        st.session_state.active_trim = trim
        st.session_state.active_year = year
        st.session_state.active_zip = zip_code
        st.session_state.active_radius = search_radius
        
        # Pull dynamic mock inventory based on new parameters to auto-populate default evaluation inputs
        fresh_inventory = get_mock_inventory(make, model, trim, zip_code)
        if fresh_inventory:
            st.session_state.target_msrp = fresh_inventory[0]["MSRP"]
            st.session_state.target_dealer = fresh_inventory[0]["Dealer"]
            st.session_state.target_vin = fresh_inventory[0]["VIN"]
        else:
            st.session_state.target_msrp = estimate_msrp(make, model, trim)
            st.session_state.target_dealer = "Primary Regional Center"
            st.session_state.target_vin = "PENDING_SELECTION"
            
        st.success(f"Parameters registered for {year} {make}!")
        st.rerun()
        
    if st.session_state.get('params_locked', False):
        st.success(f"🟢 Active Contract Locked: {st.session_state.get('active_year')} {st.session_state.get('active_make')} {st.session_state.get('active_model')} ({st.session_state.get('active_trim')})")
    else:
        st.info("Awaiting parameters verification. Click registration above.")

# --- SYSTEM ENGINE LOGIC (Determining Tax Classifications) ---
def get_tax_structure(zip_prefix):
    if zip_prefix.startswith(('75', '76', '77', '78', '79')): # Texas Focus
        return "TAX_ON_FULL_PRICE", 0.0625, True
    elif zip_prefix.startswith(('10', '11', '12', '13', '14')): # New York Focus
        return "TAX_ON_TOTAL_PAYMENTS", 0.0887, False
    else:
        return "TAX_ON_PAYMENT", 0.0775, False

tax_type, default_rate, shows_tax_credits = get_tax_structure(zip_code)

# --- APP LAYOUT TABS ---
tab1, tab2, tab3, tab4 = st.tabs(["🔍 Live Inventory Engine", "📊 AI Rate Finder", "🧮 Deal Evaluator", "🎯 Active Leads Pipeline"])

# --- TAB 1: LIVE INVENTORY ---
with tab1:
    st.header(f"Live Location Mapping: {make} {model} inside {search_radius}")
    st.info(f"Scanning localized API endpoints originating near ZIP {zip_code}...")
    
    st.markdown(f"### Target Units Located for {make} {model} {trim}")
    
    # Real-time generated vehicle list mapped to user parameter choices
    mock_universe = get_mock_inventory(make, model, trim, zip_code)
    
    for idx, unit in enumerate(mock_universe):
        c1, c2, c3, c4, c5 = st.columns([2, 1, 1, 2, 1])
        c1.write(f"🏢 **{unit['Dealer']}** ({unit['Color']})")
        c2.write(f"${unit['MSRP']:,}")
        c3.write(f"⏳ {unit['Days on Lot']} Days")
        c4.write(f"`{unit['VIN']}`")
        if c5.button("Load Unit", key=f"unit_btn_{idx}"):
            st.session_state.target_msrp = unit['MSRP']
            st.session_state.target_dealer = unit['Dealer']
            st.session_state.target_vin = unit['VIN']
            st.success(f"Sent {unit['Dealer']} unit to calculation desk!")
            st.rerun()

# --- TAB 2: AI RATE FINDER ---
with tab2:
    st.header("🔮 AI Program Pull (Live Web Scan)")
    st.write("Leveraging Gemini live search to look up this month's official captive lender data.")
    
    lookup_term = st.selectbox("Lease Term Target for Lookup (Months)", [24, 36, 39], index=1)
    
    api_key_available = "GEMINI_API_KEY" in st.secrets or "GEMINI_API_KEY" in os.environ
    if not api_key_available:
        st.warning("⚠️ GEMINI_API_KEY is not configured. Add it to Streamlit Secrets or your local .env file to enable live search lookup.")
        
    if st.button("Execute Live Lease Program Lookup", disabled=not api_key_available):
        with st.spinner(f"Querying Gemini with Google Search for {year} {make} {model} {trim}..."):
            try:
                program = fetch_live_lease_program(year, make, model, trim, zip_code, term=lookup_term)
                
                # Store in session state for Tab 3 calculation defaults
                st.session_state.target_mf = program.money_factor
                st.session_state.target_residual = program.residual_percentage
                st.session_state.target_rebate = program.lease_cash
                st.session_state.target_source = program.source_citation
                
                st.success("Program Located! Live search rates imported successfully.")
                
                col_a, col_b, col_c = st.columns(3)
                col_a.metric("Subvented Money Factor", f"{program.money_factor:.5f} ({program.money_factor * 2400:.2f}% APR)")
                col_b.metric(f"{lookup_term}-Month Residual", f"{program.residual_percentage}%")
                col_c.metric("National Lease Cash", f"${program.lease_cash:,.2f}")
                
                st.info(f"**Source / Search Citation:** {program.source_citation}")
            except Exception as e:
                st.error(f"Error fetching lease program: {e}")

# --- TAB 3: DEAL EVALUATOR ---
with tab3:
    st.header("🧮 Custom Deal Matrix")
    
    current_msrp = st.session_state.get('target_msrp', 62500)
    
    # Read values loaded from Tab 2 search or fallback to defaults
    default_rebate = float(st.session_state.get('target_rebate', 7500.0))
    default_mf = float(st.session_state.get('target_mf', 0.00210))
    default_residual = int(st.session_state.get('target_residual', 64))
    default_residual = min(max(default_residual, 45), 75) # Ensure inside slider bounds [45, 75]
    
    col_x, col_y = st.columns([1, 1])
    
    with col_x:
        st.subheader("Fine-Tune Contract Points")
        msrp_input = st.number_input("Vehicle MSRP ($)", value=current_msrp)
        discount_input = st.slider("Target Pre-Incentive Discount (%)", 0.0, 12.0, 6.5, 0.5)
        rebate_input = st.number_input("Total Rebates / Stacked Incentives ($)", value=default_rebate)
        term_input = st.selectbox("Lease Term Horizon", [24, 36, 39])
        mf_input = st.number_input("Money Factor", value=default_mf, format="%.5f")
        residual_input = st.slider("Residual Percentage (%)", 45, 75, default_residual)
        
        st.markdown("---")
        st.subheader("Localized Tax Configuration")
        st.write(f"Detected Statutory Engine Type: `{tax_type}`")
        local_rate = st.number_input("Combined State/Local Rate (%)", value=default_rate * 100) / 100
        
        tax_credit_active = False
        if shows_tax_credits:
            tax_credit_active = st.checkbox("Apply Lender Sales Tax Certificate / Credit", value=True)

    with col_y:
        st.subheader("Financial Real-Time Output")
        
        sell_price = msrp_input * (1 - (discount_input / 100))
        gross_cap = sell_price + 650
        net_cap = gross_cap - rebate_input
        residual_amt = msrp_input * (residual_input / 100)
        
        monthly_depreciation = (net_cap - residual_amt) / term_input
        monthly_rent = (net_cap + residual_amt) * mf_input
        base_pmt = monthly_depreciation + monthly_rent
        
        if tax_type == "TAX_ON_FULL_PRICE":
            calculated_tax_rate = 0.0125 if tax_credit_active else local_rate
            total_tax_due = sell_price * calculated_tax_rate
            monthly_tax = total_tax_due / term_input
        elif tax_type == "TAX_ON_TOTAL_PAYMENTS":
            total_payments_est = base_pmt * term_input
            total_tax_due = total_payments_est * local_rate
            monthly_tax = total_tax_due / term_input
        else:
            monthly_tax = base_pmt * local_rate
            
        final_payment = base_pmt + monthly_tax
        
        st.metric(f"Target Payment for {make} {model}", f"${final_payment:.2f}/mo")
        st.markdown("---")
        st.write(f"**Pre-Incentive Selling Price:** `${sell_price:,.2f}`")
        st.write(f"**Net Adjusted Capitalized Cost:** `${net_cap:,.2f}`")
        st.write(f"**Total Residual Value Lock:** `${residual_amt:,.2f}`")
        st.write(f"**Monthly Interest / Rent Charge:** `${monthly_rent:,.2f}`")

# --- TAB 4: LEADS CRM ---
with tab4:
    st.header("🎯 Active Outbound Management")
    st.write("Track ongoing negotiations, counters, and dealership sheet submissions.")
    
    pipeline_data = pd.DataFrame([
        {"Dealership": st.session_state.get('target_dealer', "Sample Store"), "VIN": st.session_state.get('target_vin', "PENDING"), "Current Status": "No Contact", "Notes": "Ready for initial script batch distribution."}
    ])
    st.data_editor(pipeline_data, num_rows="dynamic", use_container_width=True)