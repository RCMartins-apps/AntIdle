# Handoff

## Last verified: 2026-11-07 — emergency colony recovery

- Branch `feature/emergency-colony-recovery` starts from merged main `86d7203`
  (PR #40, founding workers and staged brood).
- Implemented approved D17: after all workers are lost, the queen can rear one
  emergency small worker for 5 sugar without protein, preserving research and
  existing brood/orders. Gather Sugar remains available; the founding queen
  survives starvation. Workers away still prevent emergency eligibility.
- Queen's Routine must be unlocked, enabled and have a positive target for
  unattended recovery. It uses 5 sugar above the reserve, or gathers protected
  provisions over 25 simulated seconds. They fund only this egg, not storage.
  Saved progress resumes; opt-out/returning workers clear unfinished foraging.
- One queen-tended recovery brood bypasses normal brood/housing limits so full,
  hungry larvae cannot block recovery. It receives priority care and counts in
  population; normal egg limits still apply. The hatched small worker collects
  sugar automatically and retains normal small-worker efficiency/upkeep/aging.
- Added a compact red starvation warning with sugar deficit. Recovery controls
  show costs; longer explanations stay under details.
- Verified: development and production builds; 211 engine tests (8 new recovery
  tests); recovery browser checks at 320/390/1920px including manual/automatic,
  paused larvae, full capacity, reserves, opt-out, persistence and layout.
- Existing staged-brood UI regression also passes at 320/390/1920px.
  Balance timings remain adjustable. No new prestige/content direction added.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
