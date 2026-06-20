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
    Find the official captive lender lease program details for:
    Year: {year}
    Make: {make}
    Model: {model}
    Trim: {trim}
    ZIP Code: {zip_code}
    Lease Term: {term} months
    
    Search for the current month's subvented/buy-rate Money Factor (MF), Residual Percentage, and standard national or regional rebate/lease cash incentives.
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
        st.success(f"Parameters registered for {year} {make}!")
        
    if st.session_state.get('params_locked', False):
        st.success(f"🟢 Active Contract Locked: {st.session_state.get('active_year')} {st.session_state.get('active_make')} {st.session_state.get('active_model')}")
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
    st.header(f"Live Location Mapping: {make} {model} inside {search_radius} miles")
    st.info(f"Scanning localized API endpoints originating near ZIP {zip_code}...")
    
    st.markdown(f"### Target Units Located for {make} {model} {trim}")
    
    mock_universe = [
        {"Dealer": "Regional Luxury Center", "MSRP": 62500, "Color": "Alpine White", "Days on Lot": 72, "VIN": "WBA53AW00G829102"},
        {"Dealer": "Metro Automotive Group", "MSRP": 64100, "Color": "Mineral Grey", "Days on Lot": 18, "VIN": "WBA53AW00G829554"},
    ]
    
    for idx, unit in enumerate(mock_universe):
        c1, c2, c3, c4, c5 = st.columns([2, 1, 1, 2, 1])
        c1.write(f"🏢 **{unit['Dealer']}**")
        c2.write(f"${unit['MSRP']:,}")
        c3.write(f"⏳ {unit['Days on Lot']} Days")
        c4.write(f"`{unit['VIN']}`")
        if c5.button("Load Unit", key=f"unit_btn_{idx}"):
            st.session_state.target_msrp = unit['MSRP']
            st.session_state.target_dealer = unit['Dealer']
            st.session_state.target_vin = unit['VIN']
            st.success(f"Sent {unit['Dealer']} unit to calculation desk!")

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