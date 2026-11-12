# Handoff

## Last verified: 2026-11-12 — Shared Paths regression review

- Artwork provenance docs PR #67 merged as c0d5764 after docs CI passed. Attempt to
  queue auto-merge returned clean-status because checks were already complete;
  performed a confirmed squash merge. Normal authorized auto-merge workflow stands.
- Owner says first prestige's pheromone trail feels effectively useless and supplied
  a No nectar source screenshot. Inspected current and pre-map engine code.
- Confirmed map regression: only the first Nectar Meadow study can create a trail
  source, only if Shared Paths is owned at that instant. Reserves 1,200 from the
  finite 20,000 nectar deposit, never replenishes, and misses late purchases. Old
  repeat expedition discovery is gone. At 10 workers after first founding, 1,200
  lasts about 136 simulated seconds when storage permits. Generic empty copy hides
  unstudied/missed/exhausted states. Cannot infer exact screenshot state without save.
- docs/trail-understanding-review corrects stale repeat-discovery README text and
  records diagnosis/proposed redesign in docs/IDEAS.md. No gameplay changed.
- Recommendation, NOT approved: maintained worker/scout collection from the studied
  Meadow's whole remaining finite deposit, without repetitive launches/mission-slot
  use, with late-purchase reconciliation and site-local controls. Keep finite
  deposits and reserved cargo accounting; exact staffing/rates/costs still undecided.
- Documentation checks only: git diff --check, relative paths. No game rebuild/tests
  warranted for this review. Commit/push by default; this review PR/merge not asked.
- Preserve unrelated work/ and Python caches. Original art remains in some-assets;
  the two old code experiments remain in archive/* tags. Active Main ruleset enforces
  required checks for authorized auto-merge; do not poll after a confirmed queue.
