# Handoff

This is a short replaceable snapshot, not a task queue or a permanent change log.
Verify it against Git and PR state at the next session's start.

## Last verified: 2026-11-06

- `origin/main` at `179d6c1` includes the Dry Clearing outpost, compact mobile
  action details and the redundant unlock-level cleanup through PR #32.
- Existing mechanics and balance are documented in README; source and tests
  define exact implemented behavior. The automatic branch/push policy is merged.

## Current work

Branch `fix/mobile-ui-polish` starts from that main commit. The owner requested
finding and improving other UI issues. Phone screenshots of all six tabs were
reviewed before choosing the changes.

Checkpoint implemented:
- Compact resource/available-worker totals without invisible suffix padding,
  using tabular digits for alignment; negative resource income is red.
- Direct upgrade-card grid instead of full-width wrappers containing half-width
  cards. Mobile has one column; desktop has two. Empty states distinguish
  temporarily unavailable research from completing the catalog, without spoilers.
- Unmet party minimums are visible beside exploration actions. Fleet status sits
  above destinations and highlights full slots, explaining otherwise affordable
  but disabled trips.

A targeted browser script covers formatting, resource deficits, upgrade layout,
empty states and purchases, party minimums, full fleets and return recovery.
Final builds, engine tests and phone/desktop checks are in progress. This
checkpoint is not yet declared ready for merge. README and decision D4 are updated.

## Next work and unknowns

- Complete validation and push the finished result. No PR is requested yet.
- No next gameplay task is selected. Further outpost levels and specialization
  remain future design choices.
- Understandings and queen inheritance are agreed prestige foundations, but
  prestige is not implemented; effects, costs and reset rules need design.
- Nurse care, pheromone trails, injuries and waste remain tentative possibilities.
- Preserve unrelated `work/` screenshots and `tests/__pycache__/` artifacts.

## Starting a fresh Codex session

Open this repository and use a short prompt such as:

> Read AGENTS.md and its linked context files, check the current Git state, then
> continue with: [the task I want done].

After substantive work, replace this snapshot with verified state, checks,
unfinished items and uncertainties. Keep durable rules in DECISIONS and feature
documentation in README. Avoid duplicating them here.
