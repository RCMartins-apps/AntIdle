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
  is not yet verified. Owner authorized PR and auto-merge. Main rules verified
  active with required test and changes / classify, strict up-to-date, no bypass.
  New standing rule: include preview links in PR handoffs, with honest pending/
  live/ineligible/removed status. No extra CI polling required.
  Preserve unrelated work/ and Python caches.

- PR #95 failed its first engine test run: eager SaveLoad initialization accessed
  window in Node during pure save serialization (KnowledgeEconomySuite and
  ExplorationSuite). SaveGameName is now lazy so only browser storage operations
  resolve the pathname. Auto-merge remains enabled. Development/production builds
  passed (zero Closure warnings/errors), all 308 engine + 4 balance tests passed,
  and the packaged preview isolation/restore browser check passed. Diff check
  passed. No UI changes/screenshots needed; push retriggers CI.
