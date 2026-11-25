# Handoff

## 2026-11-25 — offline upgrade balance editor

- `tools/upgrade-balance-editor` starts from main 4dc346a. Run
  `python3 scripts/upgrade_editor.py` and open the loopback URL it prints.
  The top-down graph links purchased prerequisites and Local Exploration gates.
- The editor changes costs, reveal gates, title and help in
  `src/main/balance/upgrades.json`. The engine and simulator embed those
  sparse overrides during compilation. The file ships empty and keeps current
  prices and gates. See [Changing balance](BALANCING.md#offline-upgrade-editor).
- The editor uses the game's resource icons. Engine checks passed before the
  icon pass. The final icon, editing and real-build write-through checks are
  in progress.
