import express, { Request, Response } from "express";
import path from "path";
import fs from "fs";
import { exec } from "child_process";
import { GoogleGenAI, Type } from "@google/genai";
import dotenv from "dotenv";

dotenv.config();

const app = express();
const PORT = 3000;

app.use(express.json());

// Initialize Gemini SDK with server-side API Key
const ai = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY,
  httpOptions: {
    headers: {
      "User-Agent": "aistudio-build",
    },
  },
});

const DEALS_FILE = path.join(process.cwd(), "deals.json");

// Helper to read deals.json safely
function readDeals(): any[] {
  try {
    if (!fs.existsSync(DEALS_FILE)) {
      return [];
    }
    const data = fs.readFileSync(DEALS_FILE, "utf-8");
    const parsed = JSON.parse(data);
    return Array.isArray(parsed) ? parsed : (parsed.deals || []);
  } catch (err) {
    console.error("[server] Error reading deals.json:", err);
    return [];
  }
}

// Helper to write deals.json atomically
function writeDealsAtomic(deals: any[]): void {
  const tempPath = path.join(process.cwd(), `deals_${Date.now()}.tmp`);
  fs.writeFileSync(tempPath, JSON.stringify(deals, null, 2), "utf-8");
  fs.renameSync(tempPath, DEALS_FILE);
}

// ==========================================
// API ROUTES
// ==========================================

// 1. Health check
app.get("/api/health", (_req: Request, res: Response) => {
  res.json({
    status: "ok",
    system: "Workstation Deal Hunter",
    time: new Date().toISOString(),
  });
});

