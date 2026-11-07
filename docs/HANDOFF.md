# Handoff

## Last verified: 2026-11-07 — compact UI text and explicit [i] help

- Branch `ui/compact-descriptions` starts from merged main `8183e32` (PR #45).
- Owner asked for much less UI text. D22, VISION and AGENTS now require compact
  default screens, removal of repeated explanations, and [i] for useful mechanics.
- Removed repeated nurse/half-efficiency and small-worker tutorials. Task rows
  show counts and compact live rates; default selection no longer repeats its
  label on the job row. Small-worker heading/collector summary are shorter.
- Added native 44px keyboard/touch infoHeading/infoDetails controls for tasks,
  Brood/aging, Queen's Routine, default work, royal care, territory, XP, carcass
  storage and discovery descriptions. Action [i] works on desktop too; action
  and task prose no longer appears automatically on hover. Resource-rate
  breakdown tooltips remain. Closed controls add no explanation paragraphs.
- Material care costs, prices, rates, progress, shortages, imminent aging and
  dangerous-loss disclosures remain visible. Shortage warnings render once.
  Discovery view-model projection is distinct before rendering, so unrelated
  live resource ticks do not close an open discovery explanation.
- Verified `sbt fastOptJS fullOptJS test`: all 238 engine tests; Closure clean.
  New compact_ui_smoke passes 320/390/1920px, including collapsed defaults,
  keyboard/touch, live-update stability, costs/risk and open-panel bounds. The
  phone Tasks screenshots were visually inspected. General browser_smoke passes
  all widths 320–1920px. Foraging (390/1920), management, royal brood, colony,
  protein storage, outpost progression, brood pipeline, prestige and discovery
  smoke checks all pass at their phone/desktop widths (most 320/390/1920).
- Existing tests now open [i] rather than hovering descriptions. Corrected stale
  discovery assertions for the Brood label and 0.80 formatting; made outpost
  clock setup tolerate normal test startup overhead. No game or save changes.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
