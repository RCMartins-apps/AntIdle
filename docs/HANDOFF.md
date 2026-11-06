# Handoff

This is a short replaceable snapshot, not a task queue or a permanent change log.
Verify it against Git and PR state at the next session's start.

## Last verified: 2026-11-06

- `origin/main` at `f431eb8` includes repletes in
  [PR #29](https://github.com/RCMartins-apps/AntIdle/pull/29) and the durable
  context files in [PR #30](https://github.com/RCMartins-apps/AntIdle/pull/30).
- Implemented foundations include exploration destinations/discoveries, SVG
  resources, diminishing sugar collection, default worker allocation, aphid
  chambers, protein storage, aging, queen brood automation, construction
  planning, Dry Clearing/water, statistics and expedition staffing controls.
  README describes behavior; source and tests define exact rules and balance.
- The replete implementation was checked with `fastOptJS fullOptJS test`
  (111 tests passed) and `tests/replete_smoke.py` at 320, 390 and 1920px.
  Those are prior feature checks, not tests rerun for this documentation change.

## Current work

Branch `feature/second-colony-expansion` starts from current `origin/main`
(`f431eb8`). The owner approved implementing the Dry Clearing outpost as an
extension of the current colony, with a water requirement and an ongoing benefit.
This is separate from queen-founded prestige and Understandings.

Checkpoint: the existing construction, worker assignment, aging, resource and
save paths have been reviewed. Implementation will connect an outpost to
water-dependent seed foraging for protein and extra shared colony housing.
Gameplay changes and their tests are not implemented yet at this checkpoint.

The branch carries forward the two automatic-push policy commits: despite the
owner reporting a merge, fetched main does not yet include them. This preserves
the standing authorization and checkpoint instructions without relying on chat.

## Next work and unknowns

- Complete and validate the approved Dry Clearing outpost on this branch.
- Understandings and queen inheritance are agreed prestige foundations, but
  prestige is not implemented. First rewards, costs, reset rules and founding
  conditions remain to be designed with the owner.
- Outpost construction and water-dependent operations are the active task;
  Dry Clearing exploration, water and repletes are already implemented.
- Suggested nurse care, pheromone trails, injuries and waste are possibilities,
  not approved features. See VISION for the distinction.
- Local `work/` screenshots and `tests/__pycache__/` are untracked artifacts;
  preserve unrelated files and do not commit them as project context.

## Starting a fresh Codex session

Open this repository and use a short prompt such as:

> Read AGENTS.md and its linked context files, check the current Git state, then
> continue with: [the task I want done].

After substantive work, replace this snapshot with verified state, checks,
unfinished items and uncertainties. Keep durable rules in DECISIONS and feature
documentation in README. Avoid duplicating them here.
