# Handoff

## Last verified: 2026-11-07 — staged brood and founding workers

- Branch `design/brood-development` continues from merged main `0803328` (PR #39).
- Implemented approved D16: small founding adults with exactly 50% task output
  and upkeep, saved 30–40 minute lifespans, and research switching future eggs
  to standard workers. Existing adults and already-laid brood retain their type.
- Queued, paced laying replaces instant batches; eggs/larvae/pupae share brood
  places, larvae consume sugar/protein, and capped nurse jobs supply care.
  Queen's Routine uses the same pipeline. Nearby scraps provide early protein.
  Fresh games/prestige start small; legacy saves keep standard-worker access.
- Opening simulation unlocks standards within five simulated minutes and hatches
  the first within ten, with over fifteen minutes before earliest founding deaths.
  This verifies a deliberate strategy, not every allocation or final balance.
- Engine suite: 203 passing tests, including 15 new pipeline tests. Development
  and production builds pass. Browser checks pass at 320/390/1920px for the new
  pipeline, lifecycle, progression and territory; general UI passes 320–1920px.
  Raid and prestige regressions also pass at 320/390/1920px. Final builds pass.
- README documents costs, timings, jobs and save behavior. Balance remains
  adjustable; temporary food opportunities and soldier specialization are deferred.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
