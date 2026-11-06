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
- Final outpost production build and all 120 engine tests pass. Browser checks
  in `tests/outpost_smoke.py` and existing `tests/replete_smoke.py` pass at
  320, 390 and 1920px. Local Markdown links and whitespace checks pass.

## Current work

Branch `feature/second-colony-expansion` starts from current `origin/main`
(`f431eb8`). The owner approved implementing the Dry Clearing outpost as an
extension of the current colony, with a water requirement and an ongoing benefit.
This is separate from queen-founded prestige and Understandings.

Implemented a one-time Nest → Outposts establishment action, gated by level 6
and Dew-covered Plants. It costs 3,000 sugar, 40 protein, 300 Knowledge, 750
space and 150 water, adding 50 shared colony capacity and five Seed Forager
slots. Each forager produces 0.04 protein/s for 0.02 water/s plus normal sugar
upkeep. Empty water or full protein pauses production without losing housing.

The engine enforces costs/one-time founding, staffing caps, partial water and
storage, aphid-feeding integration, defaults, save restoration and statistics.
UI costs, rates, shortage states and phone/desktop layouts are verified. README,
VISION and decision D10 record the feature and distinguish it from prestige.

The branch carries forward the two automatic-push policy commits: despite the
owner reporting a merge, fetched main does not yet include them. This preserves
the standing authorization and checkpoint instructions without relying on chat.

## Next work and unknowns

- The approved Dry Clearing outpost is implemented; no next gameplay task is
  chosen. The owner has requested pushing and opening a PR against `main`.
- Understandings and queen inheritance are agreed prestige foundations, but
  prestige is not implemented. First rewards, costs, reset rules and founding
  conditions remain to be designed with the owner.
- Further outpost expansion levels, additional staffing or environmental
  mechanics are future design choices, not approved follow-up work.
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
