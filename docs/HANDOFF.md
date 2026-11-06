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

Implemented:
- Compact resource/available-worker totals without invisible suffix padding,
  using tabular digits for alignment; negative resource income is red.
- Direct upgrade-card grid instead of full-width wrappers containing half-width
  cards. Mobile has one column; desktop has two, with controls aligned at the
  top rather than stretched to match neighboring costs. Empty states distinguish
  temporarily unavailable research from completing the catalog, without spoilers.
- Unmet party minimums are visible beside exploration actions. Fleet status sits
  above destinations and highlights full slots, explaining otherwise affordable
  but disabled trips.

Validation:
- `sbt fastOptJS fullOptJS test`: 120 tests passed; Closure reported no errors or warnings.
- `tests/ui_polish_smoke.py`: passed at 320, 390 and 1920px, covering formatting,
  deficits, upgrade layout/empty states/purchases, party minimums, full fleets
  and return recovery; passed again after the final CSS alignment adjustment.
- `tests/browser_smoke.py`: passed at 320, 390, 767, 768, 844, 1024 and 1920px.
- `tests/exploration_smoke.py`: passed at 320, 390 and 1920px.
- Resource and expedition phone layouts and desktop research grid visually
  inspected. Documentation links and `git diff --check` pass. README and D4
  describe the UI conventions. No gameplay balance or save format changed.

## Next work and unknowns

- Implementation and validation are complete.
  [PR #33](https://github.com/RCMartins-apps/AntIdle/pull/33) is open against
  `main` for this branch; merging remains the owner's decision.
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
