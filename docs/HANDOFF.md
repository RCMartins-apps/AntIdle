# Handoff

## Last verified: 2026-11-08 — completed Lineage and concise UI pass

- Branch `ui/lineage-and-founding-copy` starts from main `76a89f5` (PR #48).
  Includes pending outpost info fix `eb1590e` via `2803c7c`; its original branch
  remains pushed separately. This branch is the complete next review.
- Includes short founding copy, caste-aware Raise Small Worker Egg naming and
  dedicated Lineage tab (royal nursery/daughter/prior-founding gate, persistent
  after reset), plus the outpost disclosure fix. Earlier builds and lineage,
  prestige, brood, egg-detail and outpost checks passed at 320/390/1920px.
- Further pass: effect-first upgrade descriptions, numeric outpost bonuses with
  missing +5 Seed Forager slots restored in the preview, compact replete facts,
  and XP help without redundant next-level forecasts. Essential risks, protein
  care, future-only aging and retroactive storage behavior remain explained.
- Royal brood projects only structural visibility/built state; unrelated chamber
  and research changes no longer recreate its help/controls. D28 records the pass.
- Verified development/production builds (Closure clean), all 241 engine tests,
  concise_ui_smoke and outpost_progression_smoke at 320/390/1920px. Source affects
  UI/copy only; no balance, costs, unlock rules or save-format changes.
- Owner requested PR creation and merge after checks. Check GitHub for final PR
  state; CI must pass before merging. Branch changes are committed/pushed.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
