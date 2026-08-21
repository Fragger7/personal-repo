var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));

// server.ts
var import_express = __toESM(require("express"), 1);
var import_path = __toESM(require("path"), 1);
var import_fs = __toESM(require("fs"), 1);
var import_child_process = require("child_process");
var import_genai = require("@google/genai");
var import_dotenv = __toESM(require("dotenv"), 1);
import_dotenv.default.config();
var app = (0, import_express.default)();
var PORT = 3e3;
app.use(import_express.default.json());
var ai = new import_genai.GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY,
  httpOptions: {
    headers: {
      "User-Agent": "aistudio-build"
    }
  }
});
var DEALS_FILE = import_path.default.join(process.cwd(), "deals.json");
function readDeals() {
  try {
    if (!import_fs.default.existsSync(DEALS_FILE)) {
      return [];
    }
    const data = import_fs.default.readFileSync(DEALS_FILE, "utf-8");
    const parsed = JSON.parse(data);
    return Array.isArray(parsed) ? parsed : parsed.deals || [];
  } catch (err) {
    console.error("[server] Error reading deals.json:", err);
    return [];
  }
}
function writeDealsAtomic(deals) {
  const tempPath = import_path.default.join(process.cwd(), `deals_${Date.now()}.tmp`);
  import_fs.default.writeFileSync(tempPath, JSON.stringify(deals, null, 2), "utf-8");
  import_fs.default.renameSync(tempPath, DEALS_FILE);
}
app.get("/api/health", (_req, res) => {
  res.json({
    status: "ok",
    system: "Workstation Deal Hunter",
    time: (/* @__PURE__ */ new Date()).toISOString()
  });
});
app.get("/api/deals", (req, res) => {
  try {
    const deals = readDeals();
    const minScore = parseFloat(req.query.minScore) || 0;
    const maxPrice = req.query.maxPrice ? parseFloat(req.query.maxPrice) : Infinity;
    const sources = req.query.sources ? req.query.sources.split(",") : [];
    const search = (req.query.search || "").toLowerCase().trim();
    const onlyHighYield = req.query.onlyHighYield === "true";
    const filtered = deals.filter((d) => {
      if (d.deal_score < minScore) return false;
      if (d.price > maxPrice) return false;
      if (sources.length > 0 && !sources.includes(d.source.toLowerCase())) return false;
      if (onlyHighYield && !d.is_high_yield && !(d.deal_score >= 8.5 && d.price <= 750)) return false;
      if (search) {
        const searchable = `${d.title} ${d.specs?.cpu || ""} ${d.specs?.gpu || ""} ${d.specs?.ram_gb || ""}gb ${d.summary || ""} ${d.source}`.toLowerCase();
        if (!searchable.includes(search)) return false;
      }
      return true;
    });
    filtered.sort((a, b) => (b.deal_score || 0) - (a.deal_score || 0));
    res.json({
      success: true,
      count: filtered.length,
      total_in_store: deals.length,
      deals: filtered
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});
app.delete("/api/deals/:id", (req, res) => {
  try {
    const { id } = req.params;
    const deals = readDeals();
    const filtered = deals.filter((d) => d.id !== id && d.url !== id);
    const deletedCount = deals.length - filtered.length;
    if (deletedCount > 0) {
      writeDealsAtomic(filtered);
    }
    res.json({
      success: true,
      deletedCount,
      remaining: filtered.length
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});
app.get("/api/stats", (_req, res) => {
  try {
    const deals = readDeals();
    const total = deals.length;
    const highYield = deals.filter((d) => d.deal_score >= 8.5 && d.price <= 750 || d.is_high_yield);
    const totalProfit = deals.reduce((sum, d) => sum + (d.estimated_profit > 0 ? d.estimated_profit : 0), 0);
    const totalMargin = deals.reduce((sum, d) => sum + (d.arbitrage_margin_pct > 0 ? d.arbitrage_margin_pct : 0), 0);
    const topScore = deals.reduce((max, d) => Math.max(max, d.deal_score || 0), 0);
    const sourceBreakdown = {};
    for (const d of deals) {
      const src = d.source || "unknown";
      sourceBreakdown[src] = (sourceBreakdown[src] || 0) + 1;
    }
    res.json({
      success: true,
      total_deals: total,
      high_yield_deals: highYield.length,
      avg_profit: total > 0 ? Math.round(totalProfit / total) : 0,
      avg_margin_pct: total > 0 ? +(totalMargin / total).toFixed(1) : 0,
      top_score: topScore,
      source_breakdown: sourceBreakdown
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});
app.post("/api/deals/evaluate", async (req, res) => {
  try {
    const { title, description, price, source, url, seller, location } = req.body;
    if (!title || price === void 0) {
      return res.status(400).json({ success: false, error: "Title and asking price are required." });
    }
    const askingPrice = parseFloat(price) || 0;
    let evalResult = null;
    if (process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY.startsWith("AIza")) {
      try {
        const prompt = `Listing Details:
Source: ${source || "manual"}
Title: ${title}
Asking Price: $${askingPrice}
Seller / Location: ${seller || "Unknown"} (${location || "US"})
Description: ${(description || title).slice(0, 1e3)}

Extract exact hardware specs (CPU, RAM GB, SSD GB, GPU, Screen), calculate realistic secondary Fair Market Value (FMV), and score the deal (0.0 to 10.0). High scores (>=8.5) indicate high-margin workstation arbitrage.`;
        const geminiRes = await ai.models.generateContent({
          model: "gemini-2.5-flash",
          contents: prompt,
          config: {
            systemInstruction: `You are an elite enterprise workstation & PC hardware arbitrage valuation specialist.
Respond ONLY with structured JSON matching:
{
  "cpu": "exact CPU name and generation",
  "ram_gb": integer,
  "ssd_gb": integer,
  "gpu": "exact GPU model and VRAM",
  "screen": "display size, panel, resolution or 'Desktop'",
  "condition": "assessed condition",
  "fair_market_value": number,
  "deal_score": number,
  "summary": "1-2 sentence summary of specs and hardware value",
  "actionable_recommendation": "INSTANT ARBITRAGE BUY | STRONG BUY | FAIR VALUE | OVERPRICED",
  "confidence_score": number
}`,
            responseMimeType: "application/json",
            temperature: 0.2
          }
        });
        const text = geminiRes.text?.trim() || "";
        if (text) {
          evalResult = JSON.parse(text);
        }
      } catch (geminiErr) {
        console.warn("[server] Gemini API fallback triggered:", geminiErr);
      }
    }
    if (!evalResult) {
      const lower = `${title} ${description || ""}`.toLowerCase();
      let cpu = "Intel Core i7 12th/13th Gen";
      let cpuVal = 300;
      if (lower.includes("13950hx") || lower.includes("i9-13")) {
        cpu = "Intel Core i9-13950HX (16C/24T)";
        cpuVal = 450;
      } else if (lower.includes("12950hx") || lower.includes("i9-12")) {
        cpu = "Intel Core i9-12950HX (16C/24T)";
        cpuVal = 380;
      } else if (lower.includes("7940hs") || lower.includes("ryzen 9")) {
        cpu = "AMD Ryzen 9 Pro 7940HS (8C/16T)";
        cpuVal = 400;
      } else if (lower.includes("m2 max")) {
        cpu = "Apple M2 Max (12-Core)";
        cpuVal = 600;
      }
      let ramGb = 16;
      if (lower.includes("128gb") || lower.includes("128 gb")) ramGb = 128;
      else if (lower.includes("64gb") || lower.includes("64 gb")) ramGb = 64;
      else if (lower.includes("32gb") || lower.includes("32 gb")) ramGb = 32;
      let ssdGb = 512;
      if (lower.includes("4tb")) ssdGb = 4096;
      else if (lower.includes("2tb")) ssdGb = 2048;
      else if (lower.includes("1tb")) ssdGb = 1024;
      let gpu = "Integrated";
      let gpuVal = 0;
      if (lower.includes("4090")) {
        gpu = "NVIDIA RTX 4090 16GB";
        gpuVal = 750;
      } else if (lower.includes("4080")) {
        gpu = "NVIDIA RTX 4080 12GB";
        gpuVal = 550;
      } else if (lower.includes("a4500") || lower.includes("a5000")) {
        gpu = "NVIDIA RTX A4500 16GB ECC";
        gpuVal = 520;
      } else if (lower.includes("ada") || lower.includes("3500 ada")) {
        gpu = "NVIDIA RTX 3500 Ada 12GB";
        gpuVal = 480;
      } else if (lower.includes("a2000") || lower.includes("4060")) {
        gpu = "NVIDIA RTX A2000 / RTX 4060 8GB";
        gpuVal = 320;
      } else if (lower.includes("38-core")) {
        gpu = "Apple 38-Core GPU";
        gpuVal = 450;
      }
      let screen = '16" Workstation Display';
      let screenVal = 100;
      if (lower.includes("4k") || lower.includes("uhd+")) {
        screen = '16" 4K UHD+ (3840x2400) IPS/OLED';
        screenVal = 200;
      } else if (lower.includes("oled")) {
        screen = '16" 3.2K OLED 120Hz';
        screenVal = 180;
      } else if (lower.includes("desktop") || lower.includes("mac studio")) {
        screen = "Desktop (No screen)";
        screenVal = 0;
      }
      const fmv2 = 250 + cpuVal + ramGb * 3.5 + ssdGb / 512 * 45 + gpuVal + screenVal;
      const profit2 = Math.max(0, fmv2 - askingPrice);
      const margin2 = askingPrice > 0 ? profit2 / askingPrice * 100 : 0;
      let score = 5 + profit2 / 180 + margin2 / 50;
      if (askingPrice <= 750 && (gpuVal >= 450 || ramGb >= 64)) score += 1.2;
      score = Math.min(9.9, Math.max(1, +score.toFixed(1)));
      evalResult = {
        cpu,
        ram_gb: ramGb,
        ssd_gb: ssdGb,
        gpu,
        screen,
        condition: "Used / Good",
        fair_market_value: fmv2,
        deal_score: score,
        summary: `${cpu} with ${ramGb}GB RAM, ${ssdGb}GB NVMe SSD, and ${gpu}.`,
        actionable_recommendation: score >= 9 ? "INSTANT ARBITRAGE BUY" : score >= 8.5 ? "STRONG BUY (HIGH YIELD)" : "FAIR VALUE",
        confidence_score: 0.88
      };
    }
    const fmv = evalResult.fair_market_value || askingPrice * 1.25;
    const profit = Math.max(0, +(fmv - askingPrice).toFixed(2));
    const margin = askingPrice > 0 ? +(profit / askingPrice * 100).toFixed(1) : 0;
    const dealScore = +(evalResult.deal_score || 5).toFixed(1);
    const dealRecord = {
      id: `manual_${Date.now()}`,
      source: source || "manual",
      title,
      price: askingPrice,
      url: url || "#",
      specs: {
        cpu: evalResult.cpu,
        ram_gb: evalResult.ram_gb,
        ssd_gb: evalResult.ssd_gb,
        gpu: evalResult.gpu,
        screen: evalResult.screen,
        condition: evalResult.condition || "Used"
      },
      fair_market_value: fmv,
      estimated_profit: profit,
      arbitrage_margin_pct: margin,
      deal_score: dealScore,
      summary: evalResult.summary,
      actionable_recommendation: evalResult.actionable_recommendation,
      confidence_score: evalResult.confidence_score || 0.9,
      seller: seller || "Manual Input",
      location: location || "US",
      created_utc: (/* @__PURE__ */ new Date()).toISOString(),
      evaluated_at: (/* @__PURE__ */ new Date()).toISOString(),
      alerted: false,
      is_high_yield: dealScore >= 8.5 && askingPrice <= 750
    };
    if (req.body.saveToStore) {
      const current = readDeals();
      current.unshift(dealRecord);
      writeDealsAtomic(current);
    }
    res.json({
      success: true,
      deal: dealRecord
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});
app.post("/api/collect", (_req, res) => {
  (0, import_child_process.exec)("python3 daemon.py --once", (error, stdout, stderr) => {
    if (error) {
      console.error("[server] Collector error:", error, stderr);
      return res.status(500).json({ success: false, error: stderr || error.message });
    }
    const deals = readDeals();
    res.json({
      success: true,
      message: "Sync cycle executed successfully.",
      total_deals: deals.length,
      stdout: stdout.slice(-1e3)
    });
  });
});
app.post("/api/notify/pushover", async (req, res) => {
  try {
    const { dealId } = req.body;
    const deals = readDeals();
    const targetDeal = deals.find((d) => d.id === dealId) || deals[0];
    if (!targetDeal) {
      return res.status(404).json({ success: false, error: "No deals available to notify." });
    }
    const userKey = process.env.PUSHOVER_USER_KEY;
    const apiToken = process.env.PUSHOVER_API_TOKEN;
    if (!userKey || !apiToken) {
      return res.json({
        success: true,
        simulated: true,
        message: "Simulated Pushover push alert dispatched (Set PUSHOVER_USER_KEY and PUSHOVER_API_TOKEN for live devices).",
        payload: {
          title: `\u{1F525} [${targetDeal.deal_score}/10 DEAL] $${targetDeal.price} ${targetDeal.specs?.cpu}`,
          asking: `$${targetDeal.price}`,
          fmv: `$${targetDeal.fair_market_value}`,
          profit: `+$${targetDeal.estimated_profit} (${targetDeal.arbitrage_margin_pct}% ROI)`,
          url: targetDeal.url
        }
      });
    }
    const bodyParams = new URLSearchParams({
      token: apiToken,
      user: userKey,
      title: `\u{1F525} [${targetDeal.deal_score}/10 DEAL] $${targetDeal.price} ${targetDeal.specs?.cpu}`,
      message: `\u{1F4BB} ${targetDeal.title}

\u2022 Asking: $${targetDeal.price} (FMV: $${targetDeal.fair_market_value})
\u2022 Profit: +$${targetDeal.estimated_profit} (${targetDeal.arbitrage_margin_pct}% ROI)
\u2022 Specs: ${targetDeal.specs?.cpu} | ${targetDeal.specs?.ram_gb}GB | ${targetDeal.specs?.gpu}

${targetDeal.actionable_recommendation}`,
      url: targetDeal.url,
      url_title: `Open ${targetDeal.source.toUpperCase()} Listing \u2197`,
      priority: targetDeal.deal_score >= 9.5 ? "2" : "1",
      sound: "magic"
    });
    const pushRes = await fetch("https://api.pushover.net/1/messages.json", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: bodyParams.toString()
    });
    const pushJson = await pushRes.json();
    res.json({
      success: pushRes.ok,
      simulated: false,
      pushover_response: pushJson
    });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});
app.get("/api/python/run-tests", (_req, res) => {
  (0, import_child_process.exec)("python3 test_system.py", (error, stdout, stderr) => {
    res.json({
      success: !error,
      stdout: stdout || stderr,
      exitCode: error ? error.code : 0
    });
  });
});
app.get("/api/python/files", (_req, res) => {
  const filenames = [
    "daemon.py",
    "collector.py",
    "evaluator.py",
    "notifier.py",
    "storage.py",
    "app.py",
    "test_system.py",
    "git_sync.py",
    "README.md",
    "requirements.txt"
  ];
  const files = {};
  for (const name of filenames) {
    const p = import_path.default.join(process.cwd(), name);
    if (import_fs.default.existsSync(p)) {
      files[name] = import_fs.default.readFileSync(p, "utf-8");
    }
  }
  res.json({ success: true, files });
});
app.post("/api/git/push", (req, res) => {
  const message = req.body.message || "feat(ws-deal-hunter): update workstation deal hunter system";
  (0, import_child_process.exec)(`python3 git_sync.py --push "${message.replace(/"/g, '\\"')}"`, (error, stdout, stderr) => {
    res.json({
      success: !error,
      stdout: stdout || stderr,
      error: error ? error.message : null
    });
  });
});
app.post("/api/git/pull", (_req, res) => {
  (0, import_child_process.exec)("python3 git_sync.py --pull", (error, stdout, stderr) => {
    res.json({
      success: !error,
      stdout: stdout || stderr,
      error: error ? error.message : null
    });
  });
});
async function startServer() {
  if (process.env.NODE_ENV !== "production") {
    const { createServer: createViteServer } = await import("vite");
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa"
    });
    app.use(vite.middlewares);
  } else {
    const distPath = import_path.default.join(process.cwd(), "dist");
    app.use(import_express.default.static(distPath));
    app.get("*", (_req, res) => {
      res.sendFile(import_path.default.join(distPath, "index.html"));
    });
  }
  app.listen(PORT, "0.0.0.0", () => {
    console.log(`[Workstation Deal Hunter] Server listening on http://0.0.0.0:${PORT}`);
  });
}
startServer();
//# sourceMappingURL=server.cjs.map
