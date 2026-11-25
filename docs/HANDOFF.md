# Handoff

## 2026-11-25 — offline upgrade balance editor

- `tools/upgrade-balance-editor` includes main through 1e0bd37. Run
  `python3 scripts/upgrade_editor.py` and open the loopback URL it prints.
  The top-down graph links purchased prerequisites and Local Exploration gates.
- The editor changes costs, reveal gates, title and help in
  `src/main/balance/upgrades.json`. The engine and simulator embed those
  sparse overrides during compilation. The file ships empty and keeps current
  prices and gates. See [Changing balance](BALANCING.md#offline-upgrade-editor).
- Resource icons come from the game catalog. Chromium checks passed at desktop
  and phone widths, including edits, dependency validation, reload and conflicting
  tabs. An example rebuild charged the edited price and applied the edited reveal
  threshold. The default file was restored and the release build, 340 engine
  tests, three balance tests and a short simulation report all passed.
- Windows resolves sbt batch launchers through `cmd.exe`; missing launchers now
  produce an actionable `PATH` or `--sbt` error. Launcher unit tests and the
  editor browser regression pass after this fix.
