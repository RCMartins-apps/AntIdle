# Handoff

## 2026-11-24 — colony opening without sugar clicks

- `feature/remove-manual-sugar` replaces Gather Sugar with 10 founding sugar
  for two small-worker eggs. Founding Provisions adds 15 more. The queen can
  forage for one recovery egg when a colony has no workers or viable founding
  brood and cannot pay for an egg. Stocked post-research colonies retain their
  five-sugar emergency action.
- The simulator uses the same opening as the browser. Retained compatible saves
  keep their stock; an older empty opening can recover from the queen.
- Verified 321 engine tests, three balance tests, the production browser gate at
  390/1280 px and the opening/recovery flow at 320/390/1280 px. Rendered mobile
  screenshots were shared for the new opening and empty-colony fallback. See
  D17 and D72 for behavior.
- Related work is separate. [Brood Priority PR #111](https://github.com/RCMartins-apps/AntIdle/pull/111)
  has auto-merge enabled. `design/nest-refuse-pressure` contains the playable
  refuse prototype; full-run tuning remains open.
