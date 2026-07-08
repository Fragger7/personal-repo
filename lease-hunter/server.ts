import express from 'express';
import path from 'path';
import fs from 'fs';
import { exec } from 'child_process';
import dotenv from 'dotenv';
import { createServer as createViteServer } from 'vite';
import scrapingRouter from './src/server/scraping.js';

dotenv.config();

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // Mount API routes
  app.use('/api/scrape', scrapingRouter);

  // GET API Status & Setup checks
  app.get('/api/status', (req, res) => {
    res.json({
      gitHubTokenConfigured: !!process.env.GITHUB_TOKEN && process.env.GITHUB_TOKEN !== 'MY_GITHUB_TOKEN',
      geminiApiKeyConfigured: !!process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY !== 'MY_GEMINI_API_KEY',
      appUrl: process.env.APP_URL || 'Not specified',
      environment: process.env.NODE_ENV || 'development',
      time: new Date().toISOString(),
    });
  });

  // GET Files List and Status
  app.get('/api/files', (req, res) => {
    const filesToExpose = [
      'app.py',
      'requirements.txt',
      'LEASE_HUNTER.md',
      '.agents/AGENTS.md',
      'git_push.cjs',
      'git_push.ps1',
      'PROMPT_MANIFEST.txt',
      'run.bat'
    ];

    const filesData = filesToExpose.map(fileName => {
      const filePath = path.join(process.cwd(), fileName);
      if (fs.existsSync(filePath)) {
        const stats = fs.statSync(filePath);
        return {
          name: fileName,
          exists: true,
          size: stats.size,
          mtime: stats.mtime,
        };
      } else {
        return {
          name: fileName,
          exists: false,
          size: 0,
          mtime: null,
        };
      }
    });

    res.json(filesData);
  });

  // GET Single File Content
  app.get('/api/files/content', (req, res) => {
    const { filename } = req.query;
    if (typeof filename !== 'string') {
      res.status(400).json({ error: 'filename query parameter is required' });
      return;
    }

    const safeFilename = path.normalize(filename).replace(/^(\.\.(\/|\\|$))+/, '');
    const filePath = path.join(process.cwd(), safeFilename);

    if (!fs.existsSync(filePath)) {
      res.status(404).json({ error: `File not found: ${filename}` });
      return;
    }

    try {
      const content = fs.readFileSync(filePath, 'utf-8');
      res.json({ filename: safeFilename, content });
    } catch (err: any) {
      res.status(500).json({ error: err.message });
    }
  });

  // POST Save File Content
  app.post('/api/files/save', (req, res) => {
    const { filename, content } = req.body;
    if (!filename || typeof content !== 'string') {
      res.status(400).json({ error: 'filename and content body parameters are required' });
      return;
    }

    const safeFilename = path.normalize(filename).replace(/^(\.\.(\/|\\|$))+/, '');
    const filePath = path.join(process.cwd(), safeFilename);

    // Only allow editing files in our target workspace list
    const allowedFiles = [
      'app.py',
      'requirements.txt',
      'LEASE_HUNTER.md',
      '.agents/AGENTS.md',
      'git_push.cjs',
      'git_push.ps1',
      'PROMPT_MANIFEST.txt',
      'run.bat'
    ];

    if (!allowedFiles.includes(safeFilename)) {
      res.status(403).json({ error: 'File modification restricted to workspace files only.' });
      return;
    }

    try {
      // Ensure folder exists (e.g. .agents/)
      const dir = path.dirname(filePath);
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }

      fs.writeFileSync(filePath, content, 'utf-8');
      res.json({ success: true, message: `File ${safeFilename} saved successfully.` });
    } catch (err: any) {
      res.status(500).json({ error: err.message });
    }
  });

  // POST Tax Simulation Engine (Ported Logic from app.py)
  app.post('/api/tax-simulate', (req, res) => {
    const { zipCode } = req.body;
    if (!zipCode || typeof zipCode !== 'string') {
      res.status(400).json({ error: 'zipCode is required' });
      return;
    }

    let taxType = 'TAX_ON_PAYMENT';
    let defaultRate = 0.0775;
    let showsTaxCredits = false;

    if (zipCode.match(/^(75|76|77|78|79)/)) { // Texas Focus
      taxType = 'TAX_ON_FULL_PRICE';
      defaultRate = 0.0625;
      showsTaxCredits = true;
    } else if (zipCode.match(/^(10|11|12|13|14)/)) { // New York Focus
      taxType = 'TAX_ON_TOTAL_PAYMENTS';
      defaultRate = 0.0887;
      showsTaxCredits = false;
    }

    res.json({
      zipCode,
      taxType,
      defaultRate,
      showsTaxCredits,
      description: taxType === 'TAX_ON_FULL_PRICE' 
        ? 'Taxes calculated on full pre-incentive vehicle sale price upfront. Subject to lender sales tax credits.'
        : taxType === 'TAX_ON_TOTAL_PAYMENTS'
        ? 'Taxes calculated upfront on sum of all lease payments.'
        : 'Base payment and interest taxed per month (Standard rule).'
    });
  });

  // CRM JSON Endpoints
  const crmFilePath = path.join(process.cwd(), 'data', 'crm.json');

  app.get('/api/crm/leads', (req, res) => {
    try {
      if (!fs.existsSync(crmFilePath)) {
        return res.json([]);
      }
      const data = fs.readFileSync(crmFilePath, 'utf-8');
      res.json(JSON.parse(data));
    } catch (e: any) {
      res.status(500).json({ error: e.message });
    }
  });

  app.post('/api/crm/leads', (req, res) => {
    try {
      const { lead } = req.body;
      let leads = [];
      if (fs.existsSync(crmFilePath)) {
        leads = JSON.parse(fs.readFileSync(crmFilePath, 'utf-8'));
      } else {
        const dir = path.dirname(crmFilePath);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
      }

      const existingIndex = leads.findIndex((l: any) => l.vin === lead.vin);
      if (existingIndex >= 0) {
        leads[existingIndex] = { ...leads[existingIndex], ...lead, updatedAt: new Date().toISOString() };
      } else {
        leads.push({ ...lead, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() });
      }

      fs.writeFileSync(crmFilePath, JSON.stringify(leads, null, 2), 'utf-8');
      res.json({ success: true, leads });
    } catch (e: any) {
      res.status(500).json({ error: e.message });
    }
  });

  // POST Git Push Flow Trigger
  app.post('/api/git-push', (req, res) => {
    const { commitMsg } = req.body;
    const cleanCommitMsg = commitMsg ? commitMsg.replace(/"/g, '\\"') : 'Update Lease Hunter via AI Studio Control Panel';

    if (!process.env.GITHUB_TOKEN || process.env.GITHUB_TOKEN === 'MY_GITHUB_TOKEN') {
      res.status(400).json({ 
        error: 'GITHUB_TOKEN environment variable is not defined or configured in AI Studio. Please see the Setup guide on the panel for instructions.' 
      });
      return;
    }

    res.setHeader('Content-Type', 'text/plain');
    res.setHeader('Transfer-Encoding', 'chunked');

    res.write('>>> INITIALIZING SECURE GIT ISOLATED DEPLOYMENT PIPELINE...\n');
    res.write(`>>> Executing: node git_push.cjs "${cleanCommitMsg}"\n\n`);

    const child = exec(`node git_push.cjs "${cleanCommitMsg}"`, {
      env: { ...process.env }
    });

    child.stdout?.on('data', (data) => {
      res.write(data.toString());
    });

    child.stderr?.on('data', (data) => {
      res.write(`[ERR] ${data.toString()}`);
    });

    child.on('close', (code) => {
      if (code === 0) {
        res.write('\n>>> git_push.cjs completed successfully. Repository refreshed on main branch.\n');
      } else {
        res.write(`\n>>> git_push.cjs failed with exit code ${code}.\n`);
      }
      res.end();
    });
  });

  // Mount Vite middleware for dev or serve static files in prod
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
    
    // Explicit fallback for dev
    app.use('*', async (req, res, next) => {
      try {
        const url = req.originalUrl;
        let template = fs.readFileSync(path.resolve('index.html'), 'utf-8');
        template = await vite.transformIndexHtml(url, template);
        res.status(200).set({ 'Content-Type': 'text/html' }).end(template);
      } catch (e: any) {
        vite.ssrFixStacktrace(e);
        next(e);
      }
    });
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on port ${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
});
