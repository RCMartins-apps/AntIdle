# Handoff

## Last verified: 2026-11-14 — repairing PR #69 after newer merges

- Branch `fix/consistency-audit` / PR #69 was blocked by merge conflicts, with
  auto-merge still enabled. Merged current main `2b2b022` (build version PR #72),
  including aphid PR #70 and brood icons PR #71. Only HANDOFF conflicted; source
  and browser checks merged cleanly. Keep main's features and the audit fixes.
- PR #69 fixes next +5 worker/scout counters to match eligibility, prevents guide
  shortages bypassing expansion, and includes Dew Gatherer income in royal-care
  forecasts. Guide losses reduce trails to supported crews, returning excess to
  the default task; no guides recalls the route. Replacements do not silently
  restore old targets. See CONSISTENCY-AUDIT.md and COLLECTION-TRAILS.md.
- Existing aphid/save rules and pacing are in APHIDS.md. Running commit identity
  and deferred offline update behavior are in OFFLINE.md; no change to either
  feature is intended by this integration. BuildInfo is generated, not tracked.
- Previous branch validation passed 263 engine tests and map checks before main
  integration. Fresh combined engine/build/browser validation is pending in this
  repair; replace this entry with actual results before the final handoff.
- User authorized fixing and restoring auto-merge for #69. Push the ordinary
  merge/update to its existing branch, preserve required checks and squash auto-
  merge, and finish once GitHub confirms the queue. Do not claim merged/deployed
  from an authorization or queued state. Preserve unrelated work/ and caches.
