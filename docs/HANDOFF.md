# Handoff

## Last verified: 2026-11-14 — manual tasks first

- Branch `ui/manual-tasks-first` is based on main `7b22c11`, merged timeout PR #73.
  Started from 3a51b3c, then fast-forwarded after #73 merged while working. The
  user asked why #73 had not auto-merged: verified it had merged with both required
  checks successful; explained that PR deployment skipping is expected and main
  deployment is separate. Do not equate merging with completed deployment.
- User requested a small Tasks ordering fix as the next deployment smoke change.
  Display sorts manually assignable jobs first, then implicit roles, preserving
  existing uiOrder within each group. Saved task order, assignments, aging and
  resource behavior are unchanged. D45 records the display rule.
- Maintained CI browser fixture now includes implicit roles before manual jobs in
  its input and verifies all rows with assignment buttons precede those without.
- fastOptJS passed, including regeneration after updating to main #73. A local
  development-bundle run of the maintained CI browser checks passed at 390/1280px,
  including ordering, control geometry, brood icons and build version. The helper
  prints production labels, but this local run used the development bundle. No
  full production build, engine tests or pacing rerun for this small UI-only fix.
  git diff --check passed.
- GitHub inspection showed main deploy 43592736309 and Pages run 43592985351
  successful for #69/main, despite some older runs being stuck or cancelled.
  Thus #69–72 were not collectively absent from deployment. #73's subsequent
  deployment was not verified in this task. Next PR/main run can exercise limits.
- Owner authorized PR and auto-merge for this ordering task. Main remains
  7b22c11; active required test and changes / classify checks, strict up-to-date
  policy and no bypass reverified. Squash-only auto-merge is enabled. Check live
  PR/deployment state when resuming; finish after queue confirmation without
  polling CI. Preserve unrelated work/ and Python caches; temporary logs in /tmp.
