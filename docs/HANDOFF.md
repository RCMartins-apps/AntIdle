# Handoff

## Last verified: 2026-11-07

- Main `d9e755a` contains merged first prestige PR #37.
- Related branch `design/expedition-danger` now implements the approved raid;
  pushed with [PR #38](https://github.com/RCMartins-apps/AntIdle/pull/38) open
  against `main`. D14 and README record rules and balance.
- Latest owner requirement: oldest ants depart first, but casualties are random
  within the party. Reuses oldest-first home-worker cohort selection and samples
  individuals uniformly without replacement. Surviving expiry ticks are retained.

## Completed raid

- Rival Food Cache at exploration level 5: 10–37 workers, one scout, 120 seconds
  before extra-scout speed, 30 sugar per worker. Combat losses are guaranteed:
  2–4 of ten workers; 4–10 of 37. Protein range is 200–350 per ten workers,
  scaling linearly with party size; free storage caps actual retained income.
- Counts/certainty, rewards, costs, storage room and selected-cohort aging
  warnings are visible beside the action. In-flight parties retain risk text.
  Scouts have no combat losses, but overdue aging can kill scouts/survivors.
- Outcomes and casualty-selection seed are saved at departure. Same-tick
  launches use distinct seeds; reloads and delayed returns cannot reroll them.
  Combat deaths/cohorts are removed before overdue survivor aging, without
  double-counting. Replayed completions grant nothing. Existing safe routes
  keep their behavior. Queen's Routine replaces ordinary worker deficits.
- Raid losses have separate run/lifetime statistics and prestige retention.
  Existing parties default to no raid outcome; no save version bump needed.

Validation:
- `sbt fastOptJS fullOptJS test`: 175 engine tests pass, including eleven new
  raid cases; production Closure reports zero errors/warnings.
- `tests/raid_smoke.py`: passes at 320, 390 and 1920px for gates, risk/costs,
  oldest workers, saves, returns, storage/age warnings and automatic replacement.
- `tests/progression_smoke.py`: passes at 320, 390 and 1920px, including existing
  carcass/scout/XP/save/limit checks. Mobile raid card visually inspected.
- Local Markdown links resolve; whitespace checks pass.

## Next work

- Brood-care progression remains deferred until population pressure proves
  meaningful; recurring outpost supply costs are only an unadopted suggestion.
- Further introductory Understandings, later DNA milestones and challenge
  founding remain separate future work. Tune raids using actual play feedback.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
