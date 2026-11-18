# Handoff

## 2026-11-18 — update queue PR #97 from main

- `polish/safer-queue-controls` integrates main 441534f, merged #94 brood UI.
  Documentation conflicts combined; MainForm/CSS merged cleanly. PR #97 stays
  open for manual playtesting with auto-merge off, verified on GitHub.
- Preserves queue red cancel controls, from → to levels, sequential current-rate
  estimates, numeric mobile automation inputs, and main's compact brood controls
  with live caste count badges and centered help buttons.
- Development build and all four construction forecast tests passed. Queue and
  brood browser checks passed at 320/390/1280px, covering transitions, estimates,
  cancellation, numeric controls, live badges and disclosure/layout stability.
  Diff checks passed. No new feature/design or visual changes in this update.
- Preview: https://rcmartins-apps.github.io/AntIdle/pr-preview/pr-97/ — next
  deployment pending push/CI. Preserve unrelated work/ and Python caches.
