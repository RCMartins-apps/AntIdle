# Handoff

## Last verified: 2026-11-08 — consistent action details

- Branch `ui/consistent-action-details` starts from merged main `82ff73b`
  (PR #47, mobile expedition picker). User requested logically consistent action
  descriptions after comparing worker/scout eggs.
- Worker, scout and emergency egg help now share Queue / Development / Larval
  care fields via eggDescription. Base duration derives from Developing.totalCare;
  worker caste changes update it. Clarifies laying costs versus later larval care.
- Common action panels order explanation, Requires, then Result, omitting empty
  sections. Left aligned help/results, consistent egg singular/plural; scout
  result now shows +1 egg as worker does. Help stays behind [i], costs stay visible.
- D24 and AGENTS record shared-family formatting as a lasting rule. README
  documents behavior. No engine, balance or save changes.
- Verified development and production builds (Closure clean), all 238 engine
  tests and compact_ui_smoke at 320/390/1920px. action_details_smoke checks
  shared field order, times, care and results for worker/scout/small/emergency
  eggs at the same widths. Browser assertions use the new shared queue wording.
- Branch committed and pushed by standing authorization; no PR requested yet.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
