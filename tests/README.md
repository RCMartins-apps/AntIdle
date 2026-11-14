# Current verification

The maintained default suite for save version 7 is:

```sh
sbt fullOptJS test balance/test
python3 -m unittest discover -s tests -p 'balance*_test.py'
python3 tests/ci_changes_test.py
python3 -m unittest discover -s tests -p 'package_site_test.py'
python3 tests/ci_browser_smoke.py
ANTIDLE_PRODUCTION=1 python3 tests/map_browser_smoke.py
python3 tests/offline_browser_smoke.py
```

Install browser dependencies from requirements-browser.txt and install Playwright
Chromium first. Both browser scripts start their own localhost server and use
isolated contexts. The CI gate covers research, task geometry, live disclosures,
map/scout launch, prestige and persistent resource visibility at 390/1280px. The
map gate checks 320/390/1280px, reveal/study costs and reservations, deterministic
Knowledge, selection stability, depletion, storage blocking and reload. It also
checks sequential standard supply purchases and the purchased-upgrade disclosure
(default collapse, live purchase/tick stability and reload persistence), plus trail
assignment in groups of five, per-route scout reservations, finite manual/automatic
coexistence, resource/scout pauses, site-local UI and saved assignments.

For a development bundle, `sbt fastOptJS` plus `python3 tests/map_browser_smoke.py`
works without ANTIDLE_PRODUCTION. Production checks must use a fresh fullOptJS.
Failure screenshots use ANTIDLE_ARTIFACTS, which CI uploads on failure.

To check generated balance-report controls and importable milestone saves:

```sh
python3 tests/balance_browser_smoke.py /tmp/balance-output
```

Scala suites cover current gameplay and finite-map rules. The older optional
browser scripts in this directory capture previous prototype flows. In particular,
XP, old destination cards/carousel and version-6 fixtures have been superseded;
those scripts are historical references, not supported v7 regression commands.
Port their fixtures/selectors before using them for a new feature. browser_smoke.py
also supplies common server/save/layout helpers imported by the maintained gates;
its old standalone scenario list is historical. Do not claim every old browser
script passes simply because the current CI and Scala suites do.

Offline checks exercise real service workers in regular temporary profiles and
the actual deployment package, including multi-window updates and failed releases.
See [OFFLINE.md](../docs/OFFLINE.md).

The production CI browser gate checks brood-list egg/larva/pupa SVGs, caste and
legacy entries, and a live stage transition in the phone disclosure/desktop tooltip.

The map browser gate also covers aphid relocation: visible costs, reserved housing,
saving an in-flight group and receiving it without regenerating insects or Knowledge.

Build-version checks compare the compiled commit link with the checkout and confirm
Settings/footer agreement and reload stability. The offline gate also checks that
the label remains available offline and unchanged while a new release waits.
