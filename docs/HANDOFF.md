# Handoff

## Work in progress: 2026-11-10 — handcrafted exploration ring

- Main PR #55 merged as 790df82. Branch feature/hex-exploration starts there.
- User approved replacing XP with a fixed six-hex first ring, finite diminishing
  knowledge/deposits, useful exclusive scout work, and explicitly breaking saves.
- Current engine/UI builds: scout-only reveal/study missions, workers haul reserved
  finite cargo, overflow restored to sites, shared mission slots/aging, fixed map
  with selectable site cards. First-ring catalog is ExplorationMap.scala.
- XP removed from model and runtime gates; save version 7. Map discoveries/reveal
  counts replace XP levels. Dew Gatherers unlock after water study for renewable
  maintenance. Shared Paths draws from finite meadow stock rather than regenerating.
- Initial fastOptJS and balance/fastOptJS passed. Old exploration tests are being
  rewritten for the new rules; several fixtures are partially updated and the
  full test suite is NOT yet validated/compiling. New ExplorationSuite covers
  scout reservations, deterministic knowledge, budget/depletion and overflow.
- Balance policy adapted to scout missions and worker collection; first seeded
  pacing runs in progress. Mobile/browser verification and CI smoke update pending.
  This checkpoint is unfinished and not ready to merge. Continue implementation.
- D36/VISION record the approved direction. No outer rings implemented yet.
- Preserve unrelated work/ and Python caches; generated logs/reports are in /tmp.
