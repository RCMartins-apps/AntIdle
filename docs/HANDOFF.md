# Handoff

## Last verified: 2026-11-07 — compact UI text (checkpoint)

- Branch `ui/compact-descriptions` starts from merged main `8183e32` (PR #45).
- Owner requested much less UI prose, removing redundant text and putting useful
  explanations behind [i]. D22 and AGENTS record this as a lasting UI rule.
- Implemented reusable keyboard/touch [i] controls for tasks/sections and explicit
  action details on desktop too. Removed duplicated nurse/worker explanations;
  moved brood/aging, automation, default work, royal care, territory, XP, carcass
  storage and discovery prose into collapsed help. Numeric rates/costs and live
  shortages/danger remain visible. Inactive/zero aging summaries stay inside help.
- Updated existing browser checks to open info controls instead of hovering prose;
  added compact_ui_smoke covering collapsed text, live data, keyboard and layout.
- Development and production builds pass; all 238 engine tests pass. New compact
  browser test has passed at 320px; other widths, general responsive and existing
  gameplay smoke checks are still running/not yet completed. This is a backup
  checkpoint, not a final verification claim. The phone Tasks screenshot was
  visually inspected after fixing the shared icon and shortening the small header.
- No mechanics or save changes. Finish browser regressions and update this note
  before final push/handoff. Preserve untracked `work/` and `tests/__pycache__/`.
