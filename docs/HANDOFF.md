# Handoff

## Last verified: 2026-11-07

- PR #36 is merged; latest `origin/main` is `1e1f982`.
- Branch `feature/dna-prestige` starts from that merged main.
- Owner approved working on prestige and named DNA as the currency for buying
  permanent Understandings. D13 records this decision; VISION/D2 agree.
- No prestige gameplay is implemented yet. Existing queen rearing is merged.
- Two clarification questions are pending: the reset/persistence matrix, and
  the initial DNA award plus Understanding choices. Proposed values in D13
  are suggestions, not approved rules. Continue when the owner answers.
- This branch currently changes documentation only; whitespace checked.
  Previous feature passed 152 engine tests and mobile/desktop browser checks;
  those checks were not rerun for this documentation checkpoint.
- Next: implement saved lineage/DNA, guarded founding with a mature daughter,
  a reset preview/confirmation, Understanding purchases and actual effects,
  lifetime-stat retention, and engine/save/browser checks once rules are settled.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
