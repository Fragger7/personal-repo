/**
 * VEHICLE TRACKER — Home Edition (Windows / Node.js)
 * ------------------------------------------------
 * Same tracker that ran on Cloudflare Workers, ported to run on a
 * home PC. The whole point of the move: requests now come from your
 * RESIDENTIAL IP with normal consumer network characteristics — the
 * thing the Cloudflare version structurally couldn't provide, and the
 * reason all three sources were 403-blocking it.
 *
 * Checks Tesla / CarMax / Carvana for listings matching the vehicle you
 * configure (make / model / trim / year range in config.json),
 * remembers every VIN it has seen (seen-vins.json, saved next to this
 * file), and sends a Pushover Emergency alert (re-rings every 60s for
 * up to an hour until acknowledged) the moment a NEW one appears.
 *
 * Files in this folder:
 *   tracker.js     — this script
 *   config.json    — your Pushover keys + tuning (edit this one)
 *   seen-vins.json — auto-created; the tracker's memory
 *   state.json     — auto-created; backoff timers + heartbeat clock
 *
 * Run it:        node tracker.js
 * Stop it:       Ctrl+C in the window it's running in
 * Test push:     node tracker.js test-push
 * Reset memory:  delete seen-vins.json (next run re-seeds silently)
 *
 * See the chat instructions for making it start automatically on
 * boot via Windows Task Scheduler.
 */

const fs = require("fs");
const path = require("path");

// ---------- Config ----------
const CONFIG_PATH = path.join(__dirname, "config.json");
const SEEN_PATH = path.join(__dirname, "seen-vins.json");
const STATE_PATH = path.join(__dirname, "state.json");

let config;
try {
  config = JSON.parse(fs.readFileSync(CONFIG_PATH, "utf8"));
} catch (e) {
  console.error(`Could not read config.json: ${e.message}`);
  process.exit(1);
}

if (
  !config.PUSHOVER_USER ||
  !config.PUSHOVER_TOKEN ||
  config.PUSHOVER_USER.includes("REPLACE-WITH") ||
  config.PUSHOVER_TOKEN.includes("REPLACE-WITH")
) {
  console.error(
    "Edit config.json first: PUSHOVER_USER and PUSHOVER_TOKEN are still placeholders."
  );
  process.exit(1);
}

const CHECK_INTERVAL_MS = (config.CHECK_INTERVAL_SECONDS || 60) * 1000;
// ---------- Vehicle being searched (configurable) ----------
// Backwards compatible with the older flat YEAR_MIN setting.
// If no VEHICLE block is present (older config files), fall back to the
// original target exactly — including the Plaid trim — so an existing setup
// keeps behaving identically after upgrading rather than silently widening to
// every Model S.
const LEGACY_DEFAULT = { make: "Tesla", model: "Model S", trim: "Plaid" };
const VEHICLE = config.VEHICLE || LEGACY_DEFAULT;
const YEAR_MIN = VEHICLE.yearMin ?? config.YEAR_MIN ?? 2026;
const YEAR_MAX = VEHICLE.yearMax ?? null;
const MAKE = VEHICLE.make || "Tesla";
const MODEL = VEHICLE.model || "Model S";
// Explicit null means "any trim"; absent means use the default.
const TRIM = VEHICLE.trim !== undefined ? VEHICLE.trim : "Plaid";
const SOURCES = Object.assign({ tesla: true, carmax: true, carvana: true }, config.SOURCES || {});

// The Tesla source can only ever search Teslas. Disable it automatically for
// any other make rather than firing pointless requests at tesla.com.
const TESLA_SOURCE_APPLICABLE = MAKE.toLowerCase() === "tesla";

// TeslaTracker category codes, keyed by model|trim. Only Tesla vehicles with a
// known code can use the fallback; anything else skips it cleanly.
const TESLATRACKER_CATEGORIES = {
  "model-s|plaid": "MS_PLAID",
  "model-s|": "MS",
  "model-x|plaid": "MX_PLAID",
  "model-x|": "MX",
  "model-3|": "M3",
  "model-y|": "MY",
};

// Human-readable label for the configured vehicle, used in every notification
// so alerts say what you're actually searching for rather than a hardcoded name.
const VEHICLE_LABEL = `${MAKE} ${MODEL}${TRIM ? " " + TRIM : ""}`.trim();

function slug(s) {
  return String(s || "").trim().toLowerCase().replace(/\s+/g, "-");
}

