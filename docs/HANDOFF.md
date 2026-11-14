# Handoff

## Last verified: 2026-11-14 — consistency audit and guide-loss follow-up

- Branch `fix/consistency-audit` starts at `99ee3f2`, merged Shared Paths PR #68.
  Verified the merge before starting. Owner requested an inconsistency audit;
  clear fixes implemented, followed by the requested guide-loss behavior. No PR
  requested.
- Trail assignment now checks the whole prospective scout quota after other
  routes' reservations. Losing a guide cannot bypass expansion checks. UI costs
  show next +5 requirements (workers and resulting guides), matching eligibility.
- Follow-up requested by owner: guide losses shrink crews to the remaining
  scouts' capacity and return excess staff to the default task. No guides recalls
  the route; replacement scouts do not restore the old target automatically.
  Existing destination-ID priority across trails is preserved. Forecasts use the
  supported crew too. D42 and COLLECTION-TRAILS document the changed rule.
- Royal-care forecasts now include same-tick Dew Gatherer income, matching actual
  care and resource-rate estimates at an empty water store.
- Audit scope/findings: docs/CONSISTENCY-AUDIT.md. Adjacent construction, resource
  visibility, upgrades and save rules reviewed; no further confirmed player-path
  bug selected. No prices, rates, unlocks or save-format changes.
- Initial audit validation: 262 engine tests and 4 balance tests passed; fastOptJS
  succeeded. Follow-up: 263 engine tests passed and fastOptJS succeeded.
  Maintained map/trail browser checks passed at 320/390/1280px, including red
  1/2 scout requirements for a disabled +5 button. git diff --check passed.
  Guide-loss browser follow-up also passed at 320/390/1280px, including zero-guide
  recall and saved crew removal. Production/offline and long pacing runs were not
  repeated for these narrow changes.
- This is push-only work on the task branch; verify its remote head when resuming.
  No PR created or CI started for this task.
- Preserve unrelated work/ and Python caches. Continue native squash auto-merge
  only when PR/merge is authorized; do not poll after a confirmed auto-merge queue.
