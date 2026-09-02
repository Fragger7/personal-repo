# ⚡ Global Agentic Efficiency & Token Economy Protocol

> **Scope**: Active across all development, coding, and debugging workflows in this project and all Antigravity workspaces.

---

### 🎯 Primary Directive
Maximize developer speed, eliminate unnecessary input/output/cache token burn, avoid rate-limit delays (429s), and preserve prompt cache hits—**while never compromising on depth of reasoning, architectural rigor, or code quality**.

---

### 🛠️ 1. Two-Tier Test Execution Protocol (Zero-Token Iteration)
* **Default Fast Unit Testing**:
  - Unit test suites and regressions executed during development iterations must use deterministic local fixtures or mocked external APIs (e.g. LLM endpoints, live web scraping).
  - Fast test suites must execute in **< 3 seconds** with **0 LLM API tokens burned** and **0 external network delays**.
* **Live Integration on Demand**:
  - Live external API calls and live network scraping suites must only run when explicitly requested via `--live`, `--integration`, or during pre-release audits.

---

### 🧹 2. CLI Output & Context Window Hygiene
* **Suppress Token-Heavy Terminal Noise**:
  - Every line of text output by a shell command is fed back into the agent's context window on every subsequent turn.
  - Avoid commands that output thousands of streaming progress lines, animation frames, or raw HTTP payloads.
  - Use high-signal flags where appropriate (e.g. `pytest -q`, compact test runners, focused python one-liners).
* **Compact Test Failure Logging**:
  - When tests fail, focus output on the specific failure stack trace rather than streaming thousands of passing test lines.

---

### 📖 3. Targeted File Inspection (Bounded Slicing)
* **Never Dump Giant Files into Context**:
  - When inspecting large files (>400 lines), use bounded line ranges (`StartLine` and `EndLine`) or targeted searches (`grep_search`) rather than reading entire massive files unconditionally.
  - Preserves 80%+ of the context window for high-value reasoning and active code synthesis.

---

### 🧠 4. Prompt Optimization & Cache Prefix Invariance
* **Maximize Prompt Cache Hit Rates (Prefix Caching)**:
  - Keep core system instructions, architecture rules, and static directives invariant at the top of memory files so modern LLM inference engines (Gemini, Claude, GPT) hit cached prefixes at 90%+ discount rates.
  - Keep updates focused, concise, and structured.
* **High-Signal, Low-Fluff Communication**:
  - Be direct, technically precise, and actionable. Avoid generic conversational fluff that adds no value to the developer.

---

### 🛡️ 5. The Quality Invariant (No Cutting Corners)
* **Efficiency $\ne$ Laziness**:
  - Token optimization applies strictly to **tool execution overhead, command outputs, file reading bounds, and external test mocking**.
  - **NEVER** produce incomplete stub code, `// TODO: implement later` shortcuts, or superficial reasoning in the name of saving tokens. Full, production-ready, typed implementations are always mandatory.

---

### 🔄 6. Living Guidelines & Continuous Self-Evolution Directive
* **Floor, Not a Ceiling**:
  - The economic and development techniques in this document represent the current best-known baseline.
  - If a newer or more capable AI model (e.g. Claude 3.7+, DeepSeek R1+, GPT-5+, Gemini 2.5/3+) identifies a superior, faster, or more token-efficient technique—or detects that an existing guideline has become stale or sub-optimal:
    1. **Apply the Better Technique**: The agent is explicitly empowered and expected to use the superior method immediately.
    2. **Update the Documentation**: Atomically update `.agents/rules/agentic_efficiency.md` and `AGENTS.md` to reflect the new state of the art so all future sessions inherit the improvement.
    3. **Inform the Developer**: Clearly explain to the user what was improved, the rationale behind the change, and the expected efficiency or performance gain.

---

### 🔬 7. Data Feasibility & Empirical Verification First
* **Probe Before Proposing**:
  - Never hypothesize about scraper yields, API behaviors, or website inventory without running an empirical check first.
  - Run a quick, lightweight probe command (e.g., Python one-liner checking status codes and item counts) to verify real-world data feasibility before proposing new scrapers or query overhauls.
  - Weigh every user feature request or scraper query against the empirical reality of what the platform actually stocks (e.g., Swappa is consumer/Apple heavy; Dell DFS and eBay are enterprise workstation heavy).

---

### ⚠️ 8. Proactive Token Burn & Rate Limit Warnings
* **Warn Before Burning Quota**:
  - If a planned task, batch operation, or evaluation run threatens significant LLM token burn (e.g., un-cached evaluation of 25+ listings via Gemini API) or risks hitting RPM/daily rate limits, **the agent MUST explicitly warn the user upfront**.
  - State the estimated calls, estimated tokens, and quota percentage impact, and propose token-efficient alternatives (e.g. deterministic heuristic pre-screening before LLM calls) to balance rate limit allowances with working session goals.

---

### 🧠 9. Codebase & Architectural Memory Invariance
* **Audit Before Re-Inventing**:
  - Never propose implementing a feature or data source (e.g. "let's add DFS scraping") without first searching the codebase (`grep_search`) to verify whether it is already built, active, or was previously ruled out for a specific technical reason.
  - Rely on the repository source of truth and `AGENTS.md` to avoid redundant suggestions or amnesia about existing components.

---

### 🎯 10. Live Actionability Over Vanity Metrics
* **Strict Quality & Freshness Over Count**:
  - The core objective of deal hunting is **actionable, high-conviction, currently buyable opportunities**.
  - Never preserve, resurrect, or relax filters to keep sold-out, expired, or low-quality listings for the sake of inflating the inventory count.
  - Showing **2 genuine, buyable, high-yield deals** is vastly superior to showing **11 listings where 9 are sold out**. Dead listings destroy user trust immediately.
