# Handoff

## Last verified: 2026-11-07 — work in progress

- `origin/main` at `d86ccfc` contains merged raid PR #38.
- Branch `feature/contested-territory` implements the owner-approved next step.
- D15 records scope: optional held site, recurring combat and protein hauls,
  free withdrawal forfeiting unfinished haul, opt-in automatic reinforcement.
  Temporary opportunities and soldier specialization are explicitly deferred.
- Draft engine/UI: first raid discovers a separate Territory tab; claim costs
  20 sugar per worker for 10–37 workers; 0.3 protein/s per holder accumulates
  for delivery after each 60-second contest. Oldest home cohorts recruited,
  random holder casualties, normal upkeep/starvation, aging on contest/withdraw.
  Saved seed/round/timer; no scout/expedition slot, no forced combat on safe play.
- Initial development build succeeds; existing tests and new territory coverage
  are running. Browser/release checks, README and final review remain.
  This is an unfinished checkpoint, not merge-ready.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
