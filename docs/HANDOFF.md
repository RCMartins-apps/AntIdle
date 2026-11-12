# Handoff

## Last verified: 2026-11-12 — quiet progress and auto-merge setup

- Main baseline 5ee1838 is merged PR #65: standard supply upgrades and collapsed
  purchase history. Final GitHub CI passed, including production/browser/offline.
- docs/quiet-ci-workflow records the owner preference in AGENTS.md: avoid repetitive
  waiting updates; respect higher-level periodic-update instructions. For authorized
  PR/merge requests, prefer native squash auto-merge with enforced required CI and
  finish after a confirmed queue, reporting pending accurately. No background-watch
  promise and no broader authorization to create/merge unrequested PRs.
- Inspected GitHub: auto-merge enabled, squash only; public main branch endpoint
  reports protected=false with check enforcement off; repository rulesets empty.
  Verified actual check names from PR #65: test and changes / classify. Native
  auto-merge tool is available, but branch-protection administration is not exposed;
  its dedicated read endpoint returned integration 403. No repo settings changed.
- docs/CI-WORKFLOW.md gives owner setup: main protection requiring PR + those two
  checks, enforced for merge actor; docs-only test skips remain valid. Until set,
  ordinary checked merging remains necessary for authorized merge requests.
- Docs-only change; relative-link checks and git diff --check suffice. Branch to
  be committed/pushed by default. No PR or settings change requested for this task.
- Preserve unrelated work/ and Python caches. Legacy supplies migration and saves
  remain as implemented in #65; this task changes no gameplay or workflows.
