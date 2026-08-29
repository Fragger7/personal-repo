# Vehicle Tracker — Setup Guide

Monitors **Tesla, CarMax and Carvana** for a vehicle you specify — any make,
model, trim and year range — and sends a push notification to your phone the
moment one appears that you can actually buy.

Originally built to find a 2026 Model S Plaid: dealers were asking $140–160k
while Carvana and CarMax listed them at real market (~$112–125k), and those sold
within minutes. It ships configured for that search, but the vehicle is entirely
configurable (see **Searching for a different vehicle** below).

---

## Before you start — please read this bit

**1. This is not an app.** It is a script that runs on a Windows PC that stays
powered on, alongside a dedicated Chrome window. Budget ~30 minutes for setup
and some comfort with a command prompt.

**2. Don't lower the check intervals.** All three sites rate-limit aggressively.
Getting the defaults to work took days of being blocked and adapting. If a lot
of people run this fast, the sites will tighten their defences and it stops
working *for everyone*, including you. The shipped intervals are deliberately
conservative.

**3. It can break.** These sites change their markup without warning, and Tesla
sits behind Akamai bot protection that periodically locks us out. The tracker
tells you when a source fails rather than going quiet, but expect occasional
maintenance.

**4. Nothing is guaranteed.** A listing can be gone in under a minute. This
improves your odds; it does not reserve you a car.

---

## What you need

| Requirement | Notes |
|---|---|
| Windows PC | Must stay on. A spare laptop is ideal. |
| Node.js | Free — https://nodejs.org (choose the LTS version) |
| Google Chrome | Already installed on most machines |
| Pushover account | ~$5 one-time per platform after a 30-day trial |
| ~30 minutes | One-time setup |

---

## Step 1 — Install Node.js

Download the **LTS** version from https://nodejs.org and run the installer,
accepting the defaults.

Confirm it worked. Press `Win + R`, type `cmd`, press Enter, then:

```
node --version
```

You should see a version number like `v22.x.x`. If you get "not recognized",
close the window, open a new one, and try again.

---

## Step 2 — Put the files in place

Create a folder at **`C:\vehicle-tracker`** and copy in:

```
C:\vehicle-tracker\
├── tracker.js
├── config.json
├── start-chrome-debug.bat
├── start-tracker.bat
└── test\            (optional, for verifying changes)
```

Avoid OneDrive-synced folders — sync can interfere with the tracker's state
files.

---

## Step 3 — Install the browser automation

In a command prompt:

```
cd C:\vehicle-tracker
npm install playwright
npx playwright install chromium
```

The second command downloads a browser (~150MB) and takes a minute or two.

---

## Step 4 — Set up push notifications

The tracker uses **Pushover** rather than email or SMS, because it supports
alerts that keep ringing until you acknowledge them — which matters at 3am.

1. Sign up at https://pushover.net
2. Install the Pushover app on your phone and log in
3. Copy your **User Key** from the pushover.net dashboard
4. On pushover.net, click **Create an Application/API Token**, name it
   "Vehicle Tracker", and copy the **API Token** it gives you

The phone app is free for 30 days, then a one-time $4.99 per platform. Not a
subscription.

---

## Step 5 — Edit config.json

Open `config.json` in Notepad. You **must** change these four:

```json
"PUSHOVER_USER":  "your User Key from step 4",
"PUSHOVER_TOKEN": "your API Token from step 4",
"ZIP":            "your ZIP code",
"REGION":         "your state abbreviation, e.g. NH"
```

Also set `LAT` and `LNG` to your rough coordinates — Tesla's API requires them.
Search "latitude longitude [your town]" and paste the numbers in. Being a few
miles off is fine.

**Optional settings:**

| Setting | Default | What it does |
|---|---|---|
| `NIGHT_PAUSE` | false | Set true to stop checking overnight |
| `NIGHT_START_HOUR` / `NIGHT_END_HOUR` | 0 / 6 | Quiet window if pause is on |
| `SOURCE_INTERVAL_MINUTES` | 20 / 5 / 4 | Minutes between checks. **Please don't lower these.** |
| `HEALTHCHECK_PING_URL` | placeholder | Optional — see step 8 |

---

## Searching for a different vehicle

The tracker isn't hardwired to any particular car. Edit the `VEHICLE` block:

```json
"VEHICLE": {
  "make": "Tesla",
  "model": "Model S",
  "trim": "Plaid",
  "yearMin": 2026,
  "yearMax": null,
  "teslaModelCode": "ms",
  "teslaTrimCodes": ["PLD1"]
}
```

| Field | Notes |
|---|---|
| `make` / `model` | As the sites spell them — "Tesla" / "Model S", "BMW" / "M5" |
| `trim` | Set to `null` to match **any** trim |
| `yearMin` | Earliest model year to alert on |
| `yearMax` | `null` for no upper limit |
| `teslaModelCode` | Tesla source only: `ms`, `mx`, `m3`, `my` |
| `teslaTrimCodes` | Tesla source only. Leave `[]` if unsure — it just means no server-side filter |

**Examples:**

```json
"VEHICLE": { "make": "BMW", "model": "M5", "trim": "Competition", "yearMin": 2024 }
```
```json
"VEHICLE": { "make": "Tesla", "model": "Model X", "trim": null, "yearMin": 2025,
             "teslaModelCode": "mx", "teslaTrimCodes": [] }
```

Two things worth knowing:

- **Trim matching fails open.** If a listing's trim is blank or unrecognisable,
  you get alerted anyway. A false positive costs a glance; a false negative
  costs the car.
- **The Tesla source only searches Teslas** and disables itself automatically
  for any other make. That's expected, not a fault — CarMax and Carvana still
  cover it.

