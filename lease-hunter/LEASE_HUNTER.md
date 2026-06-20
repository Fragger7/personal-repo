# Universal Lease Hunter Engine - System Architecture & Developer Guide

This document contains the complete system architecture, operational decisions, cloud deployment triggers, and step-by-step Git lifecycle workflows for the **Universal Lease Hunter Engine**. It is designed to serve as a comprehensive knowledge source for both human developers and autonomous AI agents (LLMs).

---

## 📌 Project Overview
The Universal Lease Hunter Engine is a parametric Streamlit application. It is designed to find active vehicle inventory, retrieve live lease terms (residual percentages, subvented money factors, incentives) via the Gemini API, and compute precise lease payments across different jurisdictions with complex, multi-state tax rules.

---

## 🏗️ Architecture & Core Components

### 1. Parametric Input Controls
All inputs originate in the Streamlit Sidebar:
*   **Vehicle Specifications**: Manufacturer (`make`), Model Line (`model`), Trim Configuration (`trim`), and Model Year (`year`).
*   **Geography**: Residential ZIP Code (`zip_code`) and Search Radius.
*   **Credit Profile**: Credit score rating slider, mapping to Tier 1 (740+) or Standard Tier (below 740).

### 2. Live Inventory Engine (Tab 1)
Designed to locate actual target units. 
*   **Current State**: Simulates inventory searches with a mock list of matching vehicles.
*   **User Action**: Clicking "Load Unit" imports the vehicle's MSRP, dealership name, and VIN into `st.session_state` to seed calculations automatically.

### 3. AI Rate Finder (Tab 2)
Provides automated lease rate extraction.
*   **Objective**: Make a real call using the Google GenAI SDK (`google-genai`) using Gemini with Search Grounding (`google_search=True`) to scrape current-month captive lender rates (Residuals, Money Factors, Rebates) for the specific Make/Model/Trim/ZIP.
*   **State Integration**: Fetched metrics should automatically save to `st.session_state` to feed the Custom Deal Matrix.

### 4. Deal Evaluator & Tax Engine (Tab 3)
The heart of the lease calculator. It computes depreciation and rent charges, applying specific local tax regulations based on ZIP code:
*   **`TAX_ON_FULL_PRICE`** (Texas focus: prefixes `75`, `76`, `77`, `78`, `79`): 
    *   Taxes are calculated on the full pre-incentive selling price of the car upfront.
    *   Standard Rate: `6.25%`.
    *   *Lender Tax Credit Bypass*: If a manufacturer tax credit certificate is active, the effective tax rate is reduced to `1.25%`.
*   **`TAX_ON_TOTAL_PAYMENTS`** (New York focus: prefixes `10`, `11`, `12`, `13`, `14`):
    *   Taxes are calculated upfront on the sum of all monthly base lease payments.
    *   Standard Rate: `8.87%`.
*   **`TAX_ON_PAYMENT`** (Default: e.g., CA, FL, AZ):
    *   Taxes are applied monthly on top of the base lease payment.
    *   Standard Rate: `7.75%`.

### 5. Active Leads CRM (Tab 4)
A simple interactive tracker for negotiating deals. Uses a Pandas `data_editor` to allow users to dynamically input notes, VINs, dealership names, and negotiation status.

---

## 🛠️ Technology Stack
*   **UI Framework**: Streamlit (Python)
*   **AI Integration**: Google GenAI Python SDK (`google-genai`)
*   **Data Handling**: Pandas, JSON
*   **State Management**: Streamlit Session State (`st.session_state`)

---

## 🚀 Running Locally
To test or run the dashboard locally:
1.  **Install Dependencies**:
    ```bash
    pip install -r requirements.txt
    ```
2.  **Execute App**:
    ```bash
    python -m streamlit run app.py
    ```
3.  **Shortcut**: Double-click [run.bat](file:///C:/Development/Apps/Lease%20Hunter/run.bat) to launch in windowless mode.

---

## ☁️ Cloud Deployment Pipeline (Streamlit Community Cloud)

Streamlit Community Cloud monitors the remote Git repository and hot-reloads changes automatically.

*   **Repository Address**: `https://github.com/Fragger7/personal-repo`
*   **Target Branch**: `main`
*   **Target App File**: `lease-hunter/app.py`
*   **Secrets Configuration**: Streamlit App Settings > Secrets:
    ```toml
    GEMINI_API_KEY = "your_google_studio_api_key"
    ```

---

## 🤖 AI Agent Git Operations Lifecycle (Important for LLMs)

Follow the rules defined in [.agents/AGENTS.md](file:///C:/Development/Apps/Lease%20Hunter/.agents/AGENTS.md) exactly:
1.  **Synchronize on startup** using a temporary clone to fetch updates from `lease-hunter/` in the remote repository.
2.  **Commit and push in isolation** by copy-staging modifications into a temporary clone of the mono-repo, staging only the `lease-hunter/` folder, and pushing back to `main`.