// 2. Fetch all deals with query filtering & aggregation
app.get("/api/deals", (req: Request, res: Response) => {
  try {
    const deals = readDeals();
    const minScore = parseFloat(req.query.minScore as string) || 0.0;
    const maxPrice = req.query.maxPrice ? parseFloat(req.query.maxPrice as string) : Infinity;
    const sources = req.query.sources ? (req.query.sources as string).split(",") : [];
    const search = ((req.query.search as string) || "").toLowerCase().trim();
    const onlyHighYield = req.query.onlyHighYield === "true";

    const filtered = deals.filter((d: any) => {
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

    // Sort by deal_score descending
    filtered.sort((a: any, b: any) => (b.deal_score || 0) - (a.deal_score || 0));

    res.json({
      success: true,
      count: filtered.length,
      total_in_store: deals.length,
      deals: filtered,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 3. Stats summary
app.get("/api/stats", (_req: Request, res: Response) => {
  try {
    const deals = readDeals();
    const total = deals.length;
    const highYield = deals.filter((d: any) => (d.deal_score >= 8.5 && d.price <= 750) || d.is_high_yield);
    const totalProfit = deals.reduce((sum: number, d: any) => sum + (d.estimated_profit > 0 ? d.estimated_profit : 0), 0);
    const totalMargin = deals.reduce((sum: number, d: any) => sum + (d.arbitrage_margin_pct > 0 ? d.arbitrage_margin_pct : 0), 0);
    const topScore = deals.reduce((max: number, d: any) => Math.max(max, d.deal_score || 0), 0);

    const sourceBreakdown: Record<string, number> = {};
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
      source_breakdown: sourceBreakdown,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 4. AI Evaluation endpoint (powered by Gemini 2.5 Flash / Gemini 3.7 Flash)
app.post("/api/deals/evaluate", async (req: Request, res: Response) => {
  try {
    const { title, description, price, source, url, seller, location } = req.body;

    if (!title || price === undefined) {
      return res.status(400).json({ success: false, error: "Title and asking price are required." });
    }

    const askingPrice = parseFloat(price) || 0;
    let evalResult: any = null;

    // Try Gemini evaluation on the server
    if (process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY.startsWith("AIza")) {
      try {
        const prompt = `Listing Details:
Source: ${source || "manual"}
Title: ${title}
Asking Price: $${askingPrice}
Seller / Location: ${seller || "Unknown"} (${location || "US"})
Description: ${(description || title).slice(0, 1000)}

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
            temperature: 0.2,
          },
        });

        const text = geminiRes.text?.trim() || "";
        if (text) {
          evalResult = JSON.parse(text);
        }
      } catch (geminiErr) {
        console.warn("[server] Gemini API fallback triggered:", geminiErr);
      }
    }

    // Heuristic Fallback if Gemini not available
    if (!evalResult) {
      const lower = `${title} ${description || ""}`.toLowerCase();
      let cpu = "Intel Core i7 12th/13th Gen";
      let cpuVal = 300;
      if (lower.includes("13950hx") || lower.includes("i9-13")) { cpu = "Intel Core i9-13950HX (16C/24T)"; cpuVal = 450; }
      else if (lower.includes("12950hx") || lower.includes("i9-12")) { cpu = "Intel Core i9-12950HX (16C/24T)"; cpuVal = 380; }
      else if (lower.includes("7940hs") || lower.includes("ryzen 9")) { cpu = "AMD Ryzen 9 Pro 7940HS (8C/16T)"; cpuVal = 400; }
      else if (lower.includes("m2 max")) { cpu = "Apple M2 Max (12-Core)"; cpuVal = 600; }

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
      if (lower.includes("4090")) { gpu = "NVIDIA RTX 4090 16GB"; gpuVal = 750; }
      else if (lower.includes("4080")) { gpu = "NVIDIA RTX 4080 12GB"; gpuVal = 550; }
      else if (lower.includes("a4500") || lower.includes("a5000")) { gpu = "NVIDIA RTX A4500 16GB ECC"; gpuVal = 520; }
      else if (lower.includes("ada") || lower.includes("3500 ada")) { gpu = "NVIDIA RTX 3500 Ada 12GB"; gpuVal = 480; }
      else if (lower.includes("a2000") || lower.includes("4060")) { gpu = "NVIDIA RTX A2000 / RTX 4060 8GB"; gpuVal = 320; }
      else if (lower.includes("38-core")) { gpu = "Apple 38-Core GPU"; gpuVal = 450; }

      let screen = '16" Workstation Display';
      let screenVal = 100;
      if (lower.includes("4k") || lower.includes("uhd+")) { screen = '16" 4K UHD+ (3840x2400) IPS/OLED'; screenVal = 200; }
      else if (lower.includes("oled")) { screen = '16" 3.2K OLED 120Hz'; screenVal = 180; }
      else if (lower.includes("desktop") || lower.includes("mac studio")) { screen = "Desktop (No screen)"; screenVal = 0; }

      const fmv = 250 + cpuVal + (ramGb * 3.5) + (ssdGb / 512 * 45) + gpuVal + screenVal;
      const profit = Math.max(0, fmv - askingPrice);
      const margin = askingPrice > 0 ? (profit / askingPrice) * 100 : 0;
      let score = 5.0 + (profit / 180) + (margin / 50);
      if (askingPrice <= 750 && (gpuVal >= 450 || ramGb >= 64)) score += 1.2;
      score = Math.min(9.9, Math.max(1.0, +score.toFixed(1)));

      evalResult = {
        cpu,
        ram_gb: ramGb,
        ssd_gb: ssdGb,
        gpu,
        screen,
        condition: "Used / Good",
        fair_market_value: fmv,
        deal_score: score,
        summary: `${cpu} with ${ramGb}GB RAM, ${ssdGb}GB NVMe SSD, and ${gpu}.`,
        actionable_recommendation: score >= 9.0 ? "INSTANT ARBITRAGE BUY" : (score >= 8.5 ? "STRONG BUY (HIGH YIELD)" : "FAIR VALUE"),
        confidence_score: 0.88,
      };
    }

    const fmv = evalResult.fair_market_value || (askingPrice * 1.25);
    const profit = Math.max(0, +(fmv - askingPrice).toFixed(2));
    const margin = askingPrice > 0 ? +((profit / askingPrice) * 100).toFixed(1) : 0;
    const dealScore = +(evalResult.deal_score || 5.0).toFixed(1);

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
        condition: evalResult.condition || "Used",
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
      created_utc: new Date().toISOString(),
      evaluated_at: new Date().toISOString(),
      alerted: false,
      is_high_yield: dealScore >= 8.5 && askingPrice <= 750,
    };

    if (req.body.saveToStore) {
      const current = readDeals();
      current.unshift(dealRecord);
      writeDealsAtomic(current);
    }

    res.json({
      success: true,
      deal: dealRecord,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 5. Trigger live Python data collection cycle
app.post("/api/collect", (_req: Request, res: Response) => {
  exec("python3 daemon.py --once", (error, stdout, stderr) => {
    if (error) {
      console.error("[server] Collector error:", error, stderr);
      return res.status(500).json({ success: false, error: stderr || error.message });
    }
    const deals = readDeals();
    res.json({
      success: true,
      message: "Sync cycle executed successfully.",
      total_deals: deals.length,
      stdout: stdout.slice(-1000),
    });
  });
});

// 6. Test Pushover notification dispatch
app.post("/api/notify/pushover", async (req: Request, res: Response) => {
  try {
    const { dealId } = req.body;
    const deals = readDeals();
    const targetDeal = deals.find((d: any) => d.id === dealId) || deals[0];

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
          title: `🔥 [${targetDeal.deal_score}/10 DEAL] $${targetDeal.price} ${targetDeal.specs?.cpu}`,
          asking: `$${targetDeal.price}`,
          fmv: `$${targetDeal.fair_market_value}`,
          profit: `+$${targetDeal.estimated_profit} (${targetDeal.arbitrage_margin_pct}% ROI)`,
          url: targetDeal.url,
        },
      });
    }

    // Call Pushover API
    const bodyParams = new URLSearchParams({
      token: apiToken,
      user: userKey,
      title: `🔥 [${targetDeal.deal_score}/10 DEAL] $${targetDeal.price} ${targetDeal.specs?.cpu}`,
      message: `💻 ${targetDeal.title}\n\n• Asking: $${targetDeal.price} (FMV: $${targetDeal.fair_market_value})\n• Profit: +$${targetDeal.estimated_profit} (${targetDeal.arbitrage_margin_pct}% ROI)\n• Specs: ${targetDeal.specs?.cpu} | ${targetDeal.specs?.ram_gb}GB | ${targetDeal.specs?.gpu}\n\n${targetDeal.actionable_recommendation}`,
      url: targetDeal.url,
      url_title: `Open ${targetDeal.source.toUpperCase()} Listing ↗`,
      priority: targetDeal.deal_score >= 9.5 ? "2" : "1",
      sound: "magic",
    });

    const pushRes = await fetch("https://api.pushover.net/1/messages.json", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: bodyParams.toString(),
    });

    const pushJson = await pushRes.json();
    res.json({
      success: pushRes.ok,
      simulated: false,
      pushover_response: pushJson,
    });
  } catch (err: any) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// 7. Run Python test suite
app.get("/api/python/run-tests", (_req: Request, res: Response) => {
  exec("python3 test_system.py", (error, stdout, stderr) => {
    res.json({
      success: !error,
      stdout: stdout || stderr,
      exitCode: error ? error.code : 0,
    });
  });
});

// 8. Fetch Python source files for live architecture explorer
app.get("/api/python/files", (_req: Request, res: Response) => {
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
    "requirements.txt",
  ];

  const files: Record<string, string> = {};
  for (const name of filenames) {
    const p = path.join(process.cwd(), name);
    if (fs.existsSync(p)) {
      files[name] = fs.readFileSync(p, "utf-8");
    }
  }

  res.json({ success: true, files });
});

// ==========================================
// VITE MIDDLEWARE SETUP
// ==========================================
async function startServer() {
  if (process.env.NODE_ENV !== "production") {
    const { createServer: createViteServer } = await import("vite");
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (_req: Request, res: Response) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`[Workstation Deal Hunter] Server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer();
