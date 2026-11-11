# Handoff

## Last verified: 2026-11-11 — playtest follow-up in progress

- Owner authorized implementing docs/PLAYTEST-FOLLOWUP.md in order, with PRs and
  merging after passing checks. Plan is approved, including a separate SVG map pass.
- PR #58 merged as 4cdacd8 with all CI passing. Its balance caller was fixed.
- fix/task-selection-controls follows #58: option replacement explicitly selects
  the actual default task; collection controls use real route unlock predicates.
  Tests cover live Nursery unlock preserving selected/default saved task and a
  studied Fallen Pine without a resin collection unlock. No economy/save changes.
- Verified fastOptJS and maintained CI/map browser functions against that fresh dev
  bundle at 390/1280px. All passed. git diff --check passed. Production CI awaits PR.
- ui/compact-nest: zero-living small-worker resource/task UI hidden; ordinary Brood
  collapses fully and remains stable across ticks. Mobile queue is visible only in
  Nest through CSS on the existing single queue; desktop unchanged. fastOptJS and
  maintained CI/map browser functions passed locally at 390/1280px.
- Remaining sequence: Explore selector/research placement,
  royal brood without nurse prerequisite, SVG map. Keep independent PRs/merges.
- Preserve unrelated work/ and Python caches. Build/browser logs /tmp/antidle-ui2*.
