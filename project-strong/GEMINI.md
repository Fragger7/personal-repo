# IPTV Playlist Analytics Dashboard

## 📌 Project Overview
A lightweight Streamlit dashboard designed to parse, validate, and analyze IPTV playlist credentials (Xtream Codes API format) from messy unstructured text blocks. The tool processes nodes in a multi-tier workflow with outbound network protection, rate limiting, and lazy evaluation to ensure security, speed, and efficiency.

## 🏗️ Architecture & Core Components
The project is centered around a single script, [app.py](file:///C:/Development/Apps/Project%20Strong/app.py).

### 1. Security & Outbound Protection
* **IP Monitoring**: The dashboard utilizes [check_network_shield](file:///C:/Development/Apps/Project%20Strong/app.py#L19) to query a public utility (`api.ipify.org`). It displays a connection status badge (`🟢 VPN PROTECTED` vs `🔴 SHIELD INACTIVE`). If the outbound IP status is unknown or disconnected, user actions are locked to prevent accidental DNS/IP leakage.
* **Evasion Headers**: Outbound HTTP requests mimic standard IPTV hardware configurations (`IPTVSmartersPro` User-Agent) via `EVASION_HEADERS` to circumvent target-side user-agent blocking.

### 2. Multi-Tiered Discovery Flow
* **Parser Engine**: The [parse_m3u_urls](file:///C:/Development/Apps/Project%20Strong/app.py#L27) function extracts base URLs, usernames, and passwords using regex patterns for both standard Xtream Codes `player_api.php` links and `get.php` fallbacks.
* **Tier 1 (Fast Handshake & Stat Retrieval)**: [evaluate_account](file:///C:/Development/Apps/Project%20Strong/app.py#L43) validates credentials asynchronously (lightweight/instant). Overall Live channel and VOD library counts are lazy loaded on demand by clicking a dedicated button underneath the display manifest grid, preventing initial query bottlenecks and hangs during bulk link checks.
* **Tier 2 (On-Demand Category Exploration & Local Grouping)**: Once a node is active, [fetch_lazy_details](file:///C:/Development/Apps/Project%20Strong/app.py#L87) retrieves the list of categories and full live streams concurrently. It parses them locally to count channels within each group (rendered in parentheses in the selection dropdown) and filters streams locally to circumvent buggy server-side category endpoints, ensuring 100% of the channels display with their logos.
* **State & Filter Management**: Handshake results are stored in Streamlit's `st.session_state` to prevent script execution resets when selecting category values. A "Show only Active Connections" checkbox filters both the display table and the detailed accordion lists dynamically.
* **Status Utilities**: A custom helper [statusline.ps1](file:///C:/Development/Apps/statusline.ps1) receives agent telemetry on standard input to format the CLI status bar with the current active directory, active model, and token/context remaining information. Alternatively, the user can run the `/statusline` slash command to open the interactive Status Picker Panel, using `/` to toggle specific built-in elements (e.g., active model, task counters, context percentage) on and off, pressing Enter to commit the selection and exit, or Esc to cancel.

---

## 🛠️ Technology Stack
* **UI Framework**: Streamlit
* **Networking**: HTTPX (Asynchronous Client)
* **Concurrency**: Asyncio (Semaphore-throttled)
* **Data Processing**: Pandas

---

## 🚀 Running the Project
Ensure you have the dependencies installed:
```bash
pip install streamlit httpx pandas
```
To run the local development server on demand:
* **Option A (Double-Click)**: Double-click the helper script **[run.bat](file:///C:/Development/Apps/Project%20Strong/run.bat)** in your project root folder. This launches the application in windowless mode (via `pythonw`) and closes the console window automatically.
* **Option B (Terminal)**: Execute the python module directly from your project directory to bypass Windows PATH limitations:
  ```bash
  python -m streamlit run app.py
  ```

---

## 🧠 Ongoing Knowledge & Context
### Known Constraints & Behaviors
* **Evasion**: If connection issues occur, verify if the User-Agent in `EVASION_HEADERS` needs to be rotated to another standard client.
* **Logging**: Standard Python console logging is enabled at the `INFO` level. Operation traces (handshakes, lazy queries, sizing downloads) print to stdout, allowing you to troubleshoot connection and parse errors live inside the terminal console window.
