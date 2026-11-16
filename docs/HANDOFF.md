# Handoff

## 2026-11-16 — reset visibility on founding

- Branch `fix/prestige-visibility-reset` starts at main bf8bf05, merged #74.
  All five polish PRs #78–82 and the repaired #74 are now on main. Owner requested
  resource/population rows reset like a fresh game each prestige; see D50.
- Prestige.found stops copying resource unlock flags and protein discovery.
  Rows unlock through current-colony actions and still latch through spending
  or losses. Standard Worker Development still introduces protein and Nursery
  normally. Lifetime statistics/peaks, milestone bonuses, DNA and Understandings
  are preserved. Founding preview/help now describes fresh visibility correctly.
- On load, old pristine daughter-colony saves clear inherited visibility only if
  all current-colony state/statistics match the initial state. Active histories
  are preserved; zero stock alone is never used to erase an unlock. No schema
  bump or save wipe. ResourceVisibility.resetUnstartedFounding owns this check.
- Validation: 299 engine tests + 4 balance tests passed; fastOptJS/fullOptJS passed
  with zero Closure errors/warnings. Production CI browser gate passed 390/1280,
  comparing all rows to fresh start after founding/reload and repairing an old
  untouched save while retaining lifetime totals. Production prestige gate passed
  320/390/1280 with fresh resource rows at both introductory foundings. Python
  syntax and git diff --check passed. Final formatting/docs only changed afterward.
- Owner authorized PR creation and native auto-merge. Main ruleset enforces
  test and changes / classify with strict freshness and no bypass; queue after
  this documentation update is pushed. Preserve unrelated work/ and Python
  caches. No changes to ongoing active saves' flags.
