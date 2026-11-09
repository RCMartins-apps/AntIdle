# Handoff

## Last verified: 2026-11-09 — CI improvements and waste record merged

- Gameplay baseline includes merged PR #49 (Lineage/copy and Nursery reset fix).
- PR #50 (`ba09da5`) merged shared documentation-only detection: `docs/**` and
  root Markdown skip game tests/build/deploy; mixed/code/config/test changes run.
  Job-level skips preserve completed statuses; unknown comparisons validate;
  manual test dispatch validates. Main/debug deployment restrictions remain.
- PR #51 (`23a63b6`) merged IDEAS/GAME_REVIEW and D31. Waste interest is recorded;
  timing and exact mechanics remain undecided, with no waste gameplay authorized.
  Real docs-only PR game tests and its main-push deployment both skipped, verified.
- PR #52 (`d1554b9`) merged the production browser CI gate at 390/1280px. It checks
  founding labels/help, outpost disclosure persistence, task geometry, navigation,
  expedition launch, prestige/reset, Nursery gating and persistent protein.
  Uses fullOptJS output, pinned Playwright and its installed browser on GitHub;
  uploads screenshots on failure. Docs-only jobs skip browser setup too.
- Verified local production browser gate at both widths, six classifier tests,
  real Git range/rename cases, YAML/links/diff checks, and GitHub CI: production
  build, all 242 engine tests and both browser viewports pass. No game/balance/save
  changes were introduced by the CI work. Broader feature smoke suites stay manual.
- GAME_REVIEW's main remaining recommendations are measured first/second-run
  pacing, the next meaningful prestige goal and incremental UI extraction. They
  do not authorize implementing a new Understanding or waste mechanic.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
