# Handoff

## Last verified: 2026-11-09 — production browser CI gate

- Branch `ci/production-browser-smoke` from main `23a63b6` (merged PR #51).
  PR #50 first enabled documentation-only test/deploy skips; PR #51 then merged
  the waste proposal/review with game test/deploy jobs skipped as intended.
  Waste timing and mechanics remain proposals (D31); no gameplay implementation.
- User asked for safe improvements without new design feedback. Added the small
  production UI CI gate recommended by GAME_REVIEW: 390/1280px browser checks for
  founding labels, disclosures, task geometry, carousel/desktop navigation,
  expedition launch, founding/reset and persistent resources/Nursery gating.
- ProductionHandler maps development script URLs to antidle-opt output, so CI
  uses fullOptJS directly without building fastOptJS. Python/Playwright dependency
  is pinned; browser failure screenshots upload as artifacts. Docs-only changes
  skip the parent game job and all browser setup. No game/balance/save changes.
- Gate passed locally at both widths against the production bundle. Workflow
  YAML, six classifier policy tests, documentation links and git diff --check pass.
  CI uses the Playwright-installed browser; local runs may use system Chromium.
  PR CI must validate the actual Ubuntu setup before merge.
- Engine baseline from PR #50: 242 passing tests. Broader browser suites stay
  manual; the new gate deliberately targets recurring regressions.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
