# Handoff

## Last verified: 2026-11-10 — handcrafted exploration ring

- Main baseline: PR #55 merged as 790df82. Completed feature/hex-exploration branch
  starts there; implementation authorized, no PR/merge requested for this branch.
- Save version 7 intentionally rejects older saves (owner approved). XP and level
  reward inflation are removed. Fixed six distance-one hexagons use axial coords,
  a deterministic reveal order, finite 50/30/20% scout-study Knowledge and deposits.
- Scouts exclusively reveal/study (1–3 crew; speed versus supplies/upkeep), plus
  existing trail/raid guiding. Workers collect resources, not Knowledge. Mission
  slots, scout reservations, age expiry and default-worker borrowing are shared.
- Ring Knowledge totals 7,000, exceeding all current research/supplies/outpost
  establishment/extensions combined (actual-price test). Essential progression
  requires no combat/lucky drops. Dew discovery opens capped renewable gatherers.
  Finite cargo reserves stock + storage; overflow stays at source, duplicate same-
  tick parties each return once, scout completion cannot replay. Shared Paths
  allocates from finite meadow stock once. Nectar hauls remain profitable at every
  useful crew size after removing XP multipliers (10 sugar/worker supplies).
- Phone/desktop map replaces carousel: fog, selectable site, visible costs/crew,
  finite reserves, danger/aging warnings, storage blocks, persistent exhausted hexes.
  Only current site missions show; selection survives ticks. No outer rings or
  new Understanding included. D36/VISION and docs/EXPLORATION.md record direction.
- Verified fullOptJS (zero Closure errors/warnings), all 252 engine +4 simulator
  tests, Python report packaging, six CI-classifier tests, production CI browser
  at 390/1280px and map smoke at 320/390/1280px including >=44px hex touch targets.
  All 11 balance checkpoint families load in production at phone/desktop widths.
- Fresh six-run first-ring benchmark: all policies/seeds reached founding with
  zero starvation or combat; four reached two foundings in four-hour budgets.
  Entire Knowledge/resource bounds checked. tools/balance/MAP_RESULTS.md records
  timings and limitations; old XP results explicitly historical. Human normal-speed
  feedback still needed; bot times don't certify fun or optimal strategies.
- PR workflow includes maintained map smoke. tests/README.md identifies supported
  checks; older optional XP/carousel browser scripts remain historical references,
  not supported v7 commands. New GitHub PR CI has not yet run.
- Preserve unrelated work/ and Python caches; generated reports/screenshots stay
  in /tmp. No generated artifacts staged.
