# Handoff

## Last verified: 2026-11-09 — documentation-only workflow skips

- Branch `ci/docs-only-skips` from main `a3b5ac1` (merged PR #49).
- User asked to skip game tests/deploy for docs-only changes before merging the
  waste idea/review documents, then work on improvements needing no new design.
- Shared reusable game-changes workflow and scripts/ci_changes.py classify
  `docs/**` and root Markdown as docs. PR merge-base comparison; whole push range;
  no rename collapsing; unknown/empty comparisons run validation. Manual test
  dispatch always runs. Job-level skips preserve completed required statuses.
- Main and same-repository debug* deployment rules remain; normal non-debug PRs
  skip deployment classification too. Policy unit checks run only for non-doc work.
- Verified six policy tests, real temporary Git PR/push/rename cases, output and
  unknown-input fallback, YAML parsing and git diff --check. Workflow/code change
  must pass real CI before merge. No game rebuild needed locally.
- Pending docs branch `docs/game-state-and-waste` (7dd3792) records the waste idea
  and source review. Rebase/merge current main before its docs-only PR so it uses
  the updated workflows; reconcile D30 numbering/HANDOFF. User authorizes its PR
  and merge. Waste introduction and mechanics remain undecided, not an instruction
  to implement gameplay. Consider a small browser CI gate as the next safe step.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
