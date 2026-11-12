# Handoff

## Last verified: 2026-11-12 — artwork provenance and branch cleanup

- Main baseline 1271cea is merged PR #66: quieter updates and native auto-merge
  workflow. The first auto-merge succeeded. Active Main ruleset requires PRs,
  test + changes / classify from GitHub Actions, up-to-date branches and no bypasses.
  For authorized PR/merge requests, queue squash auto-merge and report pending
  without polling; do not claim a merge before confirmation.
- At owner request, experiments were preserved as remote annotated archive tags:
  archive/early-game (b7709fa) and archive/shoelace-lib-experiments (a64a6d9).
  Their branches and four redundant docs/outpost-fix branches were deleted after
  verification. Remaining remote branches at cleanup: main, gh-pages, some-assets.
- Owner clarified that some-assets is their own Paint.NET pixel art for an earlier
  top-down 2D ant-game prototype using a graphics library, also working on Android.
  docs/ASSETS.md records provenance and the retained branch/files. No graphics
  library identified and no asset reuse, UI redesign or engine change authorized.
- docs/asset-provenance records this information only; no artwork was copied into
  main and no gameplay/package change made. Documentation checks only; commit and
  push by default. No PR/merge requested in this provenance discussion.
- Preserve unrelated work/ and Python caches. Supply upgrade/history feature #65
  remains merged, with 255 game + 4 balance tests and final CI passing.
