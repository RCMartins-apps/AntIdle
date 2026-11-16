# Handoff

## 2026-11-16 — remove the calendar header

- `polish/remove-calendar-header` starts at main e67bfd8 (merged #85) and now
  includes merged #86 (80525f0). Code merged cleanly; documentation conflicts
  resolved retaining both decisions/test instructions and this current handoff.
  Owner authorized the header PR and native auto-merge. Required main checks
  test and changes / classify are active, strict, with no bypass.
- Removed MainForm's title/calendar top bar and its obsolete responsive styles.
  Catch-up is a sibling of the resource header/body, positioned beside the heading
  on phone/desktop, so collapsing resource details never hides or toggles it.
  It retains its stored-time duration and On/Off control, adds an accessible
  pressed state, and vanishes when no stored time remains. No simulation changes.
- D53 records the calendar decision. Mobile-resource browser coverage checks
  narrow-screen geometry, independent resource/catch-up toggles, actual accelerated
  ticks and the empty stored-time state. Production resource/catch-up checks passed
  320/390/1280px; navigation passed 320/390px with desktop resize; production UI
  passed 390/1280px. Development/production builds passed with zero Closure
  errors/warnings, four packaging tests and syntax/diff checks passed. Geometry
  checks now measure row and icon in one frame to tolerate restored scroll.
  Final edits were documentation/comments and formatting only.
- Preserve unrelated work/ and Python caches. No gameplay changes in this pass.
  The main integration changes only previously validated code from #86; the PR
  retains all required CI checks.