// Does a listing match the configured vehicle? Trim matching FAILS OPEN: an
// unrecognisable or missing trim surfaces the car rather than hiding it, since
// a false positive costs a glance and a false negative costs the car.
function matchesVehicle(year, trimName) {
  const y = Number(year);
  if (!Number.isFinite(y)) return false;
  if (y < YEAR_MIN) return false;
  if (YEAR_MAX && y > YEAR_MAX) return false;
  if (!TRIM) return true;
  const t = String(trimName || "").trim();
  if (t === "") return true; // unknown trim -> fail open
  return new RegExp(TRIM.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i").test(t);
}
const HEARTBEAT_INTERVAL_MS = 24 * 60 * 60 * 1000;
// Backoff calibration note: across 13+ hours from a residential IP we observed
// ZERO genuine rate limiting. CarMax ran ~390 consecutive checks at 2-min
// intervals with a 100% success rate. Every 403 traced to a real bug (Tesla:
// retired v1 endpoint; Carvana: TLS fingerprinting), not frequency. So backoff
// is a safety net now, not the primary defense:
//   429 (real rate limit) -> escalating backoff
//   403/5xx (likely bug)  -> short flat pause; waiting won't fix a wrong URL
const BACKOFF_START_MS = 60 * 1000; // 1 minute
const BACKOFF_MAX_MS = 15 * 60 * 1000; // cap at 15 min (was 1 hr - too costly)
const SOFT_FAIL_BACKOFF_MS = 2 * 60 * 1000; // flat pause for non-429 failures
const ALERT_AFTER_CONSECUTIVE_FAILURES = 5; // then push a warning
const ALERT_AFTER_DOWN_MS = 30 * 60 * 1000; // ...or 30 min down, whichever first

// ---------- Tiny persistent state helpers ----------
function loadJson(p, fallback) {
  try {
    return JSON.parse(fs.readFileSync(p, "utf8"));
  } catch {
    return fallback;
  }
}
function saveJson(p, data) {
  fs.writeFileSync(p, JSON.stringify(data, null, 2));
}

let state = loadJson(STATE_PATH, {
  backoff: {},
  lastHeartbeat: 0,
  checksRun: 0,
  checksFailed: 0,
  lastSourceCounts: {},
});

// ---------- Backoff (same logic as the Worker version) ----------
// Per-source pacing. teslatracker polls each source roughly HOURLY (their
// firstSeenAt timestamps cluster at ~:10 past the hour). We can be far faster
// and still be far quieter than continuous polling. Rates are matched to what
// each source has actually demonstrated:
//   carmax  - 2 min: ~390 consecutive successes observed, plain fetch, no browser
//   carvana - 5 min: works, but drives a real browser + a detail-page check
//   tesla   - 15 min: most sensitive to automation; still 4x faster than
//             teslatracker's hourly cadence
function isDueToRun(source) {
  // Hot-watch override: when a matching vehicle is sitting on another buyer's
  // checkout hold, the interval is irrelevant — the moment that hold lapses the
  // car is live again and gone within seconds. Check every tick until resolved.
  if (source === "carvana" && state.hotWatch && Object.keys(state.hotWatch).length) {
    return true;
  }
  const mins = (config.SOURCE_INTERVAL_MINUTES || {})[source];
  if (!mins) return true; // no per-source setting -> run every tick
  state.lastRun = state.lastRun || {};
  const last = state.lastRun[source] || 0;
  const dueAt = last + mins * 60 * 1000;
  if (Date.now() < dueAt) {
    return false;
  }
  state.lastRun[source] = Date.now();
  saveJson(STATE_PATH, state);
  return true;
}

function isBackedOff(source) {
  const b = state.backoff[source];
  if (b && Date.now() < b.until) {
    console.log(
      `${source}: backing off until ${new Date(b.until).toISOString()} (skipping)`
    );
    return true;
  }
  return false;
}

function recordResult(source, res) {
  state.failStreak = state.failStreak || {};
  state.alerted = state.alerted || {};

  if (res.ok) {
    delete state.backoff[source];
    if (state.failStreak[source]) {
      console.log(`${source}: recovered after ${state.failStreak[source]} failure(s)`);
      delete state.failStreak[source];
      delete state.alerted[source];
      if (state.firstFailureAt) delete state.firstFailureAt[source];
    }
    saveJson(STATE_PATH, state);
    return;
  }

  state.failStreak[source] = (state.failStreak[source] || 0) + 1;
  const streak = state.failStreak[source];

  if (res.status === 429) {
    // Genuine rate limit — escalate.
    const prevMs = state.backoff[source]?.ms || BACKOFF_START_MS / 2;
    const nextMs = Math.min(prevMs * 2, BACKOFF_MAX_MS);
    state.backoff[source] = { ms: nextMs, until: Date.now() + nextMs };
    console.error(
      `${source}: HTTP 429 (rate limited) — backing off ${Math.round(nextMs / 1000)}s`
    );
  } else {
    // 403/404/5xx: usually a bug or a hard block that waiting won't cure.
    // Pause briefly so we're not hammering, but don't escalate into hours blind.
    state.backoff[source] = {
      ms: SOFT_FAIL_BACKOFF_MS,
      until: Date.now() + SOFT_FAIL_BACKOFF_MS,
    };
    console.error(
      `${source}: HTTP ${res.status} (failure #${streak}) — pausing ${SOFT_FAIL_BACKOFF_MS / 1000}s. ` +
        `Repeated 403s usually mean a wrong endpoint or a block, not frequency.`
    );
  }

  // Don't fail silently for hours — tell the user.
  // Alert on ELAPSED TIME as well as count. A pure count threshold is
  // interval-dependent: at Tesla's 20-minute cadence, five consecutive failures
  // takes ~100 minutes before you hear about it. Whichever trigger comes first
  // wins, so a fast-cycling source still alerts on count and a slow one alerts
  // on the clock.
  state.firstFailureAt = state.firstFailureAt || {};
  if (!state.firstFailureAt[source]) state.firstFailureAt[source] = Date.now();
  const downForMs = Date.now() - state.firstFailureAt[source];
  const downTooLong = downForMs >= ALERT_AFTER_DOWN_MS;

  if ((streak >= ALERT_AFTER_CONSECUTIVE_FAILURES || downTooLong) && !state.alerted[source]) {
    state.alerted[source] = true;
    const mins = Math.round(downForMs / 60000);
    push(
      "Vehicle tracker: source failing",
      `${source} has failed ${streak} check(s) in a row over ~${mins} minute(s) (last: HTTP ${res.status}). Other sources are still running.`
    ).catch(() => {});
  }

  saveJson(STATE_PATH, state);
}

// ---------- Browser-like headers (page navigation style) ----------
const PAGE_HEADERS = {
  "User-Agent":
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
  Accept:
    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
  "Accept-Language": "en-US,en;q=0.9",
  "sec-ch-ua": '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
  "sec-ch-ua-mobile": "?0",
  "sec-ch-ua-platform": '"Windows"',
  "Sec-Fetch-Dest": "document",
  "Sec-Fetch-Mode": "navigate",
  "Sec-Fetch-Site": "none",
  "Sec-Fetch-User": "?1",
  "Upgrade-Insecure-Requests": "1",
};

// ============================================================
// SOURCE FETCHERS (ported unchanged apart from storage handling)
// ============================================================

// Browser strategy note: Tesla blocks even a real-Chrome automated session when
// it launches with a FRESH profile — the page load itself returns Access Denied.
// A normal Chrome has cookies and history from prior visits; a throwaway profile
// has none, and that difference is enough. So we use a PERSISTENT profile stored
// beside this script: cookies survive between runs, and the session gradually
// looks like a normal returning visitor.
//
// One-time setup if Tesla still blocks:  node tracker.js warmup
// That opens a visible browser using this same profile so you can visit
// tesla.com yourself. Whatever cookies that establishes are reused afterwards.
// Browser launch. NOTE: an earlier version used launchPersistentContext with a
// shared profile directory. That was a mistake: it gave no benefit against
// Akamai (which fingerprints the browser, not the profile's age) and the
// profile-directory lock made launches fail with "Opening in existing browser
// session" whenever another Chrome held it — which broke Carvana, a source that
// was working fine. Plain launch() creates an isolated throwaway context per
// run: no lock contention, no interference with your everyday Chrome.
let _browser = null;
let _usingCdp = false;
let _browserPromise = null; // in-flight guard

// The three fetchers run concurrently inside Promise.all, so all of them can
// call getBrowser() before any has finished. Without this guard each one
// attaches/launches its own browser — visible as the duplicated
// "attached to your running Chrome" lines, and with the launch fallback it
// leaks whole browser processes. Serialise on a single in-flight promise.
async function getBrowser() {
  if (_browserPromise) return _browserPromise;
  _browserPromise = _getBrowserInner().finally(() => {
    _browserPromise = null;
  });
  return _browserPromise;
}

async function _getBrowserInner() {
  // Only reuse a cached browser if it is still connected AND is the kind we
  // want. Without this second condition, a fallback browser launched during a
  // race at boot (Startup folder starts the tracker before Chrome's debug port
  // is up) would be cached for the life of the process — every source silently
  // blocked by Akamai, with no retry even once Chrome became available.
  const cachedIsUsable =
    _browser && _browser.isConnected() && (!config.CHROME_CDP_URL || _usingCdp);
  if (cachedIsUsable) return _browser;

  if (_browser && !_usingCdp && config.CHROME_CDP_URL) {
    console.log("Retrying attach to your Chrome (currently on fallback browser)...");
    await _browser.close().catch(() => {});
  }
  _browser = null;
  _usingCdp = false;
  _context = null; // drop any stale context

  let chromium;
  // Optional stealth layer for Akamai-protected sources (Tesla). Patches the
  // JS-environment tells plain Playwright leaves exposed: navigator.webdriver,
  // missing chrome runtime objects, plugin/codec mismatches, etc.
  //   npm install playwright-extra puppeteer-extra-plugin-stealth
  if (config.USE_STEALTH) {
    try {
      const { chromium: xChromium } = require("playwright-extra");
      const stealth = require("puppeteer-extra-plugin-stealth")();
      xChromium.use(stealth);
      chromium = xChromium;
      console.log("Browser: stealth plugin enabled");
    } catch (e) {
      console.error(
        `USE_STEALTH is on but packages aren't installed (${e.message}). ` +
          "Run: npm install playwright-extra puppeteer-extra-plugin-stealth"
      );
    }
  }
  if (!chromium) {
    try {
      ({ chromium } = require("playwright"));
    } catch {
      console.error(
        "Playwright not installed — browser sources will be skipped. Run: npm install playwright && npx playwright install chromium"
      );
      return null;
    }
  }

  // BEST OPTION for Akamai-protected sources (Tesla): attach to a Chrome that
  // YOU started, rather than launching one under automation control. Akamai
  // detects automation-launched browsers; a browser you started normally and
  // that we merely connect to looks like exactly what it is — your Chrome.
  //
  // To use: close all Chrome windows, then run start-chrome-debug.bat (included),
  // then start the tracker. Set CHROME_CDP_URL in config to enable.
  if (config.CHROME_CDP_URL) {
    try {
      _browser = await chromium.connectOverCDP(config.CHROME_CDP_URL);
      _usingCdp = true;
      console.log(`Browser: attached to your running Chrome at ${config.CHROME_CDP_URL}`);
      return _browser;
    } catch (e) {
      console.error(
        `Could not attach to Chrome at ${config.CHROME_CDP_URL} (${e.message}). ` +
          "Falling back to a launched browser — sources behind Akamai will be blocked. " +
          "Will keep retrying the attach on each cycle."
      );
    }
  }

  const opts = {
    headless: config.HEADLESS !== false,
    args: ["--disable-blink-features=AutomationControlled", "--no-sandbox"],
  };
  // Deliberately NOT using channel:"chrome" by default — launching your
  // installed Chrome can hand off to an already-running session and fail.
  if (config.USE_REAL_CHROME) opts.channel = "chrome";

  try {
    _browser = await chromium.launch(opts);
  } catch (e) {
    if (opts.channel) {
      console.error(`Installed Chrome failed to launch (${e.message}) — using bundled Chromium.`);
      delete opts.channel;
      _browser = await chromium.launch(opts);
    } else {
      throw e;
    }
  }
  return _browser;
}

// Tesla hides a listing unless your delivery ZIP is near the car, so the alert
// includes a representative ZIP for the car's city. This is a static lookup —
// no geocoding API, no network call, no added latency. It covers the metros
// where Tesla actually has delivery centres, which is where the inventory is.
// Unknown cities simply omit the ZIP rather than guessing wrong.
const METRO_ZIPS = {
  "los angeles": "90045", burbank: "91502", "costa mesa": "92626", buena_park: "90620",
  "san diego": "92121", "san francisco": "94110", fremont: "94538", "san jose": "95112",
  sacramento: "95826", "santa clara": "95050", "walnut creek": "94597", "palo alto": "94304",
  pasadena: "91107", "long beach": "90805", carlsbad: "92008", "west covina": "91790",
  seattle: "98108", bellevue: "98004", renton: "98057", portland: "97217", tigard: "97223",
  denver: "80216", littleton: "80120", phoenix: "85040", scottsdale: "85260", tempe: "85281",
  "las vegas": "89118", austin: "78725", dallas: "75234", houston: "77094", "san antonio": "78216",
  plano: "75024", chicago: "60618", schaumburg: "60173", naperville: "60563",
  miami: "33172", orlando: "32809", tampa: "33619", jacksonville: "32256", "west palm beach": "33417",
  atlanta: "30341", nashville: "37209", charlotte: "28217", raleigh: "27604",
  "new york": "10019", brooklyn: "11222", queens: "11101", "mount kisco": "10549",
  paramus: "07652", "springfield township": "07081", edison: "08817",
  boston: "02135", dedham: "02026", peabody: "01960", norwell: "02061",
  philadelphia: "19153", devon: "19333", "king of prussia": "19406",
  washington: "20018", rockville: "20852", tysons: "22102", richmond: "23294",
  "st louis": "63132", "saint louis": "63132", "kansas city": "64114",
  columbus: "43240", cleveland: "44145", cincinnati: "45242", detroit: "48084", troy: "48084",
  minneapolis: "55416", milwaukee: "53045", indianapolis: "46240",
  "salt lake city": "84115", "new orleans": "70002", "oklahoma city": "73114",
  honolulu: "96819", "san juan": "00926", pittsburgh: "15205", buffalo: "14221",
};

function zipForLocation(city, state) {
  if (!city) return null;
  const key = city.toLowerCase().trim();
  return METRO_ZIPS[key] || null;
}

// Page creation. Two rules learned the hard way:
//  1. When attached over CDP, reuse the EXISTING context — a new one would have
//     none of your real cookies, defeating the point of attaching.
//  2. Never pass options to newPage() on a context; Playwright rejects that with
//     "Please use browser.newContext()". So we create ONE context up front with
//     our UA/viewport, then open plain pages from it.
let _context = null;
async function getContext(browser) {
  if (_context) return _context;

  if (config.CHROME_CDP_URL) {
    const ctxs = browser.contexts();
    if (ctxs.length) {
      _context = ctxs[0]; // your real Chrome profile, cookies and all
      return _context;
    }
  }

  _context = await browser.newContext({
    userAgent: PAGE_HEADERS["User-Agent"],
    viewport: { width: 1440, height: 900 },
  });
  return _context;
}

async function openPage(browser) {
  const ctx = await getContext(browser);
  return ctx.newPage();
}

// Tesla fingerprints the client the same way Carvana does: the v4 endpoint
// returns 200 in a real browser and 403 from plain Node fetch for the IDENTICAL
// URL on the same IP. Headers can't fix that. So we run the API call from inside
// a real Chromium page, same-origin against tesla.com — which is exactly the
// context that returns 200.
// Best-effort location extraction from a Tesla inventory record. Tries the
// field names Tesla has used, then falls back to any key that looks like a
// city/state pair. Returns null if nothing plausible is found.
function teslaLocation(r) {
  const city =
    r.City ?? r.city ?? r.MetroName ?? r.LocationCity ?? r.TrtCity ?? null;
  const state =
    r.StateProvince ?? r.State ?? r.StateAbbr ?? r.LocationState ?? null;

  if (city && state) return `${city}, ${state}`;
  if (city) return String(city);
  if (state) return String(state);

  // Fallback: some responses nest location under a single display field.
  for (const k of Object.keys(r)) {
    if (!/city|location|metro/i.test(k)) continue;
    const v = r[k];
    if (typeof v === "string" && v.trim() && v.length < 60) return v.trim();
  }
  return null;
}

// Tesla has now failed twice at almost exactly the 12-hour mark, and it did so
// AFTER we cut requests from 5 pages to 1 and stretched the interval — so this
// is session expiry, not volume. Akamai issues a token that eventually dies;
// our hourly "refresh" only reloaded a page the browser already had a cookie
// for, which does not force a new one to be minted.
//
// Recovery: drop the tesla.com cookies specifically, then walk in from the
// homepage like a person arriving fresh, so a new token gets issued. Cookie
// clearing is scoped to tesla.com — this shares your real Chrome profile and
// must never touch your other logins.
async function reheelTeslaSession(browser) {
  console.log("Tesla: attempting session recovery (clearing tesla.com cookies)...");
  try {
    const ctx = await getContext(browser);
    let cleared = false;
    try {
      // Domain-scoped clear (Playwright >= 1.43). If unsupported, fall through
      // WITHOUT clearing rather than nuking every cookie in the profile.
      await ctx.clearCookies({ domain: ".tesla.com" });
      await ctx.clearCookies({ domain: "www.tesla.com" });
      cleared = true;
    } catch (e) {
      console.error(
        `Tesla: domain-scoped cookie clear unavailable (${e.message}) — skipping clear to avoid ` +
          "wiping unrelated logins in your Chrome profile."
      );
    }

    let page;
    try {
      page = await openPage(browser);
      // Arrive via the homepage first, then the inventory page — a fresh token
      // is issued on a normal entry path, not on a bare deep link.
      await page.goto("https://www.tesla.com/", { waitUntil: "domcontentloaded", timeout: 45000 });
      await page.waitForTimeout(3000);
      const nav = await page.goto("https://www.tesla.com/inventory/used/ms", {
        waitUntil: "domcontentloaded",
        timeout: 45000,
      });
      const st = nav ? nav.status() : 0;
      console.log(`Tesla: recovery navigation -> HTTP ${st} (cookies cleared: ${cleared})`);
      await page.waitForTimeout(3000);
      return st >= 200 && st < 300;
    } finally {
      if (page) await page.close().catch(() => {});
    }
  } catch (e) {
    console.error(`Tesla: session recovery failed (${e.message})`);
    return false;
  }
}

async function fetchTesla() {
  if (SOURCES.tesla === false) return undefined; // disabled in config
  if (!TESLA_SOURCE_APPLICABLE) return undefined; // Tesla source only searches Teslas
  // Order matters: check backoff FIRST. isDueToRun() records "ran at now" as a
  // side effect, so asking it while backed off burns the interval slot and the
  // source then waits a whole extra interval after the backoff expires.
  if (isBackedOff("tesla")) return null;
  if (!isDueToRun("tesla")) return undefined; // not due yet this tick

  const browser = await getBrowser();
  if (!browser) return null;

  // Check BOTH conditions. Tesla lists refused-delivery cars and repaired
  // demo/loaner units as "new" inventory, not used — querying only "used"
  // misses exactly the straggler listings worth catching. Two requests per
  // check, and Tesla runs on a 15-min interval, so the extra load is trivial.
  const conditions = config.TESLA_CONDITIONS || ["new", "used"];

  // Once a day, run the "used" sweep UNFILTERED to audit the trim filter.
  // Rationale: when inventory holds zero matching vehicles, a filtered query
  // returning 0 looks identical to a broken filter code returning 0 forever —
  // the silent-miss pattern. The audit pulls everything and checks in code
  // whether any match exists that the filter would have hidden.
  const AUDIT_EVERY_MS = 24 * 60 * 60 * 1000;
  const auditDue = Date.now() - (state.teslaFilterAuditAt || 0) > AUDIT_EVERY_MS;

  const buildQuery = (condition) => ({
    query: {
      model: VEHICLE.teslaModelCode || "ms",
      condition,
      // USED: filter server-side by trim. Verified live that Tesla respects
      //   options.TRIM. This collapses ~5 pages of all Model S down to one,
      //   cutting request volume ~80% — which is what started tripping Akamai.
      // NEW: left unfiltered. "PLD1" is only confirmed against used listings,
      //   and new Model S inventory is tiny (currently zero), so it fits in a
      //   single page regardless and we avoid relying on an unverified code.
      // AUDIT: once daily the used sweep also runs unfiltered as a cross-check
      //   that the filter is not silently hiding cars.
      options:
        condition === "used" && !auditDue && (VEHICLE.teslaTrimCodes || []).length
          ? { TRIM: VEHICLE.teslaTrimCodes }
          : {},
      arrangeby: "Price",
      order: "asc",
      market: "US",
      language: "en",
      super_region: "north america",
      lng: config.LNG ?? -71.4487,
      lat: config.LAT ?? 43.5215,
      zip: config.ZIP || "03229",
      range: config.NATIONWIDE ? 0 : config.TESLA_SEARCH_RANGE_MILES || 500,
      region: config.REGION || "NH",
    },
    offset: 0,
    count: 100,
    outsideOffset: 0,
    outsideSearch: config.NATIONWIDE === true,
  });

  // Akamai session hygiene: we navigate straight to the JSON API and never load
  // a normal Tesla page, so the protection cookies in this Chrome profile go
  // stale and are eventually rejected — which is why Tesla worked for hours and
  // then began 403ing. Periodically load the real inventory page first (which
  // the attached real Chrome can do) to refresh those cookies, the way ordinary
  // browsing would.
  // Observed: Tesla ran clean for ~5 hours after checks resumed, then began
  // 403ing. Note the session survived a 6-hour idle night pause unharmed, so
  // this is not a simple clock from issuance — something accumulates during
  // ACTIVE checking. Refresh on a 3h cadence to stay comfortably inside that
  // ~5h active window. Cheap: one extra page load.
  const COOKIE_REFRESH_EVERY_MS = 3 * 60 * 60 * 1000;
  const lastRefresh = state.teslaCookieRefreshedAt || 0;
  if (Date.now() - lastRefresh > COOKIE_REFRESH_EVERY_MS) {
    let warm;
    try {
      warm = await openPage(browser);
      // Enter via the homepage first. Reloading the deep link alone reuses the
      // existing cookie; arriving fresh is what causes a new token to be issued.
      await warm.goto("https://www.tesla.com/", {
        waitUntil: "domcontentloaded",
        timeout: 45000,
      });
      await warm.waitForTimeout(2500);
      const wr = await warm.goto("https://www.tesla.com/inventory/used/ms", {
        waitUntil: "domcontentloaded",
        timeout: 45000,
      });
      console.log(`Tesla: session refresh -> HTTP ${wr ? wr.status() : "?"}`);
      await warm.waitForTimeout(2500);
      state.teslaCookieRefreshedAt = Date.now();
      saveJson(STATE_PATH, state);
    } catch (e) {
      console.error(`Tesla: session refresh failed (${e.message})`);
    } finally {
      if (warm) await warm.close().catch(() => {});
    }
  }

  const all = [];
  let anySucceeded = false;
  let lastStatus = 0;

  // Tesla caps each response at ~24 records regardless of the "count" we ask
  // for — verified live: count:100 returned 24 while total_matches_found said
  // 111. Without paging we were seeing barely a fifth of nationwide inventory,
  // and a matching car sitting on page 2+ would have been invisible while the
  // tracker confidently reported zero. So: page through via "offset" until we
  // have them all.
  const PAGE_SIZE = 24; // Tesla serves 24 per response regardless of what we ask
  const MAX_PAGES = 12; // hard stop so a bad response can't loop forever

  for (const condition of conditions) {
    let offset = 0;
    let totalForCondition = null;
    let pagesFetched = 0;
    const seenVinsThisCondition = new Set();
    let vinlessThisCondition = 0;

    while (pagesFetched < MAX_PAGES) {
      const q = buildQuery(condition);
      // CRITICAL: Tesla IGNORES the "offset" parameter — verified live that
      // offset:0 and offset:72 returned the identical first VIN. Paging on it
      // re-fetched the same 24 cars repeatedly while appearing to make
      // progress. The parameter that actually walks the full national result
      // set is "outsideOffset" (outsideOffset:96 of 111 returned exactly the
      // last 15). So: offset stays 0, outsideOffset does the paging.
      q.offset = 0;
      q.outsideOffset = offset;
      q.count = PAGE_SIZE;

      const apiUrl =
        "https://www.tesla.com/inventory/api/v4/inventory-results?query=" +
        encodeURIComponent(JSON.stringify(q));

      let page;
      let pageResults = [];
      try {
        page = await openPage(browser);

        // Akamai protects Tesla's HTML inventory pages, so navigate STRAIGHT
        // to the JSON API path rather than loading the page first.
        const nav = await page.goto(apiUrl, {
          waitUntil: "domcontentloaded",
          timeout: 45000,
        });
        lastStatus = nav ? nav.status() : 0;
        if (lastStatus < 200 || lastStatus >= 300) {
          console.error(`Tesla (${condition}) offset ${offset}: HTTP ${lastStatus} — blocked`);
          break;
        }

        const bodyText = await page.evaluate(() => document.body.innerText);
        let d;
        try {
          d = JSON.parse(bodyText);
        } catch {
          console.error(`Tesla (${condition}) offset ${offset}: not JSON (challenge page?)`);
          break;
        }

        anySucceeded = true;
        if (totalForCondition === null) totalForCondition = d.total_matches_found ?? 0;

        if (Array.isArray(d.results)) pageResults = d.results;
        else if (d.results && typeof d.results === "object")
          pageResults = Object.values(d.results).flatMap((v) => (Array.isArray(v) ? v : []));
      } catch (err) {
        console.error(`Tesla (${condition}) offset ${offset} failed: ${err.message}`);
        break;
      } finally {
        if (page) await page.close().catch(() => {});
      }

      if (!pageResults.length) break; // no more records

      // Snapshot BEFORE ingesting this page so we can tell whether it actually
      // contributed anything new. (Previously this was captured after the loop,
      // making the comparison trivially equal and bailing on page 2 every time.)
      const uniqueBeforePage = seenVinsThisCondition.size;

      for (const r of pageResults) {
        // Count UNIQUE vehicles, not records. The old raw count made repeated
        // identical pages look like progress ("scanned 96/111" when we had
        // actually seen the same 24 cars four times).
        if (r.VIN) {
          seenVinsThisCondition.add(r.VIN);
        } else {
          vinlessThisCondition += 1;
        }
        // Year + trim matching is config-driven and FAILS OPEN on an unknown
        // trim: surfacing an extra car costs a glance, hiding the one car this
        // tool exists to find costs the car.
        if (!matchesVehicle(r.Year, r.TrimName)) continue;
        const trimName = r.TrimName || "";

        if (all.some((x) => x.vin === r.VIN)) continue; // dedupe across pages/conditions

        const city = r.City || "";
        const st = r.StateProvince || "";
        all.push({
          source: condition === "new" ? "Tesla (new)" : "Tesla",
          vin: r.VIN,
          price: r.InventoryPrice ?? r.Price ?? r.PurchasePrice,
          mileage: r.Odometer ?? 0,
          year: r.Year,
          trim: trimName,
          // Location matters here: Tesla hides a listing unless your delivery
          // ZIP is near the car, so knowing the city up front saves a step.
          location: city && st ? `${city}, ${st}` : st || city || null,
          nearbyZip: zipForLocation(city, st),
          url: `https://www.tesla.com/ms/order/${r.VIN}`,
        });
      }

      pagesFetched += 1;
      offset += pageResults.length;

      // Brief pause between pages. Five back-to-back API hits per check is a
      // burst pattern; spacing them looks far more like a person paging through
      // results and costs us only a few seconds on a 20-minute cycle.
      await new Promise((r) => setTimeout(r, 1500));

      // Safety: if a page contributed no NEW vehicles, the API is repeating
      // itself (as it does when a pagination parameter is ignored) and further
      // paging is pointless — bail rather than loop on duplicates.
      if (seenVinsThisCondition.size === uniqueBeforePage) {
        console.error(
          `Tesla (${condition}): page at outsideOffset ${offset - pageResults.length} added no new vehicles — stopping.`
        );
        break;
      }
      if (totalForCondition !== null && offset >= totalForCondition) break;
    }

    if (condition === "used" && auditDue) {
      state.teslaFilterAuditAt = Date.now();
      saveJson(STATE_PATH, state);
      const matchesFound = all.filter((x) => matchesVehicle(x.year, x.trim)).length;
      console.log(
        `Tesla: daily unfiltered audit complete — ${seenVinsThisCondition.size} listings scanned, ` +
          `${matchesFound} match(es) present. ` +
          (matchesFound > 0
            ? "Confirms matches ARE detectable; verify the filtered runs also see them."
            : "No matching vehicles in inventory at all, so the filter cannot be hiding any.")
      );
    }

    if (totalForCondition !== null) {
      const uniqueSeen = seenVinsThisCondition.size;
      const shortfall = totalForCondition - uniqueSeen;

      // Tolerance matters here. total_matches_found is captured on page 1, but
      // paging through takes several seconds — a car selling mid-scan, a
      // duplicate VIN in Tesla's own data, or a record with no VIN all produce
      // a shortfall of one or two on a perfectly healthy run. Screaming
      // "INCOMPLETE" at that trains you to ignore the warning, and then it is
      // useless when a real gap (24/111) appears. So: only alarm on a
      // meaningful miss.
      const tolerance = Math.max(2, Math.ceil(totalForCondition * 0.03));
      const meaningfulGap = shortfall > tolerance;

      let note = "";
      if (meaningfulGap) note = ` — INCOMPLETE, ${shortfall} listings not checked`;
      else if (shortfall > 0)
        note = ` (${shortfall} fewer than reported — inventory likely shifted mid-scan)`;
      if (vinlessThisCondition)
        note += ` [${vinlessThisCondition} record(s) had no VIN]`;

      const mode =
        condition === "used" ? (auditDue ? " [unfiltered audit]" : " [trim filter]") : " [unfiltered]";
      console.log(
        `Tesla (${condition})${mode}: scanned ${uniqueSeen}/${totalForCondition} unique listings in ${pagesFetched} page(s)${note}`
      );
    }
  }

  // If everything was blocked, try to re-earn a session before giving up. Only
  // once per cycle, and only when we actually got a 403 — not for timeouts or
  // a browser that never came up.
  if (!anySucceeded && lastStatus === 403) {
    const recovered = await reheelTeslaSession(browser);
    if (recovered) {
      console.log("Tesla: session recovered — next scheduled check should succeed.");
      state.teslaCookieRefreshedAt = Date.now();
      saveJson(STATE_PATH, state);
    }
  }

  recordResult("tesla", { ok: anySucceeded, status: anySucceeded ? 200 : lastStatus || 403 });
  if (!anySucceeded) {
    console.error(
      "Tesla: all conditions blocked. Is the debug Chrome still running? (start-chrome-debug.bat)"
    );
    return null;
  }
  return all;
}

async function fetchCarMax() {
  if (SOURCES.carmax === false) return undefined; // disabled in config
  // Order matters: check backoff FIRST. isDueToRun() records "ran at now" as a
  // side effect, so asking it while backed off burns the interval slot and the
  // source then waits a whole extra interval after the backoff expires.
  if (isBackedOff("carmax")) return null;
  if (!isDueToRun("carmax")) return undefined; // not due yet this tick

  // CarMax URL is /cars/{make}/{model}[/{trim}] — built from config so this
  // works for any vehicle. The server-side trim
  // filter keeps results to a single page where possible.
  const url =
    "https://www.carmax.com/cars/" +
    slug(MAKE) +
    "/" +
    slug(MODEL) +
    (TRIM ? "/" + slug(TRIM) : "");

  // CarMax ran fine on plain fetch for ~390 consecutive checks, then started
  // returning 403 — their Akamai protection eventually flagged the sustained
  // 2-minute polling, the same wall Tesla sits behind. Route it through the
  // attached real Chrome, which already defeats Akamai for Tesla. Falls back to
  // plain fetch if no browser is available, so the source still works (until
  // blocked) on a machine without the debug Chrome running.
  const browser = await getBrowser();

  let html = null;
  if (browser) {
    let page;
    try {
      page = await openPage(browser);
      const nav = await page.goto(url, { waitUntil: "domcontentloaded", timeout: 45000 });
      const status = nav ? nav.status() : 0;
      recordResult("carmax", { ok: status >= 200 && status < 300, status });
      if (status < 200 || status >= 300) {
        console.error(`CarMax: HTTP ${status} (via browser) — blocked`);
        return null;
      }
      html = await page.content();
      console.log(`CarMax: HTTP ${status} (via browser), html length=${html.length}`);
    } catch (err) {
      console.error(`CarMax browser fetch failed: ${err.message}`);
      return null;
    } finally {
      if (page) await page.close().catch(() => {});
    }
  } else {
    const res = await fetch(url, { headers: PAGE_HEADERS });
    recordResult("carmax", res);
    if (!res.ok) return null;
    html = await res.text();
    console.log(`CarMax: HTTP ${res.status} (plain fetch), html length=${html.length}`);
  }

  // NOTE: CarMax renders its schema.org JSON-LD blocks client-side with
  // JavaScript, so a plain fetch never sees them. The raw server HTML does
  // embed the listing data as JSON objects keyed by "stockNumber" — same
  // approach we use for Carvana. Parse those instead.
  // Browser-rendered content can carry the embedded JSON escaped (as Carvana's
  // does), while raw fetch HTML has it plain. Normalise so one parser covers
  // both paths, and try the un-escaped form if the plain split finds nothing.
  let chunks = html.split('"stockNumber":').slice(1);
  if (chunks.length === 0) {
    const unescaped = html.split('\\"').join('"');
    chunks = unescaped.split('"stockNumber":').slice(1);
    if (chunks.length) html = unescaped;
  }
  console.log(`CarMax: vehicle chunks found=${chunks.length}`);

  // Verified live: CarMax's own totalCount matched our unique-VIN count exactly
  // (20/20) with no pagination fields. If that ever diverges, we are missing
  // listings — say so rather than reporting a confident partial result.
  const totalMatch = html.match(/"totalCount":(\d+)/);
  if (totalMatch) {
    const stated = Number(totalMatch[1]);
    const uniqueVins = new Set(
      chunks.map((c) => (c.match(/"vin":"([A-HJ-NPR-Z0-9]{17})"/) || [])[1]).filter(Boolean)
    ).size;
    if (uniqueVins < stated) {
      console.error(
        `CarMax: WARNING — site reports ${stated} listings but we parsed ${uniqueVins}. ` +
          "Some inventory is not being checked."
      );
    }
  }

  const listings = [];
  for (const chunk of chunks) {
    const get = (re) => {
      const m = chunk.match(re);
      return m ? m[1] : null;
    };

    const vin = get(/"vin":"([A-HJ-NPR-Z0-9]{17})"/);
    const year = get(/"year":(\d{4})/);
    const trim = get(/"trim":"([^"]*)"/);
    const mileage = get(/"mileage":(\d+)/);
    const price = get(/"basePrice":(\d+(?:\.\d+)?)/);
    const stockNumber = chunk.match(/^\s*"?(\d+)"?/)?.[1];

    if (!vin || !year) continue;
    if (!matchesVehicle(year, trim)) continue;
    if (listings.some((l) => l.vin === vin)) continue; // CarMax embeds each car twice

    listings.push({
      source: "CarMax",
      vin,
      price: price ? Math.round(Number(price)) : null,
      mileage: mileage ? Number(mileage) : null,
      year: Number(year),
      trim: trim || TRIM || "",
      url: stockNumber
        ? `https://www.carmax.com/car/${stockNumber}`
        : url,
    });
  }
  return listings;
}

