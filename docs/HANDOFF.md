# Handoff

## 2026-11-24 — territory guard rotations

- `feature/territory-guards` starts from main 4dc346a. One completed contest
  reveals Territory Guards research. Guards come from the holding crew, lose
  their share of the haul and reduce the combat loss range. While holding,
  changes start at the next round. See D73 and
  [Optional territorial contests](../README.md#optional-territorial-contests).
- Both guard assignments are saved. Older territories load with zero guards.
  The production build, 335 game tests and three balance tests passed. The
  map browser suite and focused production guard checks passed at 320, 390
  and 1280 px. Mobile screenshots cover the claim and next-round controls.
- Initial costs and guard strength are playtest values. Ten holders with two
  guards gather 144 protein per round before storage caps and risk 1–2 combat
  losses. Later economy pacing depends on crew size and replacement capacity.
