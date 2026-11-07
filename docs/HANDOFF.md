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

Checkpoint implemented:
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

The production build and 140 engine tests pass, including ten new resin/nursery
cases. A final small action-label/locator change still needs rebuilding, and
phone/desktop checks are pending. This checkpoint is not yet declared ready for
merge. Exact costs/rewards and README are being finalized.

## Next work and unknowns

- Finish validation, document the feature and push the final result. No PR is requested.
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
