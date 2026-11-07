# Handoff

## Last verified: 2026-11-07

- `feature/dna-prestige` continues from merged main `1e1f982` (royal brood PR #36).
- First prestige is implemented and pushed. No PR requested for this task.
- D13 records approved direction and implemented balance; README covers rules.

## Completed first prestige

- Mature daughters enable a founding preview and confirmation in Nest.
  Colony economy, ants/brood, chambers, research, exploration/discoveries,
  trails, queues and assignments reset. DNA, Understandings, lifetime totals,
  resource visibility, settings and banked time remain. Early unlock
  subscriptions are recreated; the new colony starts with no workers.
- Each founding awards 1 DNA and additive +10% sugar production. Collectors,
  aphids and trail harvesting get the bonus; clicks, expedition rewards,
  consumption, prices and aging keep their rules. Per-collector previews,
  rates and percentage tooltips agree with actual production.
- Shared Paths costs 1 DNA once and is inherited. Nectar Meadow returns reveal
  one finite 1,200-sugar source; marking costs 50 sugar and reserves one scout.
  Up to ten workers harvest at base 0.8 sugar/s each. Storage pauses gathering;
  depletion/recall releases staff to the default pool, preserving age cohorts.
  Guides remain subject to normal upkeep, aging and starvation. Saved excess
  staffing is capped and missing marked-trail task entries are restored.
- Persistence uses `ants.lineage` and `exploration.sugarTrail`; old saves get
  additive defaults. No save version change is required.

Validation:
- `sbt fastOptJS fullOptJS test`: 164 engine tests pass, including twelve
  prestige/trail cases. Final display-only tooltip correction rebuilt in
  development/release mode; Closure reports zero errors/warnings.
- `tests/prestige_smoke.py`: passes at 320, 390 and 1920px. Covers founding
  cancellation/confirmation, DNA, reset totals, early unlocks, trails and saves.
  Desktop rerun additionally checks +10.00% in the resource tooltip.
- `tests/royal_brood_smoke.py`: passes at 320, 390 and 1920px.
- `tests/browser_smoke.py`: passes at 320, 390, 767, 768, 844, 1024 and 1920px.
- Mobile trail card visually inspected; local Markdown links/whitespace pass.

## Future work

- Only the first introductory mechanic is implemented. Repeated founding
  currently gives the same 1 DNA and +10% bonus; additional linear introductory
  Understandings and later short/long-run DNA milestones remain future work.
- Challenge founding, its objectives/rewards and interaction with normal
  Understandings remain unchosen. Do not implement tentative old ideas as rules.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
