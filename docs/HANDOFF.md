# Handoff

This is a short replaceable snapshot, not a task queue or a permanent change log.
Verify it against Git and PR state at the next session's start.

## Last verified: 2026-11-05

- `origin/main` at `2bd9d10` includes repletes, merged in
  [PR #29](https://github.com/RCMartins-apps/AntIdle/pull/29).
- Implemented foundations include exploration destinations/discoveries, SVG
  resources, diminishing sugar collection, default worker allocation, aphid
  chambers, protein storage, aging, queen brood automation, construction
  planning, Dry Clearing/water, statistics and expedition staffing controls.
  README describes behavior; source and tests define exact rules and balance.
- The replete implementation was checked with `fastOptJS fullOptJS test`
  (111 tests passed) and `tests/replete_smoke.py` at 320, 390 and 1920px.
  Those are prior feature checks, not tests rerun for this documentation change.

## Current work

The owner asked for durable context so Codex restarts do not lose the game's
direction. This documentation branch adds the startup guide, agreed vision,
decision record and this handoff; it changes no gameplay. Documentation checks
cover local links/source paths and `git diff --check`.

## Next work and unknowns

- No next gameplay implementation is authorized by this snapshot.
- Understandings and queen inheritance are agreed prestige foundations, but
  prestige is not implemented. First rewards, costs, reset rules and founding
  conditions remain to be designed with the owner.
- Outpost construction and water-dependent operations are not implemented;
  Dry Clearing exploration, water and repletes are their foundations.
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
