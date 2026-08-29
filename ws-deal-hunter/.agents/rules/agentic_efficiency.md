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
