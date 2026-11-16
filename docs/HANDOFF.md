# Handoff

## 2026-11-16 — stable brood panel

- `polish/stable-brood-panel` starts at main 80525f0 (merged #86). Calendar/header
  cleanup is separately in PR #87 with auto-merge enabled. Owner requested this
  brood implementation/follow-up; ordinary branch push is authorized.
- One Brood heading now serves the collapsible section; its independent [i]
  holds help, including accurately scoped worker/scout larval care. The duplicate
  title and always-visible worker-only feeding line are removed.
- Separate worker/egg-order, introduced scout-order and ordinary brood-place
  counters use tabular numbers. Two-line laying status space and an always-present
  cancel control (disabled when empty) keep normal queue updates from moving egg
  buttons. BroodProgression.layingStatus shares the existing status logic; the
  complete status wrapper remains compatible. No simulation/balance changes.
- All 306 engine tests and development/production builds passed, zero Closure
  warnings/errors. Focused browser checks passed 320/390/1280px: count/status/full/
  food-pause/cancel transitions, one heading, correct feeding help and independent
  disclosures and hidden founding-phase scout counters. Existing production UI
  checks passed 390/1280px. Syntax and git diff --check passed. Regression added
  to CI with a three-minute timeout. Preserve unrelated work/ and Python caches.
