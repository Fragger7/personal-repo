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
      model: "gemini-3.5-flash",
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
    res.status(500).json({ error: error.message });
  }
});

// 2. Search Dealership Endpoints (Mock representation for PoC)
router.post('/search-inventory', async (req, res) => {
  const { make, model, trim, zipCode, radius } = req.body;
  // In a full implementation, this uses custom scraping to hit dealer websites.
  // For now, we simulate finding units.
  
  try {
    res.json({
      status: 'success',
      results: [
        {
          vin: "KNDCE3LXXXXXXXXX1",
          dealerName: "Round Rock Kia",
          distance: "5.2 miles",
          msrp: 75395,
          color: "Ocean Blue",
          daysOnLot: 45
        },
        {
          vin: "KNDCE3LXXXXXXXXX2",
          dealerName: "Capitol Auto",
          distance: "18.4 miles",
          msrp: 76100,
          color: "Snow White Pearl",
          daysOnLot: 12
        }
      ],
      notations: "Dealership scraping phase complete."
    });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
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
    res.status(500).json({ error: error.message });
  }
});

export default router;
