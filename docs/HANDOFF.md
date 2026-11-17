# Handoff

## 2026-11-17 — queue preview for manual review

- `polish/safer-queue-controls` integrates main 9b0f3e5, including merged #95/#96
  PR previews and publishing fix. Documentation conflicts combined; code merged
  cleanly. Owner authorized PR creation to test the preview, explicitly no merge
  or auto-merge until further approval.
- Dedicated red 44px cancel buttons replace cancelling by tapping queued names.
  Entries show current → next chamber level, including earlier same-chamber
  queued upgrades. Sequential current-net-production estimates include earlier
  spending, capacity and consumption. No Ready/Queued labels. Missing feasible
  income shows Needs resources; future chamber bonuses are not forecast.
- Queen's Routine has numeric keyboards, 44px input heights and tappable label.
- Prior development builds, four forecast tests and 320/390/1280px queue checks
  passed; screenshots shown. Updated development build, all four forecast
  tests and queue/automation browser checks at 320/390/1280px passed after
  integration, including repeated levels and non-destructive name taps. Updated
  mobile screenshot shown. Actionlint and diff checks passed.
- Preview saves/preferences are isolated from production; PR handoff must include
  preview URL with pending/live status. No PR code executes in publishing jobs.
- Preserve unrelated work/ and Python caches. No gameplay/save-format changes.
