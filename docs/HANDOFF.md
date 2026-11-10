# Handoff

## Last verified: 2026-11-10 — egg orders need housing, not upfront resources

- Main baseline: offline/install PR #57 merged as 8a56b54, all GitHub CI passed.
- Branch fix/egg-order-capacity starts there. Worker/small-worker and scout orders
  now accept batches limited only by future housing; scout unlock prerequisites
  remain. Zero sugar/protein no longer disables ordering or reduces the batch.
  Actual laying still checks full costs, brood space and the queen's cooldown.
- Shared FIFO, queued housing reservations, cancellation and save format unchanged.
  Displayed costs remain visible and reflect housing-limited batches. D38 and the
  README record this distinction between ordering and laying.
- Verified sbt test fastOptJS: all 253 engine tests passed, including unfunded
  orders, mixed housing reservations, save persistence and later exact charging.
  Updated existing scout/feedback tests to match the new rule.
- Maintained ci_browser_smoke.check_game passed at 390/1280px against the fresh
  development bundle, including zero-resource buttons and housing-full disabling.
  The committed CI entry point still checks production builds. fullOptJS and new
  GitHub CI were not run for this branch. git diff --check passed.
- No PR or merge requested for this task; branch is committed/pushed by default.
  Preserve unrelated work/ and Python caches. Logs are under /tmp/antidle-egg-orders*.
- Offline feature remains: first full online cache required; installation optional,
  updates wait until all game windows close, and local saves are not cloud backups.