---

## Turning sources on and off

```json
"SOURCES": { "tesla": true, "carmax": true, "carvana": false }
```

A disabled source makes zero requests. Useful if you only care about one site,
or if one is giving you trouble and you'd rather not have the failure alerts.

The startup summary tells you exactly what's active:

```
  Searching: Tesla Model S Plaid, 2026+
  Sources enabled: tesla, carmax, carvana
```

> A note on night pause: a car listed at 2am is often gone by 6am, and that's
> arguably when you have the least competition. Pausing reduces load on the
> sites but genuinely costs you coverage. Your call.

---

## Step 6 — Start it up

**Order matters.** Chrome first, then the tracker.

1. Double-click **`start-chrome-debug.bat`**
   - A Chrome window opens on Tesla's inventory page
   - Wait for the green **SUCCESS** message in the black window
   - If it says FAILED, close *all* Chrome windows and run it again
   - **Leave this Chrome window open** — the tracker depends on it

2. Double-click **`start-tracker.bat`**

You should see something like:

```
Vehicle Tracker starting — base tick 60s. Ctrl+C to stop.
  Intervals: Tesla 20m / Carvana 5m / CarMax 4m
  Pushover: configured
  Chrome attach: http://127.0.0.1:9222
Browser: attached to your running Chrome at http://127.0.0.1:9222
Tesla (used) [trim filter]: scanned 110/110 unique listings in 1 page(s)
Carvana: HTTP 200 (via browser)
CarMax: HTTP 200 (via browser), html length=1425881
[11:42:31 PM] Source counts: {"tesla":0,"carmax":0,"carvana":0}
```

`Source counts: 0` is correct — it means all three were checked and nothing
matching your search is currently listed. You'll also get a "tracker started" push confirming
notifications work.

**Test the notification path:**

```
node tracker.js test-push
```

Your phone should buzz within seconds.

---

## Step 7 — Keep it running after a reboot

Press `Win + R`, type `shell:startup`, press Enter. Drop shortcuts to **both**
batch files in that folder. `start-chrome-debug` sorts alphabetically before
`start-tracker`, which is the order you want.

Also set the PC to never sleep: **Settings → System → Power → When plugged in,
put my device to sleep → Never.**

---

## Step 8 — Optional: know if the tracker dies

The tracker can't tell you it's dead. An outside watchdog can.

1. Sign up free at https://healthchecks.io
2. Create a check named "Vehicle Tracker", period 5 min, grace 10 min
3. Copy its ping URL and paste it into `HEALTHCHECK_PING_URL` in config.json
4. In healthchecks.io, add the **Pushover** integration so down-alerts reach
   the same app

Now if the PC loses power or the internet drops, you get told.

---

## What the alerts look like

Alert titles use whatever vehicle you configured, so a BMW search says
"New BMW M5 Competition" rather than anything Tesla-specific.

**A car you can actually buy** — Emergency priority, re-rings every 60 seconds
until you acknowledge it in the app:

```
New Tesla Model S Plaid: $112,000
Tesla • 2026 Plaid • 3,200 mi • Los Angeles, CA
Use delivery ZIP: 90045
https://www.tesla.com/ms/order/...
```

That ZIP line matters: Tesla hides a listing unless the delivery ZIP you enter
is near the car.

**A car under another buyer's checkout hold** — high priority, sent once:

```
Tesla Model S Plaid ON HOLD: $124,990
Carvana • 2026 Plaid • 5,379 mi
Another buyer has it in checkout. Their hold expires in ~14 min (2:26 PM).
Checking every minute — you'll get an urgent alert the second it frees up.
```

Carvana gives buyers a short exclusive checkout window. **Holds frequently
expire without the sale completing.** When one does, the tracker switches to
60-second checks and alerts you immediately.

**A source is failing** — normal priority, so you know coverage is degraded.

---

## Reading the log

| Line | Meaning |
|---|---|
| `Source counts: {"tesla":0,...}` | Checked successfully, nothing found. Normal. |
| `"tesla":"ERR"` | That source failed. Others still running. |
| `"tesla":"skip"` | Not due this cycle — normal with staggered intervals. |
| `scanned 110/110 unique listings` | Full coverage confirmed. |
| `— INCOMPLETE, N listings not checked` | Real gap. Tell me. |
| `already being purchased` | Found a match but it's reserved. Being watched. |

---

## Troubleshooting

**"Could not attach to Chrome" / Tesla always 403s**
The debug Chrome isn't running. Close all Chrome windows, run
`start-chrome-debug.bat`, wait for SUCCESS, then restart the tracker.

**Chrome debug says FAILED**
Chrome background processes are holding the port. Close every Chrome window,
check Task Manager for stray `chrome.exe` processes, then retry.

**No notifications at all**
Run `node tracker.js test-push`. If nothing arrives, check your Pushover keys
in config.json and that notifications are enabled for the app on your phone.

**One source shows ERR for hours**
Usually rate limiting; it retries and recovers on its own. If it persists for
more than a day, the site probably changed its markup.

**Everything shows ERR**
Check the PC's internet connection and that the debug Chrome is still open.

---

## Verifying it still works after any change

```
cd C:\vehicle-tracker\test
node suite.js          # 28 checks on the core logic
node hold.js           # 9 checks on the hold / hot-watch behaviour
node config-tests.js   # 19 checks on vehicle config and source toggles
```

Both should report `0 failed`.

---

## A last word

This finds cars faster than the alternatives and — unlike them — confirms a car
is genuinely purchasable before waking you up. It does not guarantee you'll get
one. Have your financing pre-approved and your accounts set up *before* an alert
lands, because the gap between notification and someone else's checkout hold is
often measured in minutes.

Good luck.
