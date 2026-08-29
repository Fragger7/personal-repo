# Daily Push - Project Context & Agent Instructions

## Project Overview
**Daily Push** is a fitness tracking PWA focused on tracking daily metrics for exercises like Pushups and Crunches. It features personalized, AI-powered health insights and transparent cross-device data synchronization via Google Drive.

## Architecture
- **Frontend**: React 18, Vite, Tailwind CSS. Focuses on a clean, motivational UI.
- **Backend**: **Vercel Serverless Functions**. API logic (like the Gemini AI insights) lives in the `/api` directory (e.g., `/api/insight.ts`) utilizing `@vercel/node`.
- **Hosting & Deployment**: Hosted on Vercel at `https://personal-repo-xi-two.vercel.app/`. 
- **Repository Pattern**: We use a `scripts/git_deploy.ts` script to compile the frontend and copy crucial source files (`/src`, `/public`, `/api`, `server.ts`, etc.) into a `daily-push` subdirectory within the `Fragger7/personal-repo` GitHub repository. Vercel automatically deploys based on those commits to GitHub.

## Core Integrations & Workflows
1. **Gemini API (AI Insights)**: 
   - Uses the `@google/genai` SDK.
   - Evaluates a user's recent workout stats—including gross totals, averages, and the newly added **Current and Longest Streaks**—to generate a tailored "Tip of the Day".
   - Executed securely server-side on Vercel using the `GEMINI_API_KEY` environment variable.
2. **Google Drive Sync (Robust Backups & State)**:
   - Uses Google OAuth 2.0 to securely store and retrieve backup data payloads to the user's personal Google Drive.
   - Tied to `VITE_GOOGLE_CLIENT_ID`.
   - **Crucial Note**: The Google Cloud Console OAuth credentials MUST include the current Vercel deployment URL in both `Authorized JavaScript origins` and `Authorized redirect URIs`.

## Core Features & KPIs
- **Consecutive Streaks**: App computes `Current Streak` (active consecutive days rounded to local 24h) and `Longest Streak`. A single missed day drops the current streak back to 0. 
- **Performance Metrics**: Calculates 'Gross Pushups/Crunches', 'Average Pushups/Crunches', and 'Max Pushups/Crunches in a Set & Day'.

## Environment Variables
When introducing new features, ensure that environment variables are properly partitioned:
- **Server-side (Vercel)**: `GEMINI_API_KEY`. These live securely in Vercel's Project Settings > Environment Variables. Do NOT prefix with `VITE_`.
- **Client-side (Vite)**: `VITE_GOOGLE_CLIENT_ID`, `APP_URL`. These can be safely bundled into the client build.

## Developer Instructions (For AI Agent)
- **Adding Backend Features**: When building new backend logic, write them as Vercel Serverless Functions inside the `/api/` directory so they map automatically to Vercel routes.
- **Code Continuity**: If new root folders or critical configuration files are added to the workspace, you **MUST** update `scripts/git_deploy.ts` to ensure those files are pushed to GitHub so Vercel can build them.
- **Preview vs Production**: The local AI Studio environment can run using `server.ts` (Express proxy) during development, but the production environment exclusively relies on Vercel's Edge/Serverless runtime executing `/api/` endpoints. Ensure parity between local proxy routes and Vercel serverless routes.
- **Design Philosophy**: Stick to minimal, single-purpose components. Avoid visual clutter; maintain the distinct "slate" styling, sleek typography, and robust visual feedback.

## Backlog & Future Improvements
- **Model Downgrade Reversal**: We temporarily downgraded the AI Insight model to `gemini-2.5-flash` due to quota limitations with the Pro and more advanced preview models. We should revisit this to upgrade back to a stronger model when quota allows.
- **Future UI Guidelines & Backups**: We have successfully secured a complete baseline backup of the v1.0 stable application state (including the complete Light/Dark mode implementation) to GitHub. We are now cleared to begin the "v2.0 UI Evolution" (fluid animations, modern bento grids, glassmorphism).
- **Theme & UX Backlog Notes**: The current static theme-selection dropdown is a functional placeholder. Future iterations should experiment with subtle, deeply integrated placement (such as contextual color pickers rather than literal "Theme" dropdowns) following world-class UI/UX patterns. The default "Emerald" theme is optimal, and the "Blue" theme serves well. The draft "Crimson" and "Product Red" variants appear amateur—either replace them by adopting documented, high-quality brand color guidelines, or scrap them entirely if they cannot meet the professional aesthetic standard.

