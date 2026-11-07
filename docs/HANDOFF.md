# Handoff

This is a replaceable snapshot. Verify it against Git and PR state on resuming.

## Last verified: 2026-11-07

- `origin/main` at `70642c5` contains merged Royal Nursery/resin PR #35.
- Branch `feature/royal-brood-rearing` implements the next prestige foundation.
  It is pushed; no PR was requested or created for this task.
- Standing branch/push/checkpoint instructions remain in AGENTS.md.

## Completed work

- Royal Nursery unlocks the capped Royal Nurse worker task and Raise Royal Egg.
- Sustained egg/larva/pupa care consumes stage-specific supplies; five nurses
  take ten simulated minutes. Fewer nurses/partial supplies slow progress;
  shortages or manual pause retain progress without unperformed feeding costs.
- Nest shows costs, stage, progress, feeding, ETA, pause/resume and shortage
  reasons. Adult upkeep precedes royal care, which precedes automatic worker eggs.
  Resource forecasts and full-storage direct feeding agree with the engine.
- A mature daughter replaces reserved housing and occupies the royal slot.
  Normal sugar upkeep applies; daughters can starve but use no worker-age cohorts.
  Saves retain progress, pause, staff, daughters, visibility and statistics;
  old saves get additive defaults. Maturation is awarded once.
- D12 and README document scope and current balance choices.

Validation completed:
- `sbt fastOptJS`, `sbt fullOptJS test`: all 152 engine tests pass, including
  twelve new royal brood cases; Closure reports zero errors/warnings.
- `tests/royal_brood_smoke.py` and `tests/royal_nursery_smoke.py`: pass at
  320, 390 and 1920px. Mobile rearing and ready cards visually inspected.
- `tests/browser_smoke.py`: passes at 320, 390, 767, 768, 844, 1024 and 1920px.
- Local Markdown links resolve and `git diff --check` passes.

## Next work and unknowns

- No founding, reset or Understanding is granted by queen rearing. Mature
  daughters remain in the nursery until a future founding feature uses them.
- Next design step: choose the first Understanding and founding/reset rules,
  then show what carries over before the player commits to a new generation.
- Challenge-colony objectives, rewards, completion/abandonment and interaction
  with ordinary prestige remain undecided. D2/VISION preserve the direction.
- Preserve unrelated untracked `work/` screenshots and `tests/__pycache__/`.
