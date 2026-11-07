# Handoff

This is a short replaceable snapshot, not a task queue or a permanent change log.
Verify it against Git and PR state at the next session's start.

## Last verified: 2026-11-07

- `origin/main` at `9cabeaa` includes outpost progression from PR #34.
- Branch `feature/royal-nursery` carries forward the previously pushed prestige
  design clarification, which is not yet in main.
- Durable context and automatic branch/push/checkpoint instructions are merged.

## Current work

The owner approved material collection and Royal Nursery construction first,
leaving nurses and queen rearing for the next step. They explicitly selected
woodland resin. Decision D11 records the approved scope.

Implemented:
- Fallen Pine surveys at exploration level 7 reveal Resin Seep after three
  returns; Collect Resin then provides guaranteed material with workers/scouts.
- Resin has 200 storage, resource/cost SVGs, gathered statistics, visible costs
  and queue waits. Its row remains visible after discovery, including at zero.
- Royal Nursery Planning requires level 8, the resin discovery and an ordinary
  Nursery. It unlocks one queued Royal Nursery preparing one royal brood slot.
- Queue guards enforce ownership and the single-chamber limit, repair resin
  costs, and reject duplicate/imported invalid plans. Early chambers keep costs.
- No nurse jobs, queen rearing, founding, prestige or challenges are implemented
  by this foundation.

Validation completed:
- `sbt fastOptJS fullOptJS test`: all 140 engine tests pass, including ten new
  resin/nursery cases; Closure reports no errors or warnings.
- `tests/royal_nursery_smoke.py`: passes at 320, 390 and 1920px for staged
  discovery, collection, costs, queue waits, completion, zero balances and old saves.
- `tests/browser_smoke.py`: passes at 320, 390, 767, 768, 844, 1024 and 1920px.
- `tests/management_smoke.py`: passes at 320, 390 and 1920px.
- Mobile material costs, queue wait and prepared states were visually inspected.
  README documents rewards and costs; local Markdown links and whitespace pass.

## Next work and unknowns

- Implementation and validation are complete.
  [PR #35](https://github.com/RCMartins-apps/AntIdle/pull/35) is open against
  `main` for this branch; merging remains the owner's decision.
- Ordinary prestige can provide starting unlocks/permanent bonuses; challenge
  prestige uses less ideal founding locations and completion rewards.
- Nurse jobs, queen rearing, first Understandings, challenge rules and the reset
  matrix remain future design work. The nursery alone does not settle them.
- Preserve unrelated local `work/` screenshots and `tests/__pycache__/` artifacts.

## Starting a fresh Codex session

Open this repository and use a short prompt such as:

> Read AGENTS.md and its linked context files, check the current Git state, then
> continue with: [the task I want done].

After substantive work, replace this snapshot with verified state, checks,
unfinished items and uncertainties. Keep durable rules in DECISIONS and feature
documentation in README. Avoid duplicating them here.
