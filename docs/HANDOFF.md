# Handoff

This is a short replaceable snapshot, not a task queue or a permanent change log.
Verify it against Git and PR state at the next session's start.

## Last verified: 2026-11-06

- `origin/main` at `963c21b` includes the UI polish merged in PR #33, plus the
  Dry Clearing outpost and earlier colony systems. Current mechanics are in README.
- The automatic branch/push/checkpoint workflow and durable context are merged.

## Current work

Branch `feature/outpost-progression` starts from that main commit. The owner
requested additional content and updates, delegating feature selection.

Checkpoint implemented:
- Dry Clearing Outpost extensions at exploration levels 7 and 8. Each adds 25
  shared colony housing and five Seed Forager slots, for level 3 totals of 100
  housing and 15 slots. Each expansion has visible, escalating multi-resource costs.
- Covered Trails research at level 7 reduces forager water use by 25%.
- Seed Processing at level 8 raises forager protein yield by 50%.
- Shared harvest calculation handles partial water/storage at the new ratios;
  task details, resource estimates and outpost summaries use the upgraded values.
  Small resource rates now retain useful fractions and tooltip signs.
- Existing saves default to an unexpanded level-1 outpost. Imported extension
  counts are bounded and loading does not grant housing again.

The first development build and existing 120 tests pass. Ten new engine tests
have been added but not yet run. Final production build and phone/desktop
checks are pending; this is a remotely preserved checkpoint, not a merge-ready
validation claim. Exact costs and ongoing documentation are being finalized.

## Next work and unknowns

- Finish validating the new content, document it and push the final result.
  No PR has been requested for this branch.
- Understandings and queen inheritance remain agreed future prestige foundations;
  effects, costs and reset rules still need design. These are ordinary colony
  expansions and research, not inherited Understandings.
- Additional locations, nurse care, pheromone trails, injuries and waste remain
  future possibilities rather than an approved implementation queue.
- Preserve unrelated local `work/` screenshots and `tests/__pycache__/` artifacts.

## Starting a fresh Codex session

Open this repository and use a short prompt such as:

> Read AGENTS.md and its linked context files, check the current Git state, then
> continue with: [the task I want done].

After substantive work, replace this snapshot with verified state, checks,
unfinished items and uncertainties. Keep durable rules in DECISIONS and feature
documentation in README. Avoid duplicating them here.
