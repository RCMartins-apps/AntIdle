# Handoff

## Last verified: 2026-11-14 — bounded GitHub Actions

- Branch `ci/workflow-timeouts` starts from main `3a51b3c`, merged PR #69. The
  consistency/guide-loss fixes are now on main, along with aphids, brood icons
  and running build identity. Latest main deployment completed successfully.
- Owner requested limits after long-running Actions. Found superseded main
  deployments still running: 43583940403 (stuck at Scala Build project) and
  43582168774. Recent successful PR tests took ~6 minutes and deploys ~3 minutes.
- Runner budgets: PR test 20 min, deployment 15, classifier 5. Explicit step
  limits include builds/tests 10, browser suites 5 each, dependencies 8 and deploy
  5. Browser commands split into three named timed steps; commands unchanged.
- PR and deploy workflows cancel superseded runs per workflow and PR/ref. Required
  test and changes / classify names, docs-only policy and main/debug gates remain.
  D44 and CI-WORKFLOW.md document behavior and non-retroactive limits.
- YAML structure/timeout coverage validated for all three workflows; all six
  ci_changes_test.py tests passed; git diff --check passed. No game build/browser
  rerun needed for workflow-only changes. Live GitHub timeout/cancellation behavior
  will be exercised after the workflow update lands; do not claim live verification.
- Existing runs were not cancelled. The GitHub connector has no cancel action;
  shell gh API access returned Forbidden. Existing executions need cancellation
  separately in Actions; future limits do not apply retroactively.
- No PR requested for this task. Save on this branch under the standing push
  workflow. Preserve unrelated work/ and Python caches.
