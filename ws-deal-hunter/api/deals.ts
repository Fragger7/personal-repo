import type { IncomingMessage, ServerResponse } from "http";
import fs from "fs";
import path from "path";

interface VercelRequest extends IncomingMessage {
  query: Record<string, string | string[]>;
  body: any;
  method?: string;
  url?: string;
}

interface VercelResponse extends ServerResponse {
  status: (code: number) => VercelResponse;
  json: (body: any) => VercelResponse;
  send: (body: any) => VercelResponse;
}

const GITHUB_REPO = process.env.GITHUB_REPO || "Fragger7/personal-repo";
const GITHUB_TOKEN = process.env.GITHUB_PAT || process.env.GITHUB_TOKEN || process.env.GH_TOKEN || "";
const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || "";
const TELEGRAM_CHAT_ID = process.env.TELEGRAM_CHAT_ID || "";

async function sendTelegramNotification(deal: any, action: string = "dismissed") {
  if (!TELEGRAM_BOT_TOKEN || !TELEGRAM_CHAT_ID) return;
  try {
    const text = (
      `🗑️ <b>Listing ${action.toUpperCase()} via Web Dashboard</b>\n\n` +
      `• <b>Title:</b> ${deal?.title || "Workstation Deal"}\n` +
      `• <b>Price:</b> $${deal?.price ?? 0}\n` +
      `• <b>Score:</b> ${deal?.deal_score ?? 0}/10\n` +
      `• <b>Source:</b> ${(deal?.source || "manual").toUpperCase()}\n` +
      `• <b>URL:</b> ${deal?.url || "N/A"}\n\n` +
      `<i>Synchronized directly to GitHub repository (deals.json).</i>`
    );
    await fetch(`https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        chat_id: TELEGRAM_CHAT_ID,
        text,
        parse_mode: "HTML",
        disable_web_page_preview: true,
      }),
    });
  } catch (err) {
    console.error("[Telegram] Error dispatching message:", err);
  }
}

async function updateFileOnGitHub(filePath: string, updatedDeals: any[], commitMsg: string): Promise<boolean> {
  if (!GITHUB_TOKEN) return false;
  try {
    const url = `https://api.github.com/repos/${GITHUB_REPO}/contents/${filePath}`;
    const getRes = await fetch(url, {
      headers: {
        Authorization: `Bearer ${GITHUB_TOKEN}`,
        Accept: "application/vnd.github.v3+json",
        "User-Agent": "WorkstationDealHunter-Vercel",
      },
    });

    if (!getRes.ok) {
      console.warn(`[GitHub API] Could not fetch ${filePath}:`, getRes.status);
      return false;
    }

    const data: any = await getRes.json();
    const newContent = Buffer.from(JSON.stringify(updatedDeals, null, 2), "utf-8").toString("base64");

    const putRes = await fetch(url, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${GITHUB_TOKEN}`,
        Accept: "application/vnd.github.v3+json",
        "Content-Type": "application/json",
        "User-Agent": "WorkstationDealHunter-Vercel",
      },
      body: JSON.stringify({
        message: commitMsg,
        content: newContent,
        sha: data.sha,
      }),
    });

    return putRes.ok;
  } catch (err) {
    console.error(`[GitHub API] Error updating ${filePath}:`, err);
    return false;
  }
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  // Enable CORS
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    return res.status(200).send("OK");
  }

  // DELETE: Delete deal by ID and commit to GitHub
  if (req.method === "DELETE") {
    let dealId = (req.query?.id as string) || "";
    
    // Parse body if not in query
    if (!dealId && req.body) {
      try {
        const bodyObj = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
        dealId = bodyObj.id || bodyObj.dealId || "";
      } catch (e) {}
    }

    // Extract from URL path if /api/deals/:id or /api/deals/<id>
    if (!dealId && req.url) {
      const match = req.url.match(/\/api\/deals\/(?:\[id\]\/)?([^?]+)/);
      if (match && match[1] !== "deals" && match[1] !== "index") {
        dealId = decodeURIComponent(match[1]);
      }
    }

    if (!dealId) {
      return res.status(400).json({ error: "Missing deal ID parameter" });
    }

    console.log(`[Vercel Serverless] Deleting deal: ${dealId}`);
    let deletedDeal: any = null;
    let currentDeals: any[] = [];

    // Method 1: Fetch live from GitHub if GITHUB_TOKEN is configured
    if (GITHUB_TOKEN) {
      try {
        const url = `https://api.github.com/repos/${GITHUB_REPO}/contents/deals.json`;
        const getRes = await fetch(url, {
          headers: {
            Authorization: `Bearer ${GITHUB_TOKEN}`,
            Accept: "application/vnd.github.v3+json",
            "User-Agent": "WorkstationDealHunter-Vercel",
          },
        });

        if (getRes.ok) {
          const data: any = await getRes.json();
          const contentStr = Buffer.from(data.content, "base64").toString("utf-8");
          currentDeals = JSON.parse(contentStr);
        }
      } catch (e) {
        console.error("[GitHub API] Error fetching deals.json:", e);
      }
    }

    // Method 2: Fallback to local deals.json file if local filesystem exists
    if (!currentDeals.length) {
      const possiblePaths = [
        path.join(process.cwd(), "deals.json"),
        path.join(process.cwd(), "public", "deals.json"),
        path.join(process.cwd(), "ws-deal-hunter", "deals.json"),
      ];
      for (const p of possiblePaths) {
        if (fs.existsSync(p)) {
          try {
            currentDeals = JSON.parse(fs.readFileSync(p, "utf-8"));
            break;
          } catch (e) {}
        }
      }
    }

    deletedDeal = currentDeals.find((d: any) => d.id === dealId || d.url === dealId);
    const updatedDeals = currentDeals.filter((d: any) => d.id !== dealId && d.url !== dealId);

    // Commit changes directly to GitHub repository across root and subfolder paths
    let committedToGithub = false;
    if (GITHUB_TOKEN) {
      const commitMsg = `chore(vercel): delete deal ${dealId} [skip ci]`;
      const p1 = await updateFileOnGitHub("deals.json", updatedDeals, commitMsg);
      const p2 = await updateFileOnGitHub("ws-deal-hunter/deals.json", updatedDeals, commitMsg);
      const p3 = await updateFileOnGitHub("public/deals.json", updatedDeals, commitMsg);
      committedToGithub = p1 || p2 || p3;
    }

    // Dispatch Telegram alert
    if (deletedDeal) {
      await sendTelegramNotification(deletedDeal, "deleted from deals.json");
    }

    return res.status(200).json({
      success: true,
      message: committedToGithub 
        ? "Deal permanently deleted and committed to GitHub deals.json" 
        : "Deal removed from local memory",
      deletedId: dealId,
      committedToGithub,
      remainingCount: updatedDeals.length,
      deal: deletedDeal,
    });
  }

  // GET: Return active deals
  if (req.method === "GET") {
    // If GITHUB_TOKEN is available, try fetching live deals.json from GitHub
    if (GITHUB_TOKEN) {
      try {
        const url = `https://api.github.com/repos/${GITHUB_REPO}/contents/deals.json`;
        const getRes = await fetch(url, {
          headers: {
            Authorization: `Bearer ${GITHUB_TOKEN}`,
            Accept: "application/vnd.github.v3+json",
            "User-Agent": "WorkstationDealHunter-Vercel",
          },
        });
        if (getRes.ok) {
          const data: any = await getRes.json();
          const contentStr = Buffer.from(data.content, "base64").toString("utf-8");
          const deals = JSON.parse(contentStr);
          return res.status(200).json(deals);
        }
      } catch (e) {}
    }

    // Fallback to local deals.json
    const possiblePaths = [
      path.join(process.cwd(), "deals.json"),
      path.join(process.cwd(), "public", "deals.json"),
      path.join(process.cwd(), "ws-deal-hunter", "deals.json"),
    ];
    for (const p of possiblePaths) {
      if (fs.existsSync(p)) {
        try {
          const deals = JSON.parse(fs.readFileSync(p, "utf-8"));
          return res.status(200).json(deals);
        } catch (e) {}
      }
    }

    return res.status(200).json([]);
  }

  return res.status(405).json({ error: "Method not allowed" });
}
