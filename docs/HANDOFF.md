# Handoff

## 2026-11-17 — task controls polish

- `polish/task-controls` starts at main c20a295 and integrates merged #89
  (94fa264), retaining the fixed resource layout. Owner authorized all task
  fixes, a PR and native auto-merge; see D56. Shared docs were combined in order.
- Small workers use a persistent native disclosure headed by their total.
  Help retains 50% mechanics; no duplicate heading. The section disappears fully
  when none remain. Small/standard +/- controls share primary styling and sizing;
  small row controls align right. Existing quantity/multiplier rules are preserved.
- Idle workers appears below the small-worker section and counts actually idle
  standard ants. Default-task ants remain available via the existing engine pool.
  Assignment blockers reserve a compact status line, so their appearance/clearing
  does not move the row or following controls. No gameplay or balance changes.
- Development/production builds passed with zero Closure warnings/errors.
  Targeted production checks passed 320/390/1280px: persistent folding/count,
  batch assignment, styling/alignment, idle vs default pool, disappearance and
  unchanged row geometry with both idle and longer default-pool blockers.
  Existing production UI passed 390/1280px and blocked actions passed
  320/390/1280px. Python syntax and git diff --check passed. Preserve unrelated
  work/ and Python caches. Required checks must stay enforced for auto-merge.
