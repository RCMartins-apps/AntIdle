# Handoff

## 2026-11-17 — repair PR #90's disclosure-persistence race

- Continue `polish/task-controls`, PR #90, head 6962b89. Required test failed in
  run 43996007646/job 133748214679 during the desktop brood regression, before
  the new task-controls check. Production UI, resources and mobile brood passed.
- The test closed/reopened native details and immediately reloaded a founding
  fixture. The DOM was open but its queued toggle event had not reliably saved
  the reopened preference, so reload restored closed. It passed intermittently
  locally. No task/gameplay failure was reported by CI.
- The brood regression now advances its paused browser clock after each toggle,
  asserts localStorage contains closed/open respectively, and explicitly checks
  open survives reload before inspecting the founding egg action. All original
  geometry, disclosure and visibility assertions remain. Browser selection honors
  CHROMIUM_PATH and uses Playwright-managed Chromium on GitHub Actions.
- Corrected production brood checks and wide-font task-control checks passed
  320/390/1280px. fullOptJS passed with zero Closure warnings/errors; Python
  syntax and git diff --check passed. No gameplay or application code changes.
  Owner requested repair of this existing PR; auto-merge remains authorized with
  required test and changes / classify enforced. Preserve work/ and Python caches.
