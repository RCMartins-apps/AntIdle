# Handoff

## 2026-11-24 — colony opening without sugar clicks

- `feature/remove-manual-sugar` replaces Gather Sugar with 10 founding sugar
  for two small-worker eggs. Founding Provisions adds 15 more. The queen can
  forage for one recovery egg when a colony has no workers or viable founding
  brood and cannot pay for an egg. Stocked post-research colonies retain their
  five-sugar emergency action.
- The simulator uses the same opening as the browser. Retained compatible saves
  keep their stock; an older empty opening can recover from the queen.
- Before integrating Brood Priority, engine, balance, production browser and
  packaged offline checks passed. Two balanced simulations (seeds 42 and 137)
  unlocked exploration at 30.3 simulated minutes and reached the first
  founding at 94.2 and 93.3 minutes, with zero starvation deaths.
- The branch includes Brood Priority from merged PR #111 and passed all 327
  game tests after integration. Rendered mobile screenshots were shared for the
  opening and empty-colony fallback.
  The separate `design/nest-refuse-pressure` prototype still needs a pacing
  playtest. See D17 and D72 for the opening rules.
