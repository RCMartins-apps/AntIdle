# Handoff

## Last verified: 2026-11-12 — quiet progress and auto-merge setup

- Main baseline 5ee1838 is merged PR #65: standard supply upgrades and collapsed
  purchase history. Final GitHub CI passed, including production/browser/offline.
- docs/quiet-ci-workflow records the owner preference in AGENTS.md: avoid repetitive
  waiting updates; respect higher-level periodic-update instructions. For authorized
  PR/merge requests, prefer native squash auto-merge with enforced required CI and
  finish after a confirmed queue, reporting pending accurately. No background-watch
  promise and no broader authorization to create/merge unrequested PRs.
- Owner configured Main ruleset 27087827; rechecked GitHub on 2026-11-12. Active,
  targets default branch with no exclusions, requires PRs and test plus changes /
  classify from GitHub Actions (15368), strict up-to-date checks, no bypass actors;
  current user cannot bypass. Zero required reviews, linear history, deletion and
  force-push blocks. Auto-merge remains enabled, repository permits squash only.
- Setup is now sufficient for native auto-merge on the next authorized PR. No test
  PR/merge performed in this settings verification. Strict updates may require
  syncing a PR after main changes. Classic-protection metadata does not reflect
  ruleset checks; use ruleset detail, not legacy enforcement=off fields.
- Updated docs/CI-WORKFLOW.md and D41 to replace the obsolete missing-setup state.
- Docs-only change; relative-link checks and git diff --check suffice. Branch to
  be committed/pushed by default. No PR or settings change requested for this task.
- Preserve unrelated work/ and Python caches. Legacy supplies migration and saves
  remain as implemented in #65; this task changes no gameplay or workflows.
