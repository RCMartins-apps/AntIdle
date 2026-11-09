# Handoff

## Last verified: 2026-11-09 — waste design/review documentation

- Branch `docs/game-state-and-waste` now includes main `ba09da5` (PR #50), whose
  shared workflow classifier skips expensive game jobs for docs-only changes.
  Code-change classification and full engine/build CI passed before merge.
- Owner requested recording waste interest, then authorized this documentation
  PR and merge. D31/IDEAS preserve undecided timing and tentative mechanics;
  this is not authorization to implement waste gameplay.
- GAME_REVIEW is a dated source/design assessment against gameplay main `a3b5ac1`:
  strong first-colony foundation; next prestige step is the main content gap.
  Whole-run balance still needs measurement. Recommendations are not approved
  new gameplay tasks. Waste stays tentative until its role/timing is chosen.
- Local Markdown links and git diff --check passed. This branch's diff from main
  must contain docs/root Markdown only. Its PR and resulting main push should
  exercise the new skip behavior; verify game test/deploy jobs are skipped.
- User also asked for safe improvements without further design feedback. A small
  production browser CI gate is being prepared next; draft tests passed locally
  at 390/1280px and cover disclosures, task geometry, navigation, launch and reset.
- Prior merged engine baseline: 242. No game builds/tests rerun for docs alone.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
