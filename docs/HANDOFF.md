# Handoff

## Last verified: 2026-11-08 — chamber/exploration consistency pass

- Ongoing branch `ui/consistent-action-details` from merged main `82ff73b`
  (PR #47). Contains shared egg fields, no redundant egg result rows, independent
  mobile Nest/Tasks amounts, non-overlapping task help, and scout batch ordering.
  Prior changes passed builds/239 engine tests and relevant mobile/desktop checks.
- Current pass: chamber actions have numeric bonus lists, no prose descriptions.
  Labels consistently use resource capacity, colony slots, brood places and
  named slots with singular/plural agreement. Nursery adds its previously prose-
  only +3 brood/+5 nurse places; Queen shows the next laying-interval reduction.
- ChamberBonuses.forDisplay includes derived effects without changing forChamber
  or completed-upgrade behavior. New ActionBonus fields are preview-only.
- Exploration [i] now shows Finds/outcome rules rather than repeating its visible
  duration/rewards. Removed generic no-carcass-storage filler and shortened raid
  explanation; chance distributions and combat modifiers stay in help, visible
  danger/costs/rewards stay beside launch. Compatibility preview includes summary.
- D25, AGENTS and README record the rule. No balance or save format changes.
- Verified development/production builds (Closure clean), all 240 engine tests,
  chamber_details_smoke for all seven chambers/all ten destinations, and
  protein_storage_smoke at 320/390/1920px. Final wording builds and all-route
  browser checks pass; visually inspected the 390px Nursery bonus panel.
- Changes committed/pushed on the ongoing branch. No new PR requested.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
