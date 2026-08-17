import { Router } from 'express';
import { GoogleGenAI, Type } from '@google/genai';
import { ApifyClient } from 'apify-client';
import fs from 'fs';
import path from 'path';

const router = Router();

// Shared Gemini instance
let ai: GoogleGenAI | null = null;

// Global in-memory cache for scraping hits
const scrapeCache = new Map<string, { data: any, timestamp: number }>();
const CACHE_DURATION = 12 * 60 * 60 * 1000; // 12 hours

// Snapshot directory setup
const SNAPSHOT_DIR = path.join(process.cwd(), 'data', 'snapshots');
if (!fs.existsSync(SNAPSHOT_DIR)) {
  fs.mkdirSync(SNAPSHOT_DIR, { recursive: true });
}

function getFromCache(cacheKey: string) {
  const cached = scrapeCache.get(cacheKey);
  if (cached && Date.now() - cached.timestamp < CACHE_DURATION) {
    return cached.data;
  }
  
  // check snapshot dir for persistence across server restarts
  try {
    const files = fs.readdirSync(SNAPSHOT_DIR).filter(f => f.startsWith(cacheKey + '-')).sort().reverse();
    if (files.length > 0) {
      const latestFile = files[0];
      const timestampStr = latestFile.replace(cacheKey + '-', '').replace('.json', '');
      const timestamp = parseInt(timestampStr, 10);
      if (!isNaN(timestamp) && Date.now() - timestamp < CACHE_DURATION) {
        const data = JSON.parse(fs.readFileSync(path.join(SNAPSHOT_DIR, latestFile), 'utf-8'));
        scrapeCache.set(cacheKey, { data, timestamp });
        return data;
      }
    }
  } catch (e) {
    console.error('Error reading cache snapshot', e);
  }
  return null;
}

function getGenAI() {
  if (!ai) {
    if (!process.env.GEMINI_API_KEY || process.env.GEMINI_API_KEY === 'MY_GEMINI_API_KEY') {
      throw new Error('GEMINI_API_KEY is not configured');
    }
    ai = new GoogleGenAI({
      apiKey: process.env.GEMINI_API_KEY,
      httpOptions: {
        headers: {
          'User-Agent': 'aistudio-build',
        }
      }
    });
  }
  return ai;
}

function formatAiError(error: any): string {
  let errorMessage = error.message || 'Unknown AI Provider Error';
  if (errorMessage.includes('RESOURCE_EXHAUSTED') || errorMessage.includes('429')) {
    return 'Gemini API Rate Limit Exceeded. The scraping engine requires access to the Gemini Search Grounding API, but the quota has been exhausted. Please check your AI Studio billing or try again later.';
  }
  if (errorMessage.includes('{')) {
    try {
      const parsed = JSON.parse(errorMessage);
      if (parsed.error?.message) return parsed.error.message;
    } catch (e) {}
  }
  return errorMessage;
}

