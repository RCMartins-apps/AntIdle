# Handoff

## 2026-11-17 — safer queue and mobile automation controls

- `polish/safer-queue-controls` starts at main e391991 (#93). Separate brood UI
  work is in PR #94 with auto-merge enabled; this branch does not include it.
- Build queue names no longer cancel on tap. Separate red × buttons have
  accessible names and 44px targets. Costs remain visible. Follow-up removes Ready/Queued and replaces the inline
  first-entry timer with sequential Resources in ~time estimates for all entries.
  ConstructionWait forecasts prior spending, capacity and net rates; red Needs
  resources means no feasible current-rate estimate. No future bonuses assumed.
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

- Sequential ETA follow-up verified: development build and four dedicated tests
  passed (prior costs/storage, ready/unreachable, water/resin, consumption).
  Updated browser checks passed 320/390/1280px with both queue estimates visible;
  mobile screenshot shown in chat. Diff check passed.

- Queued levels now show from → to, accounting for earlier queued upgrades to
  the same chamber. Development build and 320/390/1280px browser checks passed,
  including repeated-chamber transitions. Updated mobile screenshot shown in
  chat; diff check passed.
