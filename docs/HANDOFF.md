# Handoff

## Last verified: 2026-11-08 — outpost info stability

- Branch `fix/outpost-info-stability` from merged main `76a89f5` (PR #48).
  The previous UI consistency/carousel work is merged; baseline 241 engine tests.
- Reproduced Establish Dry Clearing Outpost help closing during live ticks.
  Its outer nest/exploration map rebuilt the entire section on exploration updates.
- Project only visibility/established/expansion flags, distinct before rendering.
  Levels/capacity now bind inside the stable section; costs, eligibility and
  production remain live. Expansion help also stays open across a level change
  while its action remains available. Actual structural transitions still render.
- AGENTS/D21 record the reactive UI rule. No engine, balance or save changes.
- Verified development/production builds (Closure clean), outpost_info_smoke
  and outpost_smoke at 320/390/1920px. The new check failed on the old code,
  then passed tick stability, live prices, establishment/expansion, level labels
  and manual closing. Existing costs, staffing, production and saves still pass.
  Engine tests were not rerun for this UI-only change.
- Fix committed and pushed on this branch; no PR requested for this fix yet.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
