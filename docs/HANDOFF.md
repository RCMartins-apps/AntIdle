# Handoff

## Last verified: 2026-11-09 — real-engine balance lab

- Branch `tools/balance-simulator` starts from merged PR #53 (`f550d9c`); intermediate
  checkpoint `8c2b2cf` was pushed. User requested implementation, not a PR/merge.
- Implemented headless Scala.js `balance` subproject reusing actual game ticks,
  shared pure unlock/post-action rules, seeded configurable labor/research/party
  policies, standalone interactive HTML, JSON/CSV, baseline deltas and real import
  saves at eight milestone families. Entry: `python3 scripts/balance.py --out NEW`.
  See tools/balance/README.md, INITIAL_RESULTS.md and D33.
- No balance values/game content/save schema changed. Browser subscription unlocks
  now reconcile in actions/ticks, so headless and player progression use the same
  rules. Existing UI disclosure behavior remains covered by production tests.
- Verified 245 engine + 4 balance tests, production fullOptJS, Python packaging
  test, production browser gate at 390/1280px, report controls/layout and loading
  all eight checkpoint families into production at both widths. Baseline report
  browser check covers comparison rendering; repeated short CLI runs matched
  exactly. CLI build/run tested with and without --skip-build.
- Default six-run benchmark (three policies, two seeds, four-hour/run budget)
  took ~84s excluding build: five runs reached two foundings; exploration-first/42
  reached one and was still progressing. Zero starvation deaths. Pacing is policy
  evidence, not optimality/fun or a proposed balance target. Baselines are
  informational; human normal-speed checks remain needed. Initial measured
  results persist in tools/balance/INITIAL_RESULTS.md; generated reports stay /tmp.
- PR workflow now runs balance/test and Python report packaging alongside normal
  validation. Not yet verified by a new GitHub PR; docs-only skip policy unchanged.
- Follow-up D34 records the owner's ≥80% enjoyment aspiration and priority of
  easily editable balance/unlock definitions. VISION and AGENTS reflect it;
  no satisfaction percentage measured and no wider configuration refactor done.
  This follow-up is documentation-only: relative paths and diff whitespace checked.
- No waste mechanic/new Understanding authorized by this work; those remain
  separate design tasks. Main's earlier CI/docs work is merged.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`; scripts/__pycache__
  is a generated local Python cache, not source. No generated builds/reports staged.
