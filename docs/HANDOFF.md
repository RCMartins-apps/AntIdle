# Handoff

## 2026-11-17 — safer queue and mobile automation controls

- `polish/safer-queue-controls` starts at main e391991 (#93). Separate brood UI
  work is in PR #94 with auto-merge enabled; this branch does not include it.
- Build queue names no longer cancel on tap. Separate red × buttons have
  accessible names and 44px targets. Costs/status remain visible, first-entry
  wait estimate retained, duplicate Queued text removed.
- Queen's Routine numeric fields use numeric keyboards and 44px minimum height;
  the automation checkbox label has a 44px tappable height.
- Development build and focused browser checks at 320/390/1280px verified
  non-destructive name taps, explicit keyboard cancellation, target sizes,
  numeric input modes, label toggling and no horizontal overflow. Mobile queue
  and automation screenshots shown in chat. Final build and all three viewport
  checks passed after removing duplicate queue copy; refreshed queue screenshot
  shown in chat. Diff check passed.
- No gameplay/save changes. Preserve unrelated work/ and Python caches.
  No PR requested for this branch yet; standing authorization covers push.
