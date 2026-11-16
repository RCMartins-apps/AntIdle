# Handoff

## 2026-11-16 — repair task-ordering PR #74

- PRs #78–82 are confirmed merged; main is bcad158. PR #74 remained open on
  `ui/manual-tasks-first`, with auto-merge enabled but an old failed build and
  a stale branch. Its task-ordering change was absent from main.
- Original CI job 132450871930 failed with Java heap space exhaustion and then
  the ten-minute build timeout. Current main's explicit 3 GB/G1 .jvmopts fix is
  now integrated; preserve all required checks and timeouts.
- This branch merges current main without rewriting history. Game diff is only
  the display sort: manually assignable tasks before implicit roles, with uiOrder
  retained inside each group. The browser fixture deliberately mixes both kinds
  and checks ordering and mobile geometry. Saved task order/gameplay stay intact.
- D45 restored alongside later decisions; latest polish context is preserved.
  Development build and 390/1280 browser checks are being rerun before pushing
  and re-enabling auto-merge on the existing PR, as already authorized.
- Preserve unrelated work/ and Python caches. Check GitHub for live merge state;
  finish after queued native auto-merge with enforced checks, without CI polling.
