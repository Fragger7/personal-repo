import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI } from "@google/genai";

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // API endpoints
  app.post("/api/insight", async (req, res) => {
    try {
      const { stats, history } = req.body;
      
      const apiKey = process.env.GEMINI_API_KEY;
      if (!apiKey) {
        return res.status(500).json({ error: "GEMINI_API_KEY is not configured on the server." });
      }

      const ai = new GoogleGenAI({ 
        apiKey,
        httpOptions: {
          headers: {
            'User-Agent': 'aistudio-build',
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
        - Longest Streak: ${stats.longestStreak} days
        - Recent history (last 5 entries): ${JSON.stringify(history)}
        
        Provide a concise, encouraging, and scientifically sound "Tip of the Day".
        It can cover recent trends (if they are doing great or slowing down), 
        a physiological fact linking their specific volume to health outcomes (e.g. core strength, chest hypertrophy), 
        or a short recommendation for form or complementary recovery.
        Keep it to 2-3 sentences max. Be punchy and professional. Avoid emojis.
      `;

      const response = await ai.models.generateContent({
        model: "gemini-3.5-flash",
        contents: prompt,
      });

      res.json({ tip: response.text });
    } catch (error: any) {
      console.error("Gemini API Error:", error);
      res.status(500).json({ error: error.message || "Failed to generate insight" });
    }
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
