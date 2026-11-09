# Handoff

## Work in progress: 2026-11-09 — upgrade/exploration balance definitions

- PR #54 merged the balance lab as `f7a29c5`; GitHub production build, engine,
  balance, report-packaging and phone/desktop browser tests all passed.
- Current branch `refactor/balance-unlock-definitions` starts from that main.
  Owner approved this maintainability pass; PR/merge not yet requested for it.
- Implemented UpgradeBalance catalog with structured reveal requirements, prices
  and help; saved UpgradeType IDs/order remain stable. UpgradeResearch shares
  visibility and validated one-time purchasing across UI, actions and simulator.
  Preserve earned visibility and existing Knowledge gating.
- Destinations now use named values and share level/discovery validation with
  departures. Supplies tiers pair levels/prices; milestone and discovery hints
  derive levels from their definitions. No intentional balance/save-schema change.
- docs/BALANCING.md maps editing locations, units, visibility versus affordability
  and iteration steps. Effects remain in mechanic modules; not a universal config.
- Existing 245 engine/4 balance tests passed after initial extraction. New unlock
  tests, final production browser check (including research-price boundary) and
  full six-run before/after comparison are in progress. Production fullOptJS passed.
  This checkpoint is not yet the final verified result.
- No waste mechanic/new Understanding or pacing changes are part of this work.
- Preserve untracked work/ and Python caches. Generated reports/logs are in /tmp.
