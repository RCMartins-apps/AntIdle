# Handoff

## Last verified: 2026-11-09 — upgrade/exploration balance definitions

- Main baseline: PR #54 merged the balance lab as `f7a29c5`; its GitHub production
  build, engine, balance, packaging and phone/desktop browser checks all passed.
- Completed branch `refactor/balance-unlock-definitions` starts from that main.
  Owner approved implementation; no PR/merge requested for this branch yet.
- UpgradeBalance centralizes upgrade prices/help and structured reveal conditions.
  UpgradeType save IDs and display order stay stable. UpgradeResearch shares
  visibility and validated one-time purchasing across UI, actions and simulator;
  engine rejects locked/unaffordable/repeated purchases. Earned visibility and
  Knowledge gating remain unchanged, including legacy/load reconciliation.
- Destination tuning uses named arguments; UI and departure checks share the
  level/discovery gate. Supplies levels/prices share tier definitions; milestone
  and discovery hints derive levels rather than repeating numbers.
- docs/BALANCING.md maps editing locations, units and effect modules, explains
  visibility versus affordability and gives a baseline/playtest workflow. See D35.
  Effects stay with mechanics; no universal configuration framework added.
- Verified production fullOptJS (zero Closure errors/warnings), 251 engine tests
  (six new catalog/requirements/purchase/persistence/route/supplies tests), four
  balance tests, and production browser checks at 390/1280px. Browser CI now also
  checks research visible-but-unaffordable, enabling at its price, charging once
  and hiding the purchased action; advanced Knowledge research stays hidden.
- Fresh before/after six-run comparisons matched complete Run results exactly:
  milestones/events/samples/waits/deaths/decisions. All 100 checkpoint states
  matched except export timestamps. No balance/content/save-schema changes.
  Generated reports/logs remain /tmp; new GitHub PR CI not run yet.
- Wider effect/chamber/brood consolidation, waste, new Understandings and actual
  pacing changes remain separate work. The ≥80% enjoyment goal needs real feedback.
- Preserve unrelated untracked work/ and Python caches; none are staged.
