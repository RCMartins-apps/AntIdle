# Balance lab

This developer tool runs the **actual Scala.js game engine**, one tick at a time,
without waiting for wall-clock time. No balances or resources are substituted.
It does not ship in the game's browser bundle.

```sh
python3 scripts/balance.py --out /tmp/balance-before
# After changing game values, rebuild and compare:
python3 scripts/balance.py --out /tmp/balance-after --baseline /tmp/balance-before/report.json
# Quick focused experiment:
python3 scripts/balance.py --out /tmp/balance-short --policy balanced --seed 42 --hours 1
```

Requires Java/sbt, Node and Python 3 (standard library only). Use `--sbt PATH`
when sbt isn't on PATH. Output directories must be new. Open `index.html` directly
in a browser: it has no server or external assets. `report.json` contains the
complete machine-readable results, `milestones.csv` supports spreadsheets and
`checkpoints/**/*.save.txt` can be pasted into the game's Load control. Importing
replaces your current game; export your own save first. JSON checkpoints use the
normal game save schema. They contain the actual simulated colony, not a staged
approximation. Saves are stamped at export; importing days later can store offline
time as usual, which you can choose not to spend during a pacing check.

The first measured comparison is recorded in [INITIAL_RESULTS.md](INITIAL_RESULTS.md).

## What is simulated

`BalanceSimulator` calls `TickUpdater.updateAllData(..., None)` at the real five
ticks per simulated second. `None` disables browser local-storage autosaves only.
The browser and runner share pure unlock reconciliation and the same post-action
normalization. Actions use the normal engine's cost/eligibility helpers. The
world seed drives discoveries, aging and other random outcomes. Offline time
multipliers are not spent. Prestige is the normal reset with normal inheritance.

The default configuration has two seeds and three policies. They differ in
population targets, party sizes, extra scouts and how many workers are reserved
for digging, brood care and aphids. They share explicit research priorities.
Change `tools/balance/default.json` or pass another file with `--config` to test
hypotheses. Policy names are labels, not promises of optimal play. Research only
considers the first visible unfinished priority; this is intentionally inspectable.

Each policy makes at most one strategic decision every five simulated seconds.
A decision can batch orders/assignments, so the count is **not literal UI clicks**.
Manual sugar gathering is an explicit opening allowance: 50 one-sugar clicks per
colony, paced at up to two clicks/second (rounded to the next tick). It is
recorded separately. There are no other injected resources. Current policies
pursue safe exploration/discoveries, the outpost and royal brood; they don't
model voluntary raids/territory or every possible upgrade/expansion. Targets
and these rules are policy assumptions, not changes to game rules.

## Reading results

- Milestones report both total time and time since that colony's founding.
  Baseline deltas match policy name, seed, milestone and colony. Check the full
  configuration before treating a delta as a controlled comparison.
- Stocks and workforce samples help connect delays to labor allocation, dwindling
  reserves and population. Samples can miss brief changes. Resource occupancy is
  tick-exact but includes time **before resource unlock**, so zero doesn't always
  mean a shortage. At-capacity ticks mean potential unused production, not an
  automatically bad result.
- Waits are episodes where **this policy chose no action**, with its next goal and
  missing prerequisites. The game might offer other useful actions. They are not
  proof of a forced wait or softlock. A run that misses its goal ends as
  `budget_exhausted`, not "impossible".
- Starvation and aging deaths use the engine's permanent statistics. Event logs
  cap at 20,000 entries; total decision counts still include all actions. Samples
  adapt to roughly 5,000/run. Larger budgets/seeds still take CPU and disk space.
- Provenance includes the commit, source hash, config hash, Node version and execution
  time. Commit custom configurations to make experiments reproducible. Reusing a
  bundle with `--skip-build` is convenient but may be stale after editing code.

## From numbers to fun

Use the tool to find suspicious stretches, then import a nearby checkpoint and
play **at normal speed** for a few minutes. Ask: was the next goal clear; were
there useful choices; did an upgrade feel worth its cost; was waiting rewarding
or merely empty? A fast bot cannot answer those questions. Compare a small,
controlled change against the same policies and seeds, then manually check the
parts whose timings moved. Avoid adjusting every number until one bot wins.

A useful first workflow is: save a baseline, pick one uncomfortable milestone,
look at its preceding stocks/decisions, change one lever, rerun and spot-check.
Early-game, exploration, outpost and queen-rearing checkpoints make this much
less work than replaying the entire game after every update.

## Verification

```sh
sbt test balance/test
python3 -m unittest discover -s tests -p 'balance*_test.py'
```

Tests check determinism, real resource costs, valid save round-trips, no free
resources without gathering, and pure/browser unlock parity. PR CI runs the
Scala suites alongside the existing production browser gate. Long progression
benchmarks are developer experiments, not pass/fail claims about fun.

For a generated report and checkpoint browser check (after `fullOptJS`):

```sh
python3 tests/balance_browser_smoke.py /tmp/balance-before
```
