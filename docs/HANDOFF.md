# Handoff

## Last verified: 2026-11-08 — Lineage tab and concise founding UI

- Branch `ui/lineage-and-founding-copy` starts from merged main `76a89f5`
  (PR #48), retaining pending verified outpost fix `eb1590e` via cherry-pick
  `2803c7c`. This branch includes that fix; `fix/outpost-info-stability` is also
  pushed separately, not merged yet.
- Small-worker help uses the owner's exact short text. Removed hypothetical
  Colony Nutrition variants/qualifiers from it and replete help. D28/AGENTS
  prohibit obvious future-upgrade filler while retaining actual upgrade effects.
- Queen's lineage now lives in Lineage, separate from Nest. Reveal with Royal
  Nursery/daughter queen/prior founding; retain across resets. Current readiness
  is visible, core permanent-Understanding context is behind [i], and founding
  benefits/reset warnings remain in review. Royal rearing stays in Nest.
- Raise Small Worker Egg changes to Raise Worker Egg with the standard-worker
  unlock; shared action helper updates button text, help label and title without
  recreating controls. Costs/care/batches and gameplay remain unchanged.
- Verified final development/production builds (Closure clean) and lineage_ui,
  prestige, brood_pipeline, action_details and outpost_info browser checks at
  320/390/1920px. Visually inspected the mobile founding review. Covers short
  copy, tab gates/readiness/help, resets/DNA/trails, caste transition, egg help
  and retention of the outpost fix. Prior engine baseline: 241; engine tests
  were not rerun for this UI-only change.
- Changes committed/pushed on this branch. No PR requested yet.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
