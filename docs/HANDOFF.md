# Handoff

## 2026-11-15 — polish pass 5: visual consistency

Owner authorized all five passes, each in a separate PR with native auto-merge.
PRs #78/#79/#80/#81 cover statuses, map feedback, UI memory and event log. Check
live state before integrating; all originally start at main 0405cf7 (#77).
Current branch `polish/visual-consistency` standardizes controls, numeric alignment,
focus, progress tracks and empty queue text. One shared number formatter handles
K/M/B/T suffixes, tiny values and negative signs without Long.MinValue overflow.
Exact action requirements remain in titles/accessibility labels. Royal progress
has accessible clamped values. Validation is being completed before PR creation.
CI linking on #78 exhausted the default ~0.9 GB heap. Shared .jvmopts now allows
3 GB with G1; fix propagated to all branches without relaxing timeouts/checks.
Event-log branch passed 291 engine tests, production build and 390/1280 browsers;
other passes have scoped tests/browser checks recorded in their PRs. Integrate
pending branches against each new main to avoid strict up-to-date merge blockers.
Preserve unrelated work/ and Python caches; no branch deletion or force pushes.