// Carvana is the ONE source that genuinely fingerprint-blocks plain Node fetch.
// Verified: identical URL + identical home IP returns 200 in real Chrome and 403
// from Node, at the same moment. Headers can't fix that — the TLS/HTTP2 handshake
// itself is the tell. So we drive a real Chromium via Playwright for this source
// only. Tesla and CarMax stay on plain fetch (fast, no browser overhead).
async function carvanaIsStillAvailable(browser, vehicleId) {
  let page;
  try {
    page = await openPage(browser);
    const resp = await page.goto(`https://www.carvana.com/vehicle/${vehicleId}`, {
      waitUntil: "domcontentloaded",
      timeout: 45000,
    });
    if (!resp || resp.status() < 200 || resp.status() >= 300) return null; // unknown
    await page.waitForTimeout(2500);
    const html = (await page.content()).replace(/\\"/g, '"');

    const m = html.match(/"isAvailableForPurchase":(true|false)/);
    if (!m) {
      console.log(`Carvana ${vehicleId}: availability field not found — treating as unknown`);
      return { available: null, onHold: false, expiresAt: null };
    }
    const available = m[1] === "true";

    // A HOLD is materially different from a completed sale. Carvana gives
    // another buyer a short exclusive checkout window and publishes exactly
    // when it lapses ("expires", with shouldShowTimer true). Holds frequently
    // expire without the sale completing, and when that happens the car returns
    // to the market immediately — a window measured in seconds. Capture it.
    const showTimer = /"shouldShowTimer":true/.test(html);
    const lockedByUs = /"isLockedByThisUser":true/.test(html);
    // Next.js serialises dates with a "$D" prefix.
    const expRaw = (html.match(/"expires":"?\$?D?([0-9T:.\-]+Z)/) || [])[1];
    const expiresAt = expRaw ? Date.parse(expRaw) : null;
    const onHold =
      !available && !lockedByUs && showTimer && !!expiresAt && expiresAt > Date.now();

    if (onHold) {
      const mins = Math.max(0, Math.round((expiresAt - Date.now()) / 60000));
      console.log(
        `Carvana ${vehicleId}: ON HOLD by another buyer — expires in ~${mins} min (${new Date(expiresAt).toLocaleTimeString()})`
      );
    } else {
      console.log(`Carvana ${vehicleId}: isAvailableForPurchase=${available}`);
    }
    return { available, onHold, expiresAt };
  } catch (e) {
    console.error(`Carvana availability check failed for ${vehicleId}: ${e.message}`);
    return null;
  } finally {
    if (page) await page.close().catch(() => {});
  }
}

async function fetchCarvana() {
  if (SOURCES.carvana === false) return undefined; // disabled in config
  // Order matters: check backoff FIRST. isDueToRun() records "ran at now" as a
  // side effect, so asking it while backed off burns the interval slot and the
  // source then waits a whole extra interval after the backoff expires.
  if (isBackedOff("carvana")) return null;
  if (!isDueToRun("carvana")) return undefined; // not due yet this tick

  // Carvana's "cvnaid" parameter is just base64-encoded JSON describing the
  // filter. Verified: rebuilding it from make/model/trim reproduces the exact
  // string the site itself generates, so this works for any vehicle.
  const filterObj = {
    filters: {
      makes: [
        {
          name: MAKE,
          parentModels: [TRIM ? { name: MODEL, trims: [TRIM] } : { name: MODEL }],
        },
      ],
    },
  };
  const cvnaid = Buffer.from(JSON.stringify(filterObj)).toString("base64").replace(/=+$/, "");
  const url = "https://www.carvana.com/cars/filters?cvnaid=" + cvnaid;

  const browser = await getBrowser();
  if (!browser) return null;

  let page;
  try {
    page = await openPage(browser);
    const resp = await page.goto(url, { waitUntil: "domcontentloaded", timeout: 45000 });
    const status = resp ? resp.status() : 0;
    console.log(`Carvana: HTTP ${status} (via browser)`);
    recordResult("carvana", { ok: status >= 200 && status < 300, status });
    if (status < 200 || status >= 300) return null;

    // Let the client-side data settle, then read the rendered HTML.
    await page.waitForTimeout(2500);
    const rawHtml = await page.content();

    // CRITICAL: Carvana embeds listing JSON *escaped* inside a JS string
    // (\"stockNumber\":). Without unescaping, the split below matches zero
    // times and the parser silently reports "0 listings" on a perfectly good
    // page. Verified live: unescaping yields all listings with every field.
    const html = rawHtml.replace(/\\"/g, '"');

    const chunks = html.split('"stockNumber":').slice(1);
    console.log(`Carvana: vehicle chunks found=${chunks.length}`);

    // Verified live: Carvana returns totalMatchedPages:1 with pageSize:24, and
    // typical matching inventory fits comfortably. But if it ever exceeds
    // one page we would silently see only the first — the same blind spot that
    // hid 87 of 111 Tesla listings. Make that loud instead of invisible.
    const pagesMatch = html.match(/"totalMatchedPages":(\d+)/);
    if (pagesMatch && Number(pagesMatch[1]) > 1) {
      console.error(
        `Carvana: WARNING — site reports ${pagesMatch[1]} pages of results but we only read page 1. ` +
          "Inventory has outgrown a single page; pagination needs to be added."
      );
    }

    const listings = [];
    for (const chunk of chunks) {
      const get = (re) => {
        const m = chunk.match(re);
        return m ? m[1] : null;
      };
      const vin = get(/"vin":"([A-HJ-NPR-Z0-9]{17})"/);
      const year = get(/"year":(\d{4})/);
      const trim = get(/"trim":"([^"]*)"/);
      const mileage = get(/"mileage":(\d+)/);
      const price = get(/"total":(\d+)/);
      const pending = get(/"isPurchasePending":(true|false)/);
      // Carvana's real listing links are /vehicle/{vehicleId} — NOT stockNumber,
      // and no slug. Verified against the actual anchor hrefs on the results page.
      const vehicleId = get(/"vehicleId":(\d+)/);

      if (!vin || !year || pending === "true") continue;
      if (!matchesVehicle(year, trim)) continue;

      listings.push({
        source: "Carvana",
        vin,
        price: price ? Number(price) : null,
        mileage: mileage ? Number(mileage) : null,
        year: Number(year),
        trim,
        vehicleId,
        url: vehicleId ? `https://www.carvana.com/vehicle/${vehicleId}` : url,
      });
    }
    // Confirm each candidate against its detail page before returning it.
    const confirmed = [];
    for (const l of listings) {
      const avail = await carvanaIsStillAvailable(browser, l.vehicleId);
      const available = avail ? avail.available : null;
      if (avail && avail.onHold) {
        // Someone is in checkout right now. Track the exact expiry so we can
        // watch it closely and pounce the moment it lapses.
        confirmed.push({
          ...l,
          availabilityConfirmed: false,
          currentlyReserved: true,
          onHold: true,
          holdExpiresAt: avail.expiresAt,
        });
        continue;
      }
      if (available === false) {
        console.log(
          `Carvana: ${l.year} ${l.trim} (${l.vehicleId}) is reserved — tracking it so we alert if it frees up`
        );
        // Deliberately NOT dropped. It is passed through marked unavailable so
        // runCheck records it as "possible" — that way, when the pending sale
        // falls through and it becomes buyable, the status change triggers a
        // real alert. Dropping it here is what made vehicle 4616568 invisible
        // the moment it actually became available.
        confirmed.push({ ...l, availabilityConfirmed: false, currentlyReserved: true });
        continue;
      }
      // true (confirmed) or null (couldn't tell) -> surface it; better a rare
      // false alarm than a missed car.
      confirmed.push({ ...l, availabilityConfirmed: available === true });
    }
    return confirmed;
  } catch (err) {
    console.error(`Carvana browser fetch failed: ${err.message}`);
    return null;
  } finally {
    if (page) await page.close().catch(() => {});
  }
}

// ============================================================
// FALLBACK SOURCE — TeslaTracker
// ============================================================
// Deliberately NOT a fourth peer source. Their data refreshes roughly hourly
// and they surface already-reserved cars as available (verified: the locked
// Carvana listing showed as available on their site for 37+ hours). As a peer it
// would add stale, lower-confidence duplicates.
//
// As a FAILOVER it is valuable: it is a completely different access path, so
// the Akamai blocks that take out our direct sources do not affect it. It only
// runs when a direct source has been failing repeatedly, and anything it finds
// is flagged clearly and sent at non-emergency priority, because we cannot
// verify availability through them.
async function fetchTeslaTrackerFallback(browser, failingSources) {
  if (!config.TESLATRACKER_FALLBACK) return [];
  if (!failingSources.length) return [];

  // TeslaTracker only indexes Teslas, and its category codes are model-specific.
  // Rather than silently returning nothing for other vehicles, skip explicitly
  // and say why once, so a BMW search doesn't look like a broken fallback.
  const ttCategory = TESLATRACKER_CATEGORIES[`${slug(MODEL)}|${slug(TRIM || "")}`];
  if (!TESLA_SOURCE_APPLICABLE || !ttCategory) {
    if (!state.ttFallbackNoticeShown) {
      state.ttFallbackNoticeShown = true;
      saveJson(STATE_PATH, state);
      console.log(
        `TeslaTracker fallback unavailable for ${VEHICLE_LABEL} — it only covers Teslas ` +
          "with a known category code. Direct sources are unaffected."
      );
    }
    return [];
  }

  const url =
    "https://teslatracker.com/inventory?model=" +
    encodeURIComponent(MODEL) +
    "&page=1&source=tesla%2Ccarvana%2Ccarmax%2Cprivate&category=" +
    ttCategory +
    "&minYear=" +
    YEAR_MIN;

  let page;
  try {
    let html;
    if (browser) {
      page = await openPage(browser);
      const nav = await page.goto(url, { waitUntil: "domcontentloaded", timeout: 45000 });
      const status = nav ? nav.status() : 0;
      if (status < 200 || status >= 300) {
        console.error(`TeslaTracker fallback: HTTP ${status}`);
        return [];
      }
      await page.waitForTimeout(2500);
      html = await page.content();
    } else {
      const r = await fetch(url, { headers: PAGE_HEADERS });
      if (!r.ok) {
        console.error(`TeslaTracker fallback: HTTP ${r.status}`);
        return [];
      }
      html = await r.text();
    }

    // Their listing data is embedded escaped in the server-rendered payload,
    // same shape as Carvana's.
    const un = html.split('\\"').join('"');
    const parts = un.split('"vin":').slice(1);

    const out = [];
    for (const p of parts) {
      const g = (re) => {
        const m = p.match(re);
        return m ? m[1] : null;
      };
      const vin = (p.match(/^"?([A-HJ-NPR-Z0-9]{17})"?/) || [])[1];
      const year = Number(g(/"year":(\d{4})/));
      const trim = g(/"trim":"([^"]*)"/);
      const src = g(/"source":"([a-zA-Z]+)"/);
      const priceRaw = g(/"currentPrice":(\d+)/);
      const mileage = g(/"mileage":(\d+)/);

      if (!vin || !year || year < YEAR_MIN) continue;
      if (!matchesVehicle(year, trim)) continue;
      // Only report for sources that are actually down — otherwise our own
      // direct (and fresher) check already covers it.
      if (src && !failingSources.includes(src.toLowerCase())) continue;

      out.push({
        source: `TeslaTracker (${src || "?"} — fallback)`,
        vin,
        // Their prices are stored in cents.
        price: priceRaw ? Math.round(Number(priceRaw) / 100) : null,
        mileage: mileage ? Number(mileage) : null,
        year,
        trim,
        location: null,
        nearbyZip: null,
        // Their data is hourly and unverified — never emergency priority.
        availabilityConfirmed: false,
        viaFallback: true,
        url:
          "https://teslatracker.com/inventory?model=" +
          encodeURIComponent(MODEL) +
          "&category=" +
          ttCategory +
          "&minYear=" +
          YEAR_MIN,
      });
    }
    console.log(
      `TeslaTracker fallback: checked for [${failingSources.join(", ")}] — ${out.length} candidate(s)`
    );
    return out;
  } catch (e) {
    console.error(`TeslaTracker fallback failed: ${e.message}`);
    return [];
  } finally {
    if (page) await page.close().catch(() => {});
  }
}

// ============================================================
// PUSHOVER
// ============================================================

async function push(title, body, opts = {}) {
  const params = new URLSearchParams({
    token: config.PUSHOVER_TOKEN,
    user: config.PUSHOVER_USER,
    title,
    message: body,
  });

  if (opts.priority === "urgent") {
    // Pushover priority 2 = Emergency: re-rings until acknowledged.
    params.set("priority", "2");
    params.set("retry", "60");
    params.set("expire", "3600");
    params.set("sound", "persistent");
  } else if (opts.priority === "high") {
    // Priority 1 = High: bypasses quiet hours, alerts once, no nagging.
    params.set("priority", "1");
  }
  if (opts.click) {
    params.set("url", opts.click);
    params.set("url_title", "Open listing");
  }

  const res = await fetch("https://api.pushover.net/1/messages.json", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: params.toString(),
  });
  console.log(`Pushover push "${title}" -> HTTP ${res.status}`);
  if (!res.ok) {
    console.error(
      `Pushover push FAILED: ${res.status} ${await res.text().catch(() => "")}`
    );
  }
}

async function notifyListing(l) {
  // Priority is calibrated to CONFIDENCE, not just novelty. Emergency priority
  // re-rings every 60s for an hour until acknowledged — that is the right
  // response to a car we have confirmed is buyable, and the wrong response to
  // one we could not verify. Getting woken repeatedly for a car another buyer
  // already locked is exactly the failure this tool exists to avoid.
  //
  //   confirmed available  -> emergency (wake me, this is real)
  //   could not verify     -> high priority, flagged (tell me, do not nag)
  // Fallback listings come from TeslaTracker's hourly-refreshed data, which we
  // cannot verify for availability — treat them as unverified by definition.
  const unverified =
    l.viaFallback || (l.source === "Carvana" && l.availabilityConfirmed !== true);

  const caveat = l.viaFallback
    ? "\n\n(Via TeslaTracker fallback because a direct source is down. Data may be up to an hour old and availability is unverified — check the listing before acting.)"
    : unverified
      ? "\n\n(Could not confirm availability — it may already be reserved. Check before acting.)"
      : "";

  // Location is included when known. For Tesla especially this is not a nicety:
  // the site hides a listing unless your delivery ZIP is near the car, so
  // knowing "Los Angeles, CA" up front means setting the right ZIP immediately
  // rather than watching the car seem to vanish.
  const where = l.location ? ` • ${l.location}` : "";
  // Include a usable delivery ZIP for Tesla listings — the site will hide the
  // car unless the ZIP you enter is near it.
  const zipHint = l.nearbyZip ? `\nUse delivery ZIP: ${l.nearbyZip}` : "";

  await push(
    `${unverified ? "Possible" : "New"} ${VEHICLE_LABEL}: $${l.price?.toLocaleString?.() ?? l.price}`,
    `${l.source} • ${l.year} ${l.trim} • ${l.mileage} mi${where}${zipHint}${caveat}\n${l.url}`,
    { priority: unverified ? "high" : "urgent", click: l.url }
  );
}

// ============================================================
// CHECK LOOP
// ============================================================

async function runCheck() {
  // Optional night pause (config: NIGHT_PAUSE / NIGHT_START_HOUR / NIGHT_END_HOUR).
  // Notifies on entry and exit so a silent tracker is never ambiguous — you
  // should always know whether "no alerts" means "nothing found" or "not looking".
  if (config.NIGHT_PAUSE) {
    const h = new Date().getHours();
    const start = config.NIGHT_START_HOUR ?? 0;
    const end = config.NIGHT_END_HOUR ?? 6;
    const inQuiet = start < end ? h >= start && h < end : h >= start || h < end;
    const wasQuiet = state.nightPauseActive === true;

    if (inQuiet && !wasQuiet) {
      state.nightPauseActive = true;
      saveJson(STATE_PATH, state);
      await push(
        "Vehicle tracker: night pause started",
        `Monitoring paused until ${String(end).padStart(2, "0")}:00. No checks will run and no listing alerts can fire during this window.`
      );
    } else if (!inQuiet && wasQuiet) {
      state.nightPauseActive = false;
      saveJson(STATE_PATH, state);
      await push(
        "Vehicle tracker: monitoring resumed",
        `Night pause ended. All sources are being checked again.`
      );
    }

    if (!inQuiet && wasQuiet) {
      // Coming out of a pause: force a session refresh on the first check back,
      // since nothing was refreshed during the idle window.
      state.teslaCookieRefreshedAt = 0;
      saveJson(STATE_PATH, state);
    }

    if (inQuiet) {
      console.log(`[${new Date().toLocaleTimeString()}] Night pause active — skipping check.`);
      // IMPORTANT: still ping the healthcheck. The tracker is alive and
      // deliberately idle — without this, hours of silence would trip the
      // external down-alert every single night and train you to ignore it.
      pingHealthcheck();
      return;
    }
  }

  const [tesla, carmax, carvana] = await Promise.all([
    fetchTesla().catch((e) => {
      console.error(`Tesla fetch failed: ${e.message}`);
      return null;
    }),
    fetchCarMax().catch((e) => {
      console.error(`CarMax fetch failed: ${e.message}`);
      return null;
    }),
    fetchCarvana().catch((e) => {
      console.error(`Carvana fetch failed: ${e.message}`);
      return null;
    }),
  ]);

  // Three distinct states, and they must not be conflated:
  //   undefined -> not due to run this tick (per-source pacing)  -> "skip"
  //   null      -> ran and FAILED                                -> "ERR"
  //   []        -> ran fine, genuinely zero matches              -> 0
  const label = (v) => (v === undefined ? "skip" : v === null ? "ERR" : v.length);
  const sourceCounts = {
    tesla: label(tesla),
    carmax: label(carmax),
    carvana: label(carvana),
  };
  state.lastSourceCounts = sourceCounts;
  console.log(
    `[${new Date().toLocaleTimeString()}] Source counts: ${JSON.stringify(sourceCounts)}`
  );

  const all = [...(tesla || []), ...(carmax || []), ...(carvana || [])];

  // Failover: if a direct source has failed repeatedly, ask TeslaTracker what
  // it sees for that source. Threshold of 3 avoids reacting to a single blip;
  // a source that is merely "not due this tick" (undefined) is not failing.
  const FALLBACK_AFTER_FAILURES = 3;
  state.failStreak = state.failStreak || {};
  const failing = [];
  if (tesla === null && (state.failStreak.tesla || 0) >= FALLBACK_AFTER_FAILURES)
    failing.push("tesla");
  if (carmax === null && (state.failStreak.carmax || 0) >= FALLBACK_AFTER_FAILURES)
    failing.push("carmax");
  if (carvana === null && (state.failStreak.carvana || 0) >= FALLBACK_AFTER_FAILURES)
    failing.push("carvana");

  if (failing.length) {
    const browserForFallback = await getBrowser().catch(() => null);
    const fb = await fetchTeslaTrackerFallback(browserForFallback, failing);
    for (const l of fb) {
      if (!all.some((x) => x.vin === l.vin)) all.push(l);
    }
  }

  // seen-vins tracks HOW a car was last seen, not merely that it was seen.
  //
  // Why: a car first surfaced as "possible" (availability unverified) or while
  // pending would previously be recorded as seen and could never alert again —
  // including the moment it became genuinely buyable, which is the single most
  // important transition for this tool. Vehicle 4616568 hit exactly that: it
  // was alerted as "Possible" during a period when the availability check was
  // erroring, and then stayed silent once it actually became available.
  //
  // Stored as { vin: "confirmed" | "possible" }. A car recorded as "possible"
  // that later comes back CONFIRMED available alerts again, as an upgrade.
  const seenRaw = loadJson(SEEN_PATH, null);
  const isFirstRun = seenRaw === null;

  // Migrate the old format (plain array of VIN strings) — treat prior entries
  // as "possible" so anything previously seen can still upgrade to a real alert.
  const seenMap = {};
  if (Array.isArray(seenRaw)) {
    for (const v of seenRaw) seenMap[v] = "possible";
  } else if (seenRaw && typeof seenRaw === "object") {
    Object.assign(seenMap, seenRaw);
  }

  const statusOf = (l) => {
    // Non-Carvana sources have no availability check, so a listing appearing at
    // all is the strongest signal we have.
    if (l.viaFallback) return "possible";
    if (l.source && l.source.startsWith("Carvana"))
      return l.availabilityConfirmed === true ? "confirmed" : "possible";
    return "confirmed";
  };

  // ---- Hold tracking -------------------------------------------------
  state.hotWatch = state.hotWatch || {};
  state.holdNotified = state.holdNotified || {};

  for (const l of all) {
    if (!l.vin) continue;
    if (l.onHold && l.holdExpiresAt) {
      state.hotWatch[l.vin] = l.holdExpiresAt;
      // Heads-up once per hold, keyed on VIN alone. Keying on the expiry
      // timestamp would re-notify every cycle if Carvana recalculates it even
      // slightly — turning a helpful heads-up into a stream of spam.
      if (!state.holdNotified[l.vin]) {
        state.holdNotified[l.vin] = true;
        const mins = Math.max(0, Math.round((l.holdExpiresAt - Date.now()) / 60000));
        await push(
          `${VEHICLE_LABEL} ON HOLD: $${l.price?.toLocaleString?.() ?? l.price}`,
          `${l.source} • ${l.year} ${l.trim} • ${l.mileage} mi\n` +
            `Another buyer has it in checkout. Their hold expires in ~${mins} min ` +
            `(${new Date(l.holdExpiresAt).toLocaleTimeString()}).\n` +
            `Checking every minute — you'll get an urgent alert the second it frees up.\n${l.url}`,
          { priority: "high", click: l.url }
        ).catch(() => {});
      }
    } else if (state.hotWatch[l.vin]) {
      // Resolved one way or the other — stop hot-watching and re-arm the
      // heads-up so a future hold on the same car notifies again.
      delete state.hotWatch[l.vin];
      delete state.holdNotified[l.vin];
    }
  }
  // Drop expired watches whose car never reappeared (sale completed).
  for (const [vin, exp] of Object.entries(state.hotWatch)) {
    if (Date.now() > exp + 10 * 60 * 1000) delete state.hotWatch[vin];
  }
  saveJson(STATE_PATH, state);

  const fresh = all.filter((l) => {
    if (!l.vin) return false;
    // A car we know is reserved is tracked but never announced.
    if (l.currentlyReserved) return false;
    const prev = seenMap[l.vin];
    if (!prev) return true; // never seen
    // Upgrade path: previously unverified, now confirmed buyable -> alert again.
    return prev === "possible" && statusOf(l) === "confirmed";
  });

  for (const l of all) {
    if (!l.vin) continue;
    const next = statusOf(l);
    // Never downgrade a confirmed record back to possible on a flaky check.
    if (seenMap[l.vin] !== "confirmed") seenMap[l.vin] = next;
  }
  saveJson(SEEN_PATH, seenMap);

  // Guard against NaN: a state.json written by an older version may not have
  // these keys, and `undefined + 1` silently poisons every later heartbeat.
  state.checksRun = (Number(state.checksRun) || 0) + 1;
  saveJson(STATE_PATH, state);

  if (isFirstRun) {
    await push(
      `Vehicle tracker started — watching for ${VEHICLE_LABEL}`,
      `Running from your home PC now. Seeded ${all.length} existing listings (Tesla ${sourceCounts.tesla} / CarMax ${sourceCounts.carmax} / Carvana ${sourceCounts.carvana}). Alerts fire for anything new from here.`
    );
    state.lastHeartbeat = Date.now();
    saveJson(STATE_PATH, state);
    pingHealthcheck(); // first run counts as a completed cycle too
    return;
  }

  // Notification failures must not abort the cycle. A transient Pushover outage
  // previously threw out of runCheck, skipping pingHealthcheck() below — which
  // would trip the EXTERNAL down-alert and tell you the tracker had died when
  // it was actually fine. Contain each failure and keep going.
  for (const l of fresh) {
    try {
      await notifyListing(l);
    } catch (e) {
      console.error(`Failed to send alert for ${l.vin}: ${e.message}`);
    }
  }
  try {
    await maybeHeartbeat();
  } catch (e) {
    console.error(`Heartbeat failed: ${e.message}`);
  }

  pingHealthcheck();
}

// External dead-man's switch: ping healthchecks.io after each completed cycle.
// If these pings stop (laptop off, internet down, script crashed), THEIR
// servers alert you — the one failure mode this script cannot report itself.
function isSet(v) {
  return (
    typeof v === "string" &&
    v.trim() !== "" &&
    !/REPLACE|PASTE-YOUR|your-uuid/i.test(v)
  );
}

function pingHealthcheck() {
  if (!isSet(config.HEALTHCHECK_PING_URL)) return;
  fetch(config.HEALTHCHECK_PING_URL)
    .then((r) => {
      if (!r.ok) console.error(`Healthcheck ping -> HTTP ${r.status}`);
    })
    .catch((e) => console.error(`Healthcheck ping failed: ${e.message}`));
}

async function maybeHeartbeat() {
  if (Date.now() - state.lastHeartbeat < HEARTBEAT_INTERVAL_MS) return;

  const seen = loadJson(SEEN_PATH, []);
  const c = state.lastSourceCounts;
  await push(
    "Vehicle tracker heartbeat",
    `Alive on home PC. ${state.checksRun} checks (${state.checksFailed} failed) since last heartbeat. Tracking ${seen.length} VINs. Latest per-source: Tesla ${c.tesla ?? "?"} / CarMax ${c.carmax ?? "?"} / Carvana ${c.carvana ?? "?"}. "ERR" = that source is failing.`
  );
  state.lastHeartbeat = Date.now();
  state.checksRun = 0;
  state.checksFailed = 0;
  saveJson(STATE_PATH, state);
}

// ============================================================
// ENTRY
// ============================================================

async function main() {
  if (process.argv[2] === "test-push") {
    await push(
      "Vehicle tracker test",
      `If this arrives, the home PC -> Pushover -> phone chain works. Sent at ${new Date().toISOString()}.`,
      { priority: "urgent" }
    );
    console.log("Test push sent. Check your phone.");
    return;
  }

  console.log(
    `Vehicle Tracker starting — base tick ${CHECK_INTERVAL_MS / 1000}s. Ctrl+C to stop.`
  );

  // Startup summary. Optional settings living in config.json get silently
  // blanked whenever the file is replaced wholesale — print their status so a
  // missing value is obvious on line one instead of discovered days later.
  const enabled = ["tesla", "carmax", "carvana"]
    .filter((s) => SOURCES[s] !== false && (s !== "tesla" || TESLA_SOURCE_APPLICABLE));
  console.log(
    `  Searching: ${MAKE} ${MODEL}${TRIM ? " " + TRIM : " (any trim)"}, ` +
      `${YEAR_MIN}${YEAR_MAX ? "-" + YEAR_MAX : "+"}`
  );
  console.log(`  Sources enabled: ${enabled.length ? enabled.join(", ") : "NONE — nothing will be checked"}`);
  if (SOURCES.tesla !== false && !TESLA_SOURCE_APPLICABLE) {
    console.log(`  (Tesla source auto-disabled: it can only search Teslas, and you set make=${MAKE})`);
  }

  const iv = config.SOURCE_INTERVAL_MINUTES || {};
  console.log(
    `  Intervals: Tesla ${iv.tesla ?? "every tick"}m / Carvana ${iv.carvana ?? "every tick"}m / CarMax ${iv.carmax ?? "every tick"}m`
  );
  console.log(
    `  Pushover: ${isSet(config.PUSHOVER_USER) && isSet(config.PUSHOVER_TOKEN) ? "configured" : "NOT CONFIGURED"}`
  );
  console.log(
    `  Healthcheck ping: ${isSet(config.HEALTHCHECK_PING_URL) ? "configured" : "NOT SET (no external down-alert)"}`
  );
  console.log(
    `  Chrome attach: ${config.CHROME_CDP_URL || "off (will launch its own browser — Tesla will be blocked)"}`
  );
  console.log(
    `  Tesla conditions: ${(config.TESLA_CONDITIONS || ["new", "used"]).join(", ")}\n`
  );

  // At boot the Startup folder launches this and Chrome at the same moment, and
  // Chrome's debug port takes a few seconds to open. Wait briefly for it rather
  // than racing, losing the attach, and running the first cycles on a blocked
  // fallback browser.
  if (config.CHROME_CDP_URL) {
    const deadline = Date.now() + 60_000;
    let attached = false;
    while (Date.now() < deadline) {
      try {
        const probe = await fetch(
          config.CHROME_CDP_URL.replace(/\/$/, "") + "/json/version"
        );
        if (probe.ok) {
          attached = true;
          break;
        }
      } catch {
        /* not up yet */
      }
      await new Promise((r) => setTimeout(r, 3000));
    }
    console.log(
      attached
        ? "  Chrome debug port is up.\n"
        : "  Chrome debug port did not come up within 60s — starting anyway; will keep retrying.\n"
    );
  }

  // Overlap guard. A Tesla cycle can now run long (session refresh + up to five
  // paced page loads), and if one exceeds the base tick the interval would fire
  // a second runCheck while the first is still going. Both would then read and
  // rewrite seen-vins.json independently — the later write clobbering the
  // earlier one, which loses dedupe state and produces duplicate alerts. Skip
  // the tick instead; the next one picks it up.
  let checkInFlight = false;
  let skippedTicks = 0;

  const safeRun = async () => {
    if (checkInFlight) {
      skippedTicks += 1;
      // Only complain if it becomes chronic — an occasional long cycle is fine.
      if (skippedTicks % 5 === 1) {
        console.error(
          `Previous check still running — skipped this tick (${skippedTicks} skipped so far). ` +
            "If this is frequent, raise CHECK_INTERVAL_SECONDS."
        );
      }
      return;
    }
    checkInFlight = true;
    const startedAt = Date.now();
    try {
      await runCheck();
    } catch (e) {
      state.checksFailed = (Number(state.checksFailed) || 0) + 1;
      saveJson(STATE_PATH, state);
      console.error(`Check failed: ${e.message}`);
    } finally {
      checkInFlight = false;
      const took = Date.now() - startedAt;
      if (took > CHECK_INTERVAL_MS) {
        console.error(
          `Check took ${Math.round(took / 1000)}s, longer than the ${CHECK_INTERVAL_MS / 1000}s tick.`
        );
      }
    }
  };

  // Run immediately, then on the interval.
  await safeRun();
  setInterval(safeRun, CHECK_INTERVAL_MS);
}

main();
