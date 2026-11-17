# Playable pull-request previews

A passing, non-documentation PR from this repository publishes its game at:

`https://rcmartins-apps.github.io/AntIdle/pr-preview/pr-<number>/`

PR handoffs include the GitHub PR link and this preview URL, labeled pending
until deployment is confirmed. Docs-only/fork PRs have no generated preview;
merged/closed previews are removed. Screenshots remain required for visual changes.

Use the PR's deployment/environment link or the **Preview link** in the PR Test
workflow summary. New pushes update that URL after validation; publication checks
the revision marker before reporting success. Closing/merging the PR removes its
server files. Main releases preserve active previews. Fork PRs build/test without
publishing. Docs-only PRs skip builds/previews as before.

## Isolation

Production keeps its existing `save` key. PR 9 uses `save:pr-preview:9`, separate
from production and PR 10. `/` and `index.html` within one preview share its save;
new pushes preserve it. UI preferences are similarly namespaced. Import/export
still works for deliberately testing a copy of a colony. A preview may contain
incompatible prototype changes: export a backup before testing a valued save.

Offline assets/cache names and worker registrations are scoped to each preview.
The production worker now bypasses `pr-preview/` requests. An already-installed
older production worker needs the normal release update (close existing game
windows after the update is ready); a fresh/incognito browser can test immediately.
Removing a hosted preview does not erase browser saves or an installed offline copy.

## Workflow

PR Test keeps read-only access while building, running tests and packaging via
`scripts/package_site.py`. Its separate write-enabled job downloads static assets;
it never checks out or executes PR source. It skips closed/superseded revisions.
The environment and workflow summary supply the preview link without PR comments.

Production builds only on main. Its publishing job downloads the package and
preserves `pr-preview/`. There is no special `debug*` deployment path anymore.
Publishers and cleanup share a non-cancelling concurrency group for gh-pages.
Cleanup uses `pull_request_target` only for closed events and checks out gh-pages,
never PR source. All jobs and steps have explicit runtime limits.

These workflows require GitHub Pages to publish gh-pages, and the Actions token
to have contents write permission in publishing/cleanup jobs. They preserve the
existing required `test` and `changes / classify` checks and docs-only policy.
The cleanup workflow becomes available once merged into main.

## Validation

`actionlint` validates workflow syntax. `sbt fullOptJS 'testOnly *BrowserStorageSuite'`
checks namespaces. `python3 tests/preview_browser_smoke.py` checks real packaged
production plus two preview paths on one origin: root-worker bypass, distinct
saves, canonical index URLs and separate preferences. It runs in PR CI.
Existing packaging and offline browser suites cover coherent release updates.
Live publication/cleanup must also be exercised in GitHub after opening a PR.
