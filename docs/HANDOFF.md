# Handoff

## Work in progress: 2026-11-10 — handcrafted exploration ring

- Main PR #55 merged as 790df82. Branch feature/hex-exploration starts there;
  first incomplete checkpoint f49687a pushed. Implementation authorized, no PR yet.
- Version 7 removes XP/level rewards and intentionally rejects old saves (owner
  approved). First ring: fixed six axial-coordinate hexes, deterministic reveals,
  scout-only studies (50/30/20% of finite site Knowledge), finite worker deposits.
- Scouts have exclusive reveal/study actions, 1–3 crews with faster trips/higher
  supplies, normal local upkeep and fixed-expiry travel ages. Guides also matter
  for Shared Paths and optional raids. Workers collect; no Knowledge from hauls.
- Map knowledge total 7,000 exceeds all current research/supplies/outpost-extension
  costs combined (tested from real prices). Essential deposits are safe; cargo
  reserves source + free storage, overflow stays at source, identical simultaneous
  hauls each return once. Water discovery opens capped Dew Gatherers for renewable
  maintenance. Shared Paths allocates once from finite meadow reserves.
- Responsive map and single selected-site card replace carousel. Costs/counts,
  remaining knowledge/resources, danger/aging and exhausted sites stay visible.
  Full/depleted/unstudied sites don't expose misleading available collection.
- Initial production builds, 251 engine +4 simulator tests and phone/desktop
  production/feature map browser suites passed. Final nectar audit lowers supplies
  to 10 sugar/worker (large hauls must stay profitable after removing XP bonuses);
  252 engine +4 simulator tests pass. Final production rebuild, six-policy/seed
  pacing rerun and release browser rerun completing now; do not call final yet.
- README/BALANCING/EXPLORATION document current rules; historical XP benchmark
  explicitly marked outdated. Older optional XP/carousel browser scripts remain
  historical; current CI and map smoke are maintained and included in PR workflow.
- No outer ring/new Understanding implemented. D36/VISION record future direction.
- Preserve unrelated work/ and Python caches; generated artifacts stay /tmp.
