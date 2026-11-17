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

`prestige_smoke.py` is now a maintained gate (its historical XP/trail scenario was
replaced). It checks the staged founding transaction and second-ring research at
320/390/1280px. Run after fastOptJS, or with ANTIDLE_PRODUCTION=1 after fullOptJS.
The PR workflow runs it in a separate five-minute step.


The production CI gate also checks milestone research through actual expedition
returns and worker maturation, purchases all three upgrades, and checks the scout
duration preview at 390/1280px. Engine coverage is in MilestoneResearchSuite.

Permanent milestone checks also earn a resource tier through live production,
verify its ×1.01 multiplier, preserve an open goal across ticks, and reload lower
holdings/current-run totals without losing lifetime progress or saved peaks.
ColonyMilestonesSuite covers exact compounding, fixed costs, overflow, forecasts,
finite-haul refunds, automation, prestige and legacy peak-field defaults.

After fastOptJS, `python3 tests/polish_visual_smoke.py` checks compact resource
values, digit alignment, control sizing, settings and queue empty state at
390/1280px. NumberPresentationSuite covers boundaries, signs and tiny values.

After fastOptJS, `python3 tests/polish_log_smoke.py` checks significant-event
visibility, routine grouping and stable disclosure at 390/1280px. EventLogSuite
covers bounded retention, routine floods and old string-save compatibility.

After fastOptJS, `python3 tests/polish_memory_smoke.py` checks disclosure/map
restoration, new-colony fallback and invalid preference keys at 390/1280px.

After fastOptJS, `python3 tests/polish_map_smoke.py` checks active map study
progress, hidden site labels and mission completion at 390/1280px.

UI polish checks: after `sbt fastOptJS`, run
`python3 tests/polish_status_smoke.py` for shared activity states and real blockers
at phone/desktop widths. ActivityStatusSuite covers state classes and missing
construction materials. These checks use the development bundle.

Founding visibility regression: CI browser checks compare every visible resource
row with the fresh-game panel immediately after founding and after reload, and
repair an old untouched founding save without changing lifetime totals. The
prestige gate checks both introductory foundings at 320/390/1280px. Engine
ResourceVisibilitySuite covers all resource/caste flags, within-run retention,
permanent milestones and preserving active-run discovery history.

Mobile navigation: `python3 tests/mobile_navigation_smoke.py` checks 320/390px
bottom destinations, hidden unlocks, grouped screens, scroll restoration and
resizing to desktop. `select_tab` in browser_smoke navigates via visible controls
on both layouts; maintained production gates use it as well.

`python3 tests/mobile_resources_smoke.py` checks compact resource amounts, hidden
optional resources, full-capacity styling, depletion warnings, disclosure memory
and desktop resizing at 320/390px. Use `ANTIDLE_PRODUCTION=1` for both mobile
checks against fullOptJS. Resource-detail regressions explicitly expand the panel
through `expand_resources`; the compact-resource test verifies the default view.

`python3 tests/blocked_actions_smoke.py` uses the production bundle at
320/390/1280px to verify visible housing, scout/busy, queue, storage-capacity and
resource-shortage feedback. It also checks foodless egg orders and unfunded
chamber queueing remain enabled and action reasons are accessible descriptions.

The mobile-resource regression also checks the relocated catch-up control at
320/390/1280px: no decorative top header, no overlap, independence from resource
collapse, accelerated simulated ticks, and disappearance when stored time is empty.

`ANTIDLE_PRODUCTION=1 python3 tests/brood_layout_smoke.py` checks a single brood
heading, scoped feeding details, independent disclosures, and stable egg-button
positions through empty/large worker-and-scout queues, full brood, food pauses,
cancellation and live ticks at 320/390/1280px.

The resource regression repeatedly switches source timing between absent, full,
full-countdown and depletion states, including changing number widths. It verifies
unchanged panel/cell geometry in collapsed and expanded phone views and desktop
rows, plus warning colors and complete accessible estimate labels.

Resource timing geometry checks also use a wider Linux numeric font to reproduce
PR #89's bold-value wrap at 320px, and assert every compact value stays on one
line and fits its cell without clipping. The mobile-resource launcher honors
CHROMIUM_PATH; GitHub Actions uses the pinned Playwright browser by default.

`python3 tests/task_controls_smoke.py` checks the production task UI at
320/390/1280px: persistent small-worker folding and total count, matching right
aligned primary controls, mobile batch assignment, actual idle/default-pool
semantics, hiding the exhausted small-worker section and stable blocker geometry.
