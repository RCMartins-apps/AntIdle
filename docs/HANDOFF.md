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
- Verified development/production builds (Closure clean), all 242 engine tests,
  concise_ui_smoke and outpost_progression_smoke at 320/390/1920px. Source affects
  UI/copy plus corrected Nursery availability; no balance, costs or save-format changes.
- Owner requested PR creation and merge after checks. Check GitHub for final PR
  state; CI must pass before merging. Branch changes are committed/pushed.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.

- Added the reported post-prestige Nursery fix to PR #49: nurseryAvailable
  separates current standard-worker progression from inherited protein history;
  Protein resource visibility has its own signal. Existing chambers remain.
  D29/AGENTS record the distinction. Verified final fastOptJS/fullOptJS (Closure
  clean), all 242 engine tests, and actual prestige/reload plus standard-worker
  research browser checks at 320/390/1920px. Updated head must pass CI before merge.
