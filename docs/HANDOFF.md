# Handoff

## Last verified: 2026-11-07 — mobile expedition picker

- Branch `ui/mobile-expedition-picker` starts from merged main `2e7cc6f`
  (PR #46, compact UI/help). Owner asked to reduce scrolling in Explore.
- Mobile Explore now shows an unlocked-destination select, worker/scout controls
  and only the selected action card. Prices, rewards, shortages and dangerous
  losses stay visible. Destination-specific Min presets preserve explicit party
  choice; stepper and 37-worker cap remain. Desktop retains all unlocked cards.
- Active expeditions, trail, XP and supplies follow launch. D23 and README
  record the layout. No engine, balance, automation or save changes.
- New mobile_explore_smoke passes 320/390/844/1920px: early locked-route hiding,
  picker/card agreement, minimum presets, capped workers, visible costs/risk,
  launches, extra scouts, full slots and missing workforce. Inspected 320px image.
- Development/production builds and all 238 engine tests passed. Exploration
  and dangerous-raid/staffing regressions pass at 320/390/1920px. General browser
  checks pass all seven widths from 320 to 1920px, including resize behavior.
- Existing route helpers now select mobile destinations before interacting;
  desktop helpers keep using the comparison grid. Other helper adaptations were
  syntax checked, not all older suites were rerun.
- Branch committed and pushed by standing authorization. No PR requested yet.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
