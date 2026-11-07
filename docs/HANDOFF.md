# Handoff

This is a short replaceable snapshot, not a task queue or a permanent change log.
Verify it against Git and PR state at the next session's start.

## Last verified: 2026-11-07

- `origin/main` at `9cabeaa` includes the outpost progression merged in
  [PR #34](https://github.com/RCMartins-apps/AntIdle/pull/34).
- That feature adds outpost levels 2–3, Covered Trails and Seed Processing;
  README documents current costs and mechanics. Its prior validation was 130
  passing engine tests and phone/desktop checks; those were not rerun here.
- Durable context and the automatic branch/push/checkpoint policy are merged.

## Current work

Branch `docs/queen-prestige-paths` records the owner's prestige clarification:
- Upgrade prestige can give the new colony starting unlocks and permanent
  production/capacity boosts. Numeric rewards are explicitly valid.
- Challenge prestige starts a daughter colony in a less ideal environment and
  unlocks something new on challenge completion.
- Raising a daughter queen is endorsed, with opportunities for new chambers
  and jobs. Specific rearing mechanics and content have not been selected.

VISION and decision D2 preserve this distinction. This is documentation-only;
no prestige, challenge, brood-care or queen-rearing gameplay is implemented by
this branch. Validation is local Markdown link checks and `git diff --check`.
The final response reports the actual push result. No PR has been requested.

## Next work and unknowns

- Design the first daughter-queen rearing/founding loop, its costs and the reset
  matrix before implementation. First Understanding effects are not yet chosen.
- Define the first challenge location, goal and reward, completion/abandonment
  behavior, its relationship to Understandings, and whether it can be combined
  with normal prestige benefits. None is settled by the two-form direction.
- New chambers/jobs are supported as a direction; nurse staffing, feeding rules,
  royal chambers and other concrete examples remain suggestions.
- Preserve unrelated local `work/` screenshots and `tests/__pycache__/` artifacts.

## Starting a fresh Codex session

Open this repository and use a short prompt such as:

> Read AGENTS.md and its linked context files, check the current Git state, then
> continue with: [the task I want done].

After substantive work, replace this snapshot with verified state, checks,
unfinished items and uncertainties. Keep durable rules in DECISIONS and feature
documentation in README. Avoid duplicating them here.
