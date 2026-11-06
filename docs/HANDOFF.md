# Handoff

This is a short replaceable snapshot, not a task queue or a permanent change log.
Verify it against Git and PR state at the next session's start.

## Last verified: 2026-11-06

- `origin/main` at `32305bf` includes the Dry Clearing outpost in
  [PR #31](https://github.com/RCMartins-apps/AntIdle/pull/31), durable context
  files, and the automatic branch/push/checkpoint workflow.
- Existing foundations and balance are documented in README. Source and tests
  remain the authority for exact implemented behavior.

## Current work

Branch `fix/compact-action-details` starts from that main commit. The owner asked
for more compact mobile action cards: replace the tall Details disclosure label
with an info icon that expands the existing content.

Checkpoint: shared action cards now use a labeled inline SVG summary beside the
primary button, with a 44px touch target. Native details preserve keyboard and
open/closed behavior; disabled actions still allow viewing details. Closed
panels add no extra row, costs stay visible, and desktop tooltips remain.

Browser checks have been updated to use accessible info labels and check compact
height, independent activation, keyboard toggle and disabled actions. Final
build/engine/browser verification is in progress; this checkpoint is not yet
ready for merge. Relevant README text and decision D4 are updated.

## Next work and unknowns

- Complete UI validation and push the final result. No PR is requested yet.
- No next gameplay task is selected. Further outpost levels and specialization
  remain future design choices.
- Understandings and queen inheritance are agreed prestige foundations, but
  prestige is not implemented; effects, costs and reset rules need design.
- Nurse care, pheromone trails, injuries and waste are tentative possibilities.
- Preserve unrelated local `work/` screenshots and `tests/__pycache__/` artifacts;
  do not commit them as project context.

## Starting a fresh Codex session

Open this repository and use a short prompt such as:

> Read AGENTS.md and its linked context files, check the current Git state, then
> continue with: [the task I want done].

After substantive work, replace this snapshot with verified state, checks,
unfinished items and uncertainties. Keep durable rules in DECISIONS and feature
documentation in README. Avoid duplicating them here.
