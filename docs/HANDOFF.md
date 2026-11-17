# Handoff

## 2026-11-17 — repair PR #89's narrow-font layout failure

- Continue `fix/stable-resource-timing`, PR #89, at 2866d9e. Auto-merge was enabled
  but required test failed in job 133127511588/run 43815802931: on 320px, full-state
  bold 999.99K wrapped, increasing panel height by exactly 21.609375px. This was
  a real CSS issue, not missing auto-merge or a reason to weaken the checks.
- Reproduced the exact CI height sequence locally using DejaVu Sans for compact
  values (the local default font was narrower). Values now use nowrap and a
  bounded responsive font size to fit narrow fixed cells without clipping.
- The resource regression keeps all geometry assertions and adds single-line /
  no-horizontal-overflow checks for values, using the wider font across reloads.
  Its launcher honors CHROMIUM_PATH and uses Playwright-managed Chromium on
  GitHub Actions, matching the production gate's browser selection.
- Pinned Chromium download was denied by the environment's CDN policy; local
  system Chromium plus the wider-font fixture reproduces the CI failure exactly.
  The corrected production resource/catch-up/stability suite passed at
  320/390/1280px, including all unchanged geometry assertions and the new
  non-wrapping/non-overflow checks. Production UI passed 390/1280px. fullOptJS
  passed with zero Closure warnings/errors; four packaging tests and syntax/diff
  checks passed. No engine changes. Push this repair to the same PR branch;
  native squash auto-merge remains authorized with enforced required checks.
- Preserve unrelated work/ and Python caches; keep required test and changes /
  classify enforced. Owner requested fixing the existing PR and retaining auto-merge.
