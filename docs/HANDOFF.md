# Handoff

## 2026-11-18 — preview link first in PR descriptions

- `docs/preview-first-pr-descriptions` starts at main 441534f. AGENTS.md and
  preview docs now require a Markdown preview link and availability status on
  the first line of every PR description. Use the actual assigned PR number;
  update immediately after creation. Ineligible previews are explicitly labeled.
- Updated existing #97 description accordingly; its body otherwise preserved.
  Preview HTTP verification returned 403 here, so labeled not yet verified.
  #97 stays open without auto-merge for owner playtesting.
- Documentation-only rule change: wording/path and diff checks passed; no game
  build or screenshot needed. Standing authorization covers commit/push, no new
  PR requested for this docs branch. Queue work remains in #97.
- Preserve unrelated work/ and Python caches.
