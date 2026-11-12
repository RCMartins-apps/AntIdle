# Handoff

## In progress: 2026-11-12 — collection trail automation and linear crews

- Baseline c0d5764 includes merged artwork docs PR #67. Owner approved Shared Paths
  automation, one scout per ten workers rounded up, ±5 controls, and linear manual
  exploration with 5–50-worker crews. No gameplay PR/merge requested yet.
- feature/automated-collection-trails implements safe route-specific collection
  targets in exploration.collectionTrails, ordinary home task workers/aging and
  additive guide reservations. Uses common finite deposits and manual storage/cargo
  reservations; no mission slots, new resources or knowledge. Raids remain manual.
- Manual yields are linear anchored at former ten-worker yield; min/max/step enforced
  in engine and controls. Raid yield no longer caps at 37; previous 10–37 risk curve
  stays, bounded at larger crews. Territorial contests remain their separate system.
- Automation runs at 80% manual gross rate, charges proportional ongoing sugar and
  normal upkeep, pauses at storage/guide/supply shortages and releases on depletion.
  Site-local controls show rates/supply costs/scout requirements. Resin now has a
  real income estimate. Old unused nectar is restored once on load; no save reset.
- 260 game tests passed after updating old one-worker expedition fixtures and yields.
  Balance-tool integration and browser/production validation are still running.
  This is a checkpoint, not yet ready to merge. Logs /tmp/antidle-trails*.
- docs/COLLECTION-TRAILS.md records rules, rate math and tuning. Still need finalize
  other stale docs, verify all balance/browser/production checks and inspect phone
  layouts. Keep finite deposits; do not restore the old recurring nectar source.
- Preserve unrelated work/ and Python caches. Follow active Main CI rules and native
  squash auto-merge only for explicitly authorized PR/merge requests.
