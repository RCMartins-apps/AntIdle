# Handoff

## Last verified: 2026-11-07 — visible resource capacity timers

- Branch `feature/resource-capacity-timers`, PR #43, now includes merged main
  `602ed3f` (PR #42). Both D18 aging staffing and D19 resource timers are retained.
  Merge conflicts were confined to these context notes; source merged cleanly.
- Added approximate Full in / Empty in labels below resource names. Depletion
  is red; full stores say Full, including at zero net income. Draining full or
  over-capacity stores show time to empty based on the actual stock.
- Sugar, protein, water and tunnelling use the same net rates as the existing
  income display; resin can show Full without inventing continuous income.
  No stationary countdowns or unlimited/population capacity forecasts. Shared
  ResourceTiming helper rounds up ticks and also supplies income tooltips.
- On mobile the label/timer occupies the left column and amount/capacity/rate
  occupy the right, keeping the compact two-line layout. Estimates use simulated
  time at the current net rate, not predicted future actions or expedition hauls.
- Verified `sbt fastOptJS fullOptJS test`: 225 passing engine tests after merging main, including
  four new timer edge-case tests; Closure reports no errors/warnings. New
  resource_timing_smoke passes at 320/390/844/1920px; existing browser_smoke
  passes all widths from 320–1920px. Final font sizing was rechecked across the
  four new-test widths; the 320px screenshot was visually inspected.
- After merging main, both resource timing (320/390/844/1920px) and aging jobs
  (320/390/1920px) browser checks were rerun and pass.
- README and D19 record behavior. No save schema, economy or progression change.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
