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
var import_vite = require("vite");
var import_genai = require("@google/genai");
async function startServer() {
  const app = (0, import_express.default)();
  const PORT = 3e3;
  app.use(import_express.default.json());
  app.post("/api/insight", async (req, res) => {
    try {
      const { stats, history } = req.body;
      const apiKey = process.env.GEMINI_API_KEY;
      if (!apiKey) {
        return res.status(500).json({ error: "GEMINI_API_KEY is not configured on the server." });
      }
      const ai = new import_genai.GoogleGenAI({
        apiKey,
        httpOptions: {
          headers: {
            "User-Agent": "aistudio-build"
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
        contents: prompt
      });
      res.json({ tip: response.text });
    } catch (error) {
      console.error("Gemini API Error:", error);
      res.status(500).json({ error: error.message || "Failed to generate insight" });
    }
  });
  if (process.env.NODE_ENV !== "production") {
    const vite = await (0, import_vite.createServer)({
      server: { middlewareMode: true },
      appType: "spa"
    });
    app.use(vite.middlewares);
  } else {
    const distPath = import_path.default.join(process.cwd(), "dist");
    app.use(import_express.default.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(import_path.default.join(distPath, "index.html"));
    });
  }
  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
  });
}
startServer();
//# sourceMappingURL=server.cjs.map
