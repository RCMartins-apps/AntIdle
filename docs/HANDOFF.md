# Handoff

## Last verified: 2026-11-08 — mobile exploration carousel

- Ongoing branch `ui/consistent-action-details` from merged main `82ff73b`
  (PR #47). Includes shared egg descriptions, independent Nest/Tasks amounts,
  scout batches, task help alignment, numeric chamber bonuses, concise expedition
  outcomes and unfinished-discovery badges. Prior engine baseline: 241 passing.
- Owner requested replacing the mobile picker with a carousel for comparisons.
  D27 supersedes D23's selector and D26's picker hints. Cards keep Discovery badges.
- Workers/scouts remain above a single card. Arrows, keyboard and horizontal
  swipes change destinations without altering the party. Arrows wrap; title and
  unlocked-position counter identify selection. Vertical gestures scroll; short
  or diagonal movement does not navigate. Single-destination arrows disable.
- Mobile navigation sits immediately above launch; desktop retains the grid.
  Tests use select_expedition to navigate the carousel instead of a native select.
- No engine/save changes. Verified development/production builds (Closure clean),
  mobile_explore_smoke at 320/390/844/1920px and discovery_badge_smoke at
  320/390/1920px. Includes arrows/wrap, keyboard, real Chromium touch gestures,
  vertical/jitter discrimination, locked routes, party amounts, costs and launches.
  Visually inspected the 390px Explore layout. Vertical scroll/pinch zoom remain
  available. Prior 241 engine tests were not rerun for this UI-only change.
- Changes committed/pushed on the ongoing branch; no new PR requested.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
