import { Router } from 'express';
import { GoogleGenAI, Type } from '@google/genai';

const router = Router();

// Shared Gemini instance
let ai: GoogleGenAI | null = null;
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

  try {
    const aiClient = getGenAI();
    
    const prompt = `Find the latest Edmunds Forums and Leasehackr lease parameters for the ${year} ${make} ${model} ${trim}. 
    Area: ZIP code ${zipCode}.
    I need the base Money Factor (MF), the Residual Value percentage (RV%), and any Lease Cash or manufacturer incentives.
    Use Google Search to find the most current data.`;

    const response = await aiClient.models.generateContent({
      model: "gemini-2.5-flash",
      contents: prompt,
      config: {
        tools: [{ googleSearch: {} }],
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            moneyFactor: { type: Type.NUMBER, description: "The base Money Factor, e.g. 0.00125" },
            residualValue: { type: Type.NUMBER, description: "The Residual Value as a percentage, e.g. 62" },
            leaseCash: { type: Type.NUMBER, description: "Total lease cash or manufacturer incentives, e.g. 7500" },
            marketMomentum: { type: Type.STRING, description: "Brief summary of market momentum or ease of getting deals on this car." },
            sourceNotes: { type: Type.STRING, description: "Summary of where this data was found (e.g. Edmunds Forum latest post date)." },
          },
          required: ["moneyFactor", "residualValue", "leaseCash", "marketMomentum", "sourceNotes"]
        }
      }
    });

    const data = JSON.parse(response.text || '{}');
    res.json(data);
  } catch (error: any) {
    console.error('Error extracting baselines:', error);
    res.status(500).json({ error: formatAiError(error) });
  }
});

// 2. Search Dealership Endpoints (Powered by Gemini Search Grounding)
router.post('/search-inventory', async (req, res) => {
  const { make, model, trim, zipCode, radius } = req.body;
  
  try {
    const aiClient = getGenAI();
    
    const prompt = `Use Google Search to find 3 REAL, CURRENT ${make} ${model} vehicles for sale at dealerships near ZIP code ${zipCode}.
    Focus specifically on the ${trim} trim if possible. 
    You must return actual vehicles found on dealership websites (e.g. Kia of South Austin, Round Rock Kia).
    Find the VIN, the specific Dealer's Name, the listed MSRP or Price, the exterior color, and estimate how many days it might have been on the lot (guess if not listed, based on typical inventory age).
    DO NOT MAKE UP VINS. Provide real data from your search results.`;

    const response = await aiClient.models.generateContent({
      model: "gemini-2.5-flash",
      contents: prompt,
      config: {
        tools: [{ googleSearch: {} }],
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            status: { type: Type.STRING },
            notations: { type: Type.STRING },
            results: {
              type: Type.ARRAY,
              items: {
                type: Type.OBJECT,
                properties: {
                  vin: { type: Type.STRING },
                  dealerName: { type: Type.STRING },
                  distance: { type: Type.STRING },
                  msrp: { type: Type.NUMBER },
                  color: { type: Type.STRING },
                  daysOnLot: { type: Type.NUMBER }
                },
                required: ["vin", "dealerName", "msrp", "color"]
              }
            }
          },
          required: ["status", "results", "notations"]
        }
      }
    });

    const data = JSON.parse(response.text || '{}');
    res.json(data);
  } catch (error: any) {
    console.error('Error searching inventory:', error);
    res.status(500).json({ error: formatAiError(error) });
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
      model: "gemini-2.5-flash",
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
    res.status(500).json({ error: formatAiError(error) });
  }
});

export default router;
