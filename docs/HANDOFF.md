# Handoff

This is a short replaceable snapshot, not a task queue or a permanent change log.
Verify it against Git and PR state at the next session's start.

## Last verified: 2026-11-05

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

Branch `docs/automatic-branch-push` records new standing authorization: each
distinct task or lasting design decision gets a branch, and changed work is
committed and pushed by default, including documentation-only work without a PR.
Related follow-ups continue that branch. PRs, merges and deployments still need
separate authorization. See AGENTS and decision D9 for the full policy.

This work changes no gameplay. Validation is local Markdown link/file-target
checking and `git diff --check`; game builds/tests are not rerun for it. The
completion message reports the actual push result. Until merged, this policy is
on the named remote branch rather than `main`.

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
