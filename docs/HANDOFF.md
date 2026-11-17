# Handoff

## 2026-11-17 — playable isolated PR previews

- `feature/pr-previews` starts at main e391991 (#93). Owner requested an Ant Idle
  equivalent of time-loop PR #9 to test PRs before merge, and explicitly removed
  debug-prefix deployments (D60). Pending `polish/safer-queue-controls` at
  07a323b remains unmerged and remotely saved. Brood PR #94 is separate.
- PR Test keeps required check names/docs-only behavior and adds a packaged
  static artifact after successful validation. A separate write job publishes
  same-repository previews to pr-preview/pr-N, guarded by open/current head.
  Environment/summary link and bounded HTTP revision verification included.
- Main builds/publishes separately, preserves previews and skips superseded code
  (newer docs-only commits do not suppress a valid game release). Closed-event
  cleanup checks out gh-pages only. Writes share non-cancelling concurrency.
- Production save remains save; previews use save:pr-preview:N and isolated UI
  preferences, canonical across index URLs. Production service worker bypasses
  preview paths. Documentation describes existing-worker updates and lifecycle.
- Development/production builds passed, zero Closure warnings/errors. Two Scala
  namespace tests, four packaging tests, six classification tests, actionlint,
  and existing offline suite at 390/1280px passed. Packaged three-site browser
  check passed, including strengthened actual restore through index.html.
  Final actionlint, Python syntax, documentation links and diff checks passed.
- No visual UI change; screenshots not applicable. Live GitHub publish/cleanup
  still requires opening a PR; not claimed tested. No PR requested for this branch.
  Preserve unrelated work/ and Python caches.
