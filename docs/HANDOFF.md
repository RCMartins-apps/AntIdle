# Handoff

## Work in progress: 2026-11-09 — balance lab

- Branch `tools/balance-simulator` starts from merged PR #53 (`f550d9c`).
- User approved a balance tool: real-engine seeded policies, milestone/wait/stock
  reports, baseline comparisons and playable checkpoints. No balance changes.
- First implementation under `tools/balance`, Python entry `scripts/balance.py`.
  Three configurable population/exploration policies; standalone interactive HTML,
  JSON/CSV and normal import saves. See tools/balance/README.md and D33.
- Unlock rules moved from browser subscriptions to pure reconciliation shared
  with actions and ticks; browser regression gate remains essential.
- First build passed. Trial balanced/42 reached first founding at 7830 simulated
  seconds. Its second run exposed an overly expensive idle-worker reservation
  policy; strategy revised. These are preliminary policy results, not balance
  conclusions. Full test/build/browser verification and longer runs in progress.
- PR CI now includes balance/test and Python report packaging; not yet verified
  on GitHub. This checkpoint is not ready to merge.
- Waste remains recorded only; no new Understanding or gameplay balance approved.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
