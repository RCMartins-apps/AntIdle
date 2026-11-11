# First-prestige playtest follow-up — completed 2026-11-11

The owner approved this sequence and authorized PR creation and merging. Each
implementation PR passed its final CI before merging. The game retains its
handcrafted finite exploration map, balance and save format.

1. [#58: Egg ordering](https://github.com/RCMartins-apps/AntIdle/pull/58):
   orders reserve available housing regardless of current sugar/protein; actual
   laying waits for resources. The balance runner uses the same updated API.
2. [#59: Task selection and collection controls](https://github.com/RCMartins-apps/AntIdle/pull/59):
   default selection survives new options; collection controls require a genuinely
   unlocked deposit at the selected site. Storage and staffing blockers stay visible.
3. [#60: Compact Nest](https://github.com/RCMartins-apps/AntIdle/pull/60):
   hide resource/task UI for extinct small workers; collapse all ordinary Brood
   controls; show the existing single queue only on Nest on mobile. Desktop access
   remains unchanged. Disclosure state survives live ticks.
4. [#61: Explore controls and research](https://github.com/RCMartins-apps/AntIdle/pull/61):
   direct 1/2/3 scout buttons below the map; Expedition Supplies research moves to
   Upgrades with its existing saved ranks, prices and eligibility rules.
5. [#62: Royal care](https://github.com/RCMartins-apps/AntIdle/pull/62):
   lay a royal egg without assigned nurses, paying the normal laying cost and
   reserving nursery/housing capacity. Care waits without progress loss or care
   spending. Explicit Royal Nurse counts replace the ambiguous worker requirement.
6. [#63: Visual map](https://github.com/RCMartins-apps/AntIdle/pull/63):
   inline SVG terrain symbols, short labels and distinct terrain colors. Full names
   remain in the selected card and accessible button labels. Unknown sites stay
   hidden; selection/exhaustion remain explicit. A separate canvas is unnecessary
   for this pass and remains an option for future rendering needs.

## Verification

Engine regressions cover unfunded orders, mixed worker/scout housing reservations,
FIFO, persistence, exact laying costs, nurse-free royal laying and care resumption.
Browser regressions cover live task unlocks, collapse stability, mobile queue
visibility, selected-site prerequisites, scout button placement, supply purchases,
royal care waiting, map states, finite deposits and founding/reset flows.

Local final checks: 254 engine tests, 4 balance tests, fastOptJS, maintained UI/map
scenarios at 390/1280px and map checks at 320/390/1280px. Compared phone screenshots
before/after the terrain pass. Final production CI includes packaging, browser,
offline cold launch and deferred/failed update checks for each implementation PR.
Automated checks do not measure whether pacing is fun; the owner's next full run
should assess feel and clarity on the deployed version.