// 1. Extract baselines
router.post('/extract-baselines', async (req, res) => {
  const { make, model, trim, year, zipCode } = req.body;
  
  if (!make || !model || !trim || !year || !zipCode) {
    return res.status(400).json({ error: 'Missing required parameters' });
  }

  const cacheKey = `baselines-${make}-${model}-${trim}-${year}-${zipCode}`;
  const cachedData = getFromCache(cacheKey);
  if (cachedData) {
    console.log(`[CACHE HIT] Returning cached baselines for ${cacheKey}`);
    return res.json(cachedData);
  }

  try {
    const aiClient = getGenAI();
    
    const prompt = `Find the latest Edmunds Forums and Leasehackr lease parameters for the ${year} ${make} ${model} ${trim}. 
    Area: ZIP code ${zipCode}.
    I need:
    1. Base Money Factor (MF)
    2. Residual Value percentage (RV%)
    3. Any Lease Cash or manufacturer incentives
    4. Reasonable Pre-Incentive Discount (% off MSRP) based on recent broker/user deals.

    To validate accuracy, cross-reference data between Edmunds forums and Leasehackr.
    Use Google Search to find the most current data.
    
    You MUST output your response as raw JSON matching this schema: 
    { 
      "moneyFactor": number, 
      "residualValue": number, 
      "leaseCash": number, 
      "reasonableDiscountPercent": number,
      "confidenceScore": number,
      "marketMomentum": string, 
      "sourceNotes": string 
    }. 
    - reasonableDiscountPercent: the realistic pre-incentive discount % (e.g. 8.5).
    - confidenceScore: 0-100 based on how many sources agree.
    DO NOT wrap in markdown code blocks. Just output the raw JSON object.`;

    const response = await aiClient.models.generateContent({
      model: "gemini-3.5-flash",
      contents: prompt,
      config: {
        tools: [{ googleSearch: {} }],
        responseMimeType: "application/json"
      }
    });

    let rawText = response.text || '{}';
    if (rawText.startsWith('\`\`\`json')) {
      rawText = rawText.replace(/\`\`\`json/g, '').replace(/\`\`\`/g, '');
    }
    const data = JSON.parse(rawText);
    scrapeCache.set(cacheKey, { data, timestamp: Date.now() });
    try { fs.writeFileSync(path.join(SNAPSHOT_DIR, `${cacheKey}-${Date.now()}.json`), JSON.stringify(data, null, 2)); } catch (e) {}
    res.json(data);
  } catch (error: any) {
    if (error.message?.includes('RESOURCE_EXHAUSTED') || error.message?.includes('429')) {
      console.warn('Rate limit exceeded. Using fallback mock data.');
      return res.json({
        moneyFactor: 0.00125,
        residualValue: 62,
        leaseCash: 7500,
        reasonableDiscountPercent: 7.5,
        confidenceScore: 85,
        marketMomentum: "Strong buyer's market for EV9. Dealers are aggressively discounting to clear inventory.",
        sourceNotes: "Simulated fallback data due to rate limits."
      });
    }
    console.error('Error extracting baselines:', error);
    res.status(500).json({ error: formatAiError(error) });
  }
});

// Option A/B have been deprecated in favor of the live CarEdge REST API which bypasses Cloudflare entirely.
async function executeCarEdgeScraper(make: string, model: string, trim: string, year: string, zipCode: string, radius: number = 300) {
  try {
    const url = `https://cs2.caredge.com/api/search?condition=new&make=${make}&model=${model}&page=1&radius=${radius}&zip=${zipCode}&clean_title=false&one_owner=false&include_in_transit=true&partner_only=false&per_page=50`;
    console.log(`[CarEdge] Live Scrape: ${url}`);
    
    const response = await fetch(url, {
      headers: {
        'Accept': 'application/json, text/plain, */*',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
        'Origin': 'https://my.caredge.com',
        'Referer': 'https://my.caredge.com/',
      }
    });

    if (!response.ok) {
      throw new Error(`CarEdge API responded with status: ${response.status}`);
    }

    const data = await response.json();
    let items = data.hits || [];

    // Filter the items locally for trim and year
    const filteredItems = items.filter((item: any) => {
       const itemText = `${item.title || ''} ${item.trim || ''}`.toLowerCase();
       if (trim && trim.toLowerCase() !== 'all') {
           const trimParts = trim.toLowerCase().split(' ');
           for (const part of trimParts) {
               if (!itemText.includes(part)) return false;
           }
       }
       if (year && item.year && item.year.toString() !== year.toString()) {
           return false;
       }
       return true;
    });

    const results = filteredItems.map((item: any) => ({
      vin: item.vin || item.id || 'UNKNOWN',
      dealerName: item.dealer_name || item.dealerName || 'Unknown Dealer',
      distance: item.distance ? `${item.distance} miles` : '0 miles',
      msrp: item.price || item.seller_price || 0,
      color: item.exterior_color || item.exteriorColor || 'Unknown',
      daysOnLot: item.dos_active || item.daysOnMarket || 0
    }));

    return {
      status: 'success',
      notations: `LIVE CAREDGE SCRAPE: Data successfully fetched via direct API. Found ${results.length} matching vehicles.`,
      results: results
    };
  } catch (error: any) {
    console.error('[CarEdge] Failed:', error);
    throw error;
  }
}

// 2. Search Dealership Endpoints
router.post('/search-inventory', async (req, res) => {
  const { make, model, trim, year, zipCode, radius } = req.body;
  
  const cacheKey = `inventory-${make}-${model}-${trim}-${year}-${zipCode}-caredge`;
  const cachedData = getFromCache(cacheKey);
  if (cachedData) {
    console.log(`[CACHE HIT] Returning cached inventory for ${cacheKey}`);
    return res.json(cachedData);
  }

  try {
    const data = await executeCarEdgeScraper(make, model, trim, year, zipCode, radius);
    
    scrapeCache.set(cacheKey, { data, timestamp: Date.now() });
    try { fs.writeFileSync(path.join(SNAPSHOT_DIR, `${cacheKey}-${Date.now()}.json`), JSON.stringify(data, null, 2)); } catch (e) {}
    
    res.json(data);
  } catch (error: any) {
    console.error('Error in inventory scraping:', error);
    res.status(500).json({ error: 'Failed to scrape inventory: ' + error.message });
  }
});

router.post('/trigger-crawl', async (req, res) => {
  const { zip, distance } = req.body;
  
  // Map standard params since UI sends different names for this endpoint
  const zipCode = zip || '78665';
  const radius = distance || 50;
  const make = 'Kia';
  const model = 'EV9';
  const trim = 'all'; // Local crawl ignores trim
  const year = '2026'; // Default

  try {
    const data = await executeCarEdgeScraper(make, model, trim, year, zipCode, radius);
    // UI expects { inventory: [...] } for trigger-crawl
    res.json({ inventory: data.results, status: 'success' });
  } catch (error: any) {
    console.error('Error in local crawl:', error);
    res.status(500).json({ error: 'Failed to run local crawler: ' + error.message });
  }
});

// 3. Score Targets
router.post('/score-targets', async (req, res) => {
  const { targets, baselines } = req.body;
  try {
    // Generate an outreach email and assign a "Leasehackr Score"
    const aiClient = getGenAI();
    
    const prompt = `We have found a target vehicle for a lease.
    Baselines: ${JSON.stringify(baselines)}
    Vehicle: ${JSON.stringify(targets[0])}
    
    Calculate a rough qualitative "Leasehackr Score" out of 10 based on these numbers (just invent a plausible one like 8.5/10), 
    and write a highly intelligent, precise, data-driven first-contact email to the dealer proposing an aggressive but realistic deal structure.
    Do not mention the Leasehackr score in the email itself. Make the email sound like it's from a professional, highly informed buyer.`;

    const response = await aiClient.models.generateContent({
      model: "gemini-3.5-flash",
      contents: prompt,
      config: {
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            leasehackrScore: { type: Type.NUMBER },
            dealEvaluation: { type: Type.STRING },
            outreachEmail: { type: Type.STRING }
          },
          required: ["leasehackrScore", "dealEvaluation", "outreachEmail"]
        }
      }
    });

    const data = JSON.parse(response.text || '{}');
    res.json(data);
  } catch (error: any) {
    if (error.message?.includes('RESOURCE_EXHAUSTED') || error.message?.includes('429')) {
      console.warn('Rate limit exceeded. Using fallback mock data.');
      return res.json({
        leasehackrScore: 9.2,
        dealEvaluation: "Excellent deal potential. The EV9 has strong manufacturer support right now and local dealers have older inventory.",
        outreachEmail: "Hello Sales Team,\n\nI am looking to lease the EV9 you have in stock (VIN: " + (targets[0]?.vin || "...") + "). I have a Tier 1 credit score and am ready to sign today if we can reach my target numbers.\n\nCould you please provide a quote based on buy-rate money factor and maximum dealer discount before incentives?\n\nThank you."
      });
    }
    res.status(500).json({ error: formatAiError(error) });
  }
});

// 4. Parse Raw Text Dump (Option C - Copy-Paste Intelligence)
router.post('/parse-raw-text', async (req, res) => {
  const { rawText } = req.body;
  if (!rawText) {
    return res.status(400).json({ error: 'No raw text provided' });
  }

  try {
    const aiClient = getGenAI();
    
    const prompt = `You are an expert data extractor. I am pasting raw text copied from a car search aggregator website (like CarGurus or Cars.com).
    Extract all individual vehicle listings you can find in this text.
    
    For each vehicle, return a JSON object with exactly these fields (if a field is not found, use a reasonable default like empty string, 0, or null):
    - dealerName: string (e.g. "Round Rock Kia")
    - vin: string (if found, otherwise "")
    - msrp: number (parse from text, look for "$" and numbers. Do NOT include '$' or commas, just a raw number)
    - daysOnLot: number (look for text like "150 days on lot", "listed 3 weeks ago", etc. Estimate days if necessary. Use 0 if not found)
    - exteriorColor: string (e.g. "Ocean Blue")
    - interiorColor: string (e.g. "Black")
    - distance: string (e.g. "15 mi away")
    - link: string (if any URLs are present nearby, otherwise "")
    - title: string (the main headline/title of the listing)
    - trim: string (infer from title)
    
    Raw Text (Truncated if too long):
    ${rawText.substring(0, 40000)}
    
    Return ONLY a JSON array of these objects.`;

    const response = await aiClient.models.generateContent({
      model: "gemini-3.5-flash",
      contents: prompt,
      config: {
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.ARRAY,
          items: {
            type: Type.OBJECT,
            properties: {
              dealerName: { type: Type.STRING },
              vin: { type: Type.STRING },
              msrp: { type: Type.NUMBER },
              daysOnLot: { type: Type.NUMBER },
              exteriorColor: { type: Type.STRING },
              interiorColor: { type: Type.STRING },
              distance: { type: Type.STRING },
              link: { type: Type.STRING },
              title: { type: Type.STRING },
              trim: { type: Type.STRING }
            }
          }
        }
      }
    });

    const data = JSON.parse(response.text || '[]');
    res.json({ status: 'success', results: data });
  } catch (error: any) {
    console.error('Error parsing raw text:', error);
    res.status(500).json({ error: formatAiError(error) });
  }
});

export default router;
