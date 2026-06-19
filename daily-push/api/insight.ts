import type { VercelRequest, VercelResponse } from '@vercel/node';
import { GoogleGenAI } from "@google/genai";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method Not Allowed' });
  }

  try {
    const { stats, history } = req.body;
    
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      return res.status(500).json({ error: "GEMINI_API_KEY is not configured on Vercel environment variables." });
    }

    const ai = new GoogleGenAI({ 
      apiKey,
      httpOptions: {
        headers: {
          'User-Agent': 'vercel-backend',
        }
      }
    });

    const prompt = `
      You are a highly knowledgeable fitness coach and sports science AI.
      Here is the user's current workout data for Pushups and Crunches:
      - Total Pushups: ${stats.grossPushups}
      - Total Crunches: ${stats.grossCrunches}
      - Average Pushups per active day: ${stats.avgPushups}
      - Average Crunches per active day: ${stats.avgCrunches}
      - Longest Streak: ${stats.longestStreak || 0} days
      - Recent history (last 5 entries): ${JSON.stringify(history)}
      
      Provide a concise, encouraging, and scientifically sound "Tip of the Day".
      It can cover recent trends (if they are doing great or slowing down), 
      a physiological fact linking their specific volume to health outcomes (e.g. core strength, chest hypertrophy), 
      or a short recommendation for form or complementary recovery.
      Keep it to 2-3 sentences max. Be punchy and professional. Avoid emojis.
    `;

    const response = await ai.models.generateContent({
      model: "gemini-2.5-flash",
      contents: prompt,
    });

    res.status(200).json({ tip: response.text });
  } catch (error: any) {
    console.error("Gemini API Error on Vercel:", error);
    res.status(500).json({ error: error.message || "Failed to generate insight" });
  }
}
