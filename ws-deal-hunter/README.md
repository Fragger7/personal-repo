# Workstation Deal Hunter

Autonomous hardware arbitrage and monitoring system for enterprise laptops and workstations.

## Architecture

- **Syndicated Collectors (`collector.py`)**:
  - **eBay Browse REST API**: OAuth2 Client Credentials authentication with item summary aggregation.
  - **Reddit `r/hardwareswap`**: JSON endpoint parser with custom User-Agent and price extraction.
  - **Swappa RSS**: RSS and XML stream aggregator for laptops and MacBooks.
- **AI Hardware Valuation (`evaluator.py`)**:
  - Gemini 2.5 Flash structured JSON extractor for CPU, RAM, SSD, GPU, Screen specs.
  - Valuation model computing Fair Market Value (FMV), dollar spread, and Deal Score ($0.0 - 10.0$).
  - Resilient rule-based heuristic fallback engine.
- **Pushover Alert Dispatcher (`notifier.py`)**:
  - Real-time mobile push notifications for deals meeting criteria: **Deal Score &ge; 8.5** and **Asking Price &le; $750**.
- **Thread-Safe Storage (`storage.py`)**:
  - Atomic JSON file writes (`deals.json`) with `RLock` synchronization.
- **Autonomous Daemon (`daemon.py`)**:
  - Background polling worker supporting one-shot (`--once`) and continuous loops.
- **Dashboards (`app.py` & React UI)**:
  - Streamlit dashboard and full-stack Express/React application.
- **Unit & Integration Test Suite (`test_system.py`)**:
  - 12 comprehensive unit tests covering all modules.

## Getting Started

```bash
# Install Python dependencies
pip install -r requirements.txt

# Run test suite
python3 test_system.py

# Run autonomous daemon (single cycle)
python3 daemon.py --once

# Run Streamlit dashboard
streamlit run app.py
```
