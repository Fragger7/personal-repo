# Vehicle Tracker — test suite

Runs the REAL runCheck() from ../tracker.js against stubbed sources whose
payloads match the shapes verified live from Tesla, CarMax and Carvana.
Fixtures use a Model S Plaid because that is the shipped default, but the
config tests cover other makes and models too.

    cd test
    node suite.js          # 28 core checks
    node hold.js           # 9 checks for the on-hold / hot-watch lifecycle
    node config-tests.js   # 19 checks for vehicle config + source toggles
    node upgrade.js        # 7 checks for reserved-to-available alerting

Exits non-zero on any failure. Run this after changing tracker.js.

Covers: first-run seeding, new-listing detection, dedupe across cycles,
Carvana availability verification, isPurchasePending filtering, night pause
(including the healthcheck ping that must still fire while idle), source
failure handling, both failure-alert triggers (count and elapsed time),
recovery clearing state, alert priority calibration, browser page hygiene,
state-file integrity, the reserved-to-available upgrade path, the on-hold
hot-watch lifecycle, per-source enable/disable, configurable make/model/trim/
year matching, and backwards compatibility with older config files.

All four suites should report 0 failed. Run them after any change to tracker.js.
