# Handoff

## Last verified: 2026-11-13 — Shared Paths automation and linear crews complete

- Branch feature/automated-collection-trails starts at c0d5764 (merged artwork docs
  PR #67). Owner approved automation, one scout per ten workers rounded up, ±5
  controls, and linear collection with 5–50-worker crews. No gameplay PR requested.
- Safe studied routes now automate via exploration.collectionTrails. Each route
  has its own 5–50 target, guiding scouts and normal home workers/upkeep/aging.
  Uses the shared finite deposit and respects reserved manual cargo/storage;
  no mission slots, new resources or Knowledge. Raids remain explicit manual acts.
- Final linear curve preserves the former five-worker haul, avoiding weaker early
  collection. Trails run at 60% of equivalent manual gross rate, charge proportional
  ongoing sugar and normal upkeep, pause for storage/sugar/scouts, release on
  depletion and recognize sites studied before buying the Understanding.
- Site-local ±5 controls show assigned workers, rates, supplies and scouts. The
  native input snaps visibly to valid groups. Missing guides/storage pause safely.
  Resin now has a real income estimate. Legacy unused nectar is restored once on
  load and old staff released; in-flight cargo and version 7 saves are preserved.
- Final validation on 2026-11-12 before environment restart: 260 engine tests and
  4 balance tests passed; fullOptJS finished without Closure errors/warnings.
  Maintained production CI browser checks passed at 390/1280px, production map and
  trail checks at 320/390/1280px, and offline cold-open/update checks at 390/1280px.
  Inspected the 320px trail panel. Current source retains that verified state.
- All six final seeded pacing runs reached first founding (95.2–143.2 simulated
  minutes), four reached a second within four hours; no starvation or negative
  sampled resources. See tools/balance/TRAIL_RESULTS.md for results/limitations.
  Earlier 10-worker/80% tuning was discarded after slowing small-colony progress.
- docs/COLLECTION-TRAILS.md records exact rates/rules; D42, VISION, README, balancing
  guide and tests documentation updated. This completes the earlier checkpoint.
  New GitHub PR/CI not run yet. Final commit/push pending in this handoff snapshot.
- Environment restarted on 2026-11-13. Working files and checkpoint survived, but
  /tmp build/browser logs and screenshots did not. Prior successful tool outputs
  are in conversation history; checked current diff and documentation, no code
  changes after the completed validation. Do not claim those temporary files exist.
- Preserve unrelated work/ and Python caches. Follow Main rules and native squash
  auto-merge only for explicitly authorized PR/merge requests; no CI polling after
  a confirmed queue. Territorial contest crews remain a separate unchanged system.
