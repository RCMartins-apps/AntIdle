# Handoff

## Last verified: 2026-11-07 — work in progress

`origin/main` at `70642c5` includes merged Royal Nursery/resin PR #35.
Current branch: `feature/royal-brood-rearing`. Standing branch/push instructions
remain in AGENTS.md. Decision D12 records the scope of this next prestige step.

Implemented engine and UI draft: capped Royal Nurse task, royal egg/larva/pupa
care, stage-specific feeding, progress/pause/shortage UI, mature daughters,
housing/upkeep/statistics and additive save fields. No founding/reset or
Understanding is granted. First Understanding and reset rules remain undecided.

Development/release builds pass; Closure reports zero errors/warnings and all
152 engine tests pass, including twelve royal brood cases. The new
`tests/royal_brood_smoke.py` passes at 320, 390 and 1920px, with mobile rearing
and ready cards visually inspected. README covers current costs and behavior;
local Markdown links and whitespace pass. Existing resin/nursery and general UI
regressions are still running. Final review remains; this is a checkpoint.
Preserve unrelated untracked `work/` screenshots and `tests/__pycache__/`.
