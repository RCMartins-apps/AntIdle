# Handoff

## Last verified: 2026-11-07

- PR #36 is merged; latest `origin/main` is `1e1f982`.
- Branch `feature/dna-prestige` starts from that merged main.
- Owner approved working on prestige and named DNA as the currency for buying
  permanent Understandings. D13 records this decision; VISION/D2 agree.
- No prestige gameplay is implemented yet. Existing queen rearing is merged.
- Latest steering: first roughly 3–4 prestiges mostly linear, each introducing
  meaningful mechanics; later short/long runs offer different DNA accumulation.
  This supersedes the proposed immediate three-choice shop/fixed DNA reward.
- Reset rules, introductory mechanics and DNA formula remain undecided. D13
  records the direction and a tentative sequence for discussion; no proposals
  are approved mechanics. Next agree the first generation's reward and reset
  scope, then implement the first playable founding loop.
- This branch currently changes documentation only; whitespace checked.
  Previous feature passed 152 engine tests and mobile/desktop browser checks;
  those checks were not rerun for this documentation checkpoint.
- Next: implement saved lineage/DNA, guarded founding with a mature daughter,
  a reset preview/confirmation, Understanding purchases and actual effects,
  lifetime-stat retention, and engine/save/browser checks once rules are settled.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
