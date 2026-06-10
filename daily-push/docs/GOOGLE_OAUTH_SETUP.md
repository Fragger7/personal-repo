# Google Drive OAuth Integration Guide & Knowledge Base
**Daily Push (v2.0)**

This document serves as the project documentation and workspace knowledge base for configuring, retrieving, and securing your Google Drive backup integration.

---

## 1. Safety and Security: Who Can Access Your Data?

A common question when setting up client-side Google Drive integrations is:
> *If I enter my Client ID on the app or hardcode it in my code, can other people access my workout files?*

### The Short Answer: No. Your data is absolutely safe!

Here is why:
1. **Google OAuth is Identity-Bound**: The **Client ID** is simply a public identifier for the application (it acts like the app's "username" so Google knows which app is asking to connect). It does **not** grant access to physical databases or files.
2. **User Sovereignty**: When someone visits your Daily Push tracking app and clicks **CONNECT DRIVE**, Google prompts **their** device to authorize their **own** Google Account.
3. **Encapsulated AppData Sandbox (Secure scope)**: Daily Push uses the restricted `"https://www.googleapis.com/auth/drive.appdata"` scope. This ensures:
   - Your app can *only* read and write a single private backup file (`workout_data.json`) in a hidden, isolated workspace on Google Drive reserved exclusively for Daily Push.
   - You cannot read other users' Google Drives, and other users cannot access yours, even if you are using the exact same Client ID and application URL.
   - Anyone visiting the app can use their own Google Drive seamlessly to save their own workouts on their own account.

---

## 2. How to Retrieve Your Client ID (`faraze46m3@gmail.com`)

If you ever lose your browser cache or want to retrieve your Client ID from the source, follow these steps to find it:

### Step-by-Step Google Cloud Console Retrieval:
1. **Go to Google Cloud**: Open your browser and navigate to the **[Google Cloud Credentials Console](https://console.cloud.google.com/apis/credentials)**.
2. **Account Sign-In**: Ensure you are signed in with your personal account: **`faraze46m3@gmail.com`**.
3. **Select Your Project**: Click the project selection dropdown in the top-left navigation bar and choose the project associated with your tracker (e.g. `Daily Push` or `personal-repo`).
4. **Locate Credentials**:
   - In the left sidebar, click **Credentials**.
   - Under the **OAuth 2.0 Client IDs** table, look for your Web application credentials (e.g., `"Daily Push Client"`).
5. **Copy the Client ID**: Click the copy icon next to the Client ID. 
   
   For reference, your active Client ID is already configured as:
   `363694431662-rmt5fjbvik4dogimij7papln804ec315.apps.googleusercontent.com`

---

## 3. Configuring the Authorized Origins & URLs

To ensure Google responds correctly to login requests from both your development workspace and your live GitHub Pages production tracker, make sure your OAuth client settings in the Cloud Console have these values added:

### Authorized JavaScript origins:
* `http://localhost:3000` (Local local development/preview)
* `https://ais-dev-eqn2z6ttrtglw2clnh22p4-335559214841.us-east5.run.app` (Your active AI Studio development domain)
* `https://Fragger7.github.io` (Your personal GitHub Pages domain)

### Authorized redirect URIs:
* `http://localhost:3000/oauth-callback.html`
* `https://ais-dev-eqn2z6ttrtglw2clnh22p4-335559214841.us-east5.run.app/oauth-callback.html`
* `https://Fragger7.github.io/personal-repo/daily-push/oauth-callback.html`

---

## 4. Baked-In Design: Fully Automated Hands-Free Sync!

To ensure maximum design elegance and zero friction, we have completely retired and removed the redundant manual Client ID configuration box and Setting gear button from the user interface. 

The application is now **permanently configured** with your locked Client ID (`363694431662-rmt5fjbvik4dogimij7papln804ec315.apps.googleusercontent.com`) directly in the source code as a default fallback (`DEFAULT_GOOGLE_CLIENT_ID` at the top of `src/App.tsx`).

### Benefits of this Baked-In Architecture:
* **True Single-Click Sync**: Simply click **CONNECT DRIVE** or **SYNC NOW**. There are absolutely no text boxes or local cache details to re-enter.
* **Minimalist UI**: Clutter-free dashboard is completely spared from settings toggles, redundant forms, and instructions.
* **Zero Configuration**: Anyone visiting your deployed application can sync their workout records to their secure Google Drive instantly, completely hands-free!

---

## 5. Resolving "App has not completed the Google verification process" (Error 403: access_denied)

When you attempt to click **Connect Drive** and see an error saying the app is currently in testing and can only be accessed by developer-approved testers, your client ID is correct, but your Google Cloud project's Consent Screen is currently in **Testing** mode. 

You can easily bypass or resolve this in one of two ways in your Google Cloud Console:

### Method A: Add your email as an Approved Test User (Recommended)
This keeps your project secure and does not require going through Google's public verification process.
1. Go to the **[Google Cloud Consent Screen Console](https://console.cloud.google.com/apis/credentials/consent)**.
2. Select your project in the top-left dropdown.
3. Under the **Test users** section, click **+ ADD USERS**.
4. Enter your email: **`faraze46m3@gmail.com`** (and any other email accounts you plan to use).
5. Click **Save**.
6. Refresh your Daily Push app and try to log in again. It will now let you proceed!

### Method B: Publish the App to Production
If you want anyone with a Google account to be able to sync their personal backups to Drive under your Client ID:
1. Go to the **[Google Cloud Consent Screen Console](https://console.cloud.google.com/apis/credentials/consent)**.
2. Under **Publishing status**, click **PUBLISH APP**.
3. Confirm the dialog. Google will warn you that you may need to submit for verification, but you can ignore this for personal use; users will simply see an "Advanced -> Go to Daily Push (unsafe)" option to log in.

