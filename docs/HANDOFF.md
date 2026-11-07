# Handoff

## Last verified: 2026-11-07

- `origin/main` at `d86ccfc` contains merged raid PR #38.
- Branch `feature/contested-territory` implements the owner-approved next step;
  pushed, with no PR requested. D15 and README document rules and balance.
- Temporary vulnerable food sources and soldier specialization were liked but
  explicitly deferred. They are saved in VISION/D15, not implemented here.

## Completed territorial contests

- First Rival Food Cache return discovers Contested Foraging Ground, revealing
  a dedicated Territory tab. Older current-run raid casualties recover that
  earned discovery on load. Prestige resets it with ordinary discoveries.
- Claim a fixed target of 10–37 workers for 20 sugar each; no scout or expedition
  slot. Oldest available home cohorts are reserved at the site. Each holder
  contributes 0.3 protein/s toward an unfinished haul, delivered after each
  60-second contest if holders survive combat and aging, limited by storage.
- Visible recurring casualties use raid-size ranges: 2–4 of ten or 4–10 of 37;
  smaller understaffed crews continue fighting and can be wiped out. Saved
  seed/round/timer prevents reload rerolls. Full stores never stop casualties.
- Safe withdrawal restores survivor cohorts/default assignments but abandons
  the unfinished haul. Expired holders die at contest/withdrawal, preserving
  fixed lifespans. Defeat loses ground/haul without automatic reclaim.
- Manual reinforcement and opt-in automatic staffing borrow only idle/default
  workers. Queen's Routine supplies ordinary replacements. Holders pay normal
  upkeep and can starve. `agesAtHome` separates cohort location from upkeep,
  preventing home aging from removing the wrong holders or granting immunity.
- Costs, risks, countdown, haul/rate, storage/aging warnings and staffing
  availability are visible. Contest/death/claim statistics have run/lifetime
  persistence. Old saves default to no hold; no save version bump required.

Validation:
- Development/release builds and all 188 engine tests pass, including thirteen
  territory cases. Closure reports zero errors/warnings.
- `tests/territory_smoke.py`: passes at 320, 390 and 1920px for discovery, costs,
  risk, deferred haul, contest/save timing, replacement/refill, withdrawal,
  storage, aging and layout. Extra 390px rerun covers old-raid discovery recovery.
- `tests/raid_smoke.py`: passes at 320, 390 and 1920px.
- `tests/browser_smoke.py`: passes at 320, 390, 767, 768, 844, 1024 and 1920px.
- Mobile territory card visually inspected; local Markdown links/whitespace pass.

## Next work

- Tune population/reward pressure through play feedback before adding further
  brood-care progression. Additional sites, later introductory Understandings,
  scalable DNA milestones and challenge founding remain future work.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
