# Handoff

## 2026-11-26 — mobile colony spacing

- On phones, the construction queue appears on Chambers. Brood has less
  vertical padding, and the compact resource header and Catch up control
  take less space while keeping the resource timer row stable.
- Mobile success alerts let taps reach the controls beneath them while
  keeping their close button interactive.

## 2026-11-25 — compact Explore layout

- The hex map opens directly above the selected site's actions. Study groups
  the scout picker, trip time, remaining Knowledge, and launch costs.
- Collection shows the expected haul on the launch button, with a storage-full
  label when the destination's resource has no room. Manual and Shared Paths
  crews have Max shortcuts, and the trail's 0 control recalls its crew.
- Explore places resource and scout icons beside the corresponding counts.
  Study and Collect use the same remaining-amount layout.
- Compact phone spacing keeps all four manual crew controls on one row.
- The milestone browser test reads the scout duration from the new crew row.

## 2026-11-25 — offline upgrade balance editor

- `tools/upgrade-balance-editor` includes main through 3018708. Run
  `python3 scripts/upgrade_editor.py` and open the loopback URL it prints.
  The top-down graph links purchased prerequisites.
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
- The balance strategy now selects the first available decision from lazy
  `Option` candidates. Its previous non-local `return` control flow and compiler
  warnings are gone while retaining the existing decision priority. All three
  balance tests and the optimized balance build pass.
- Exploration access is represented only by the normal Local Exploration
  upgrade dependency. The editor no longer exposes separate Explore-tab or
  exploration-available switches, implicit dashed edges, or special cycle rules.
- Important future work is recorded in
  [Changing balance](BALANCING.md#important-todo).
  Replace the temporary Scala-default plus sparse-JSON override layer with one
  complete JSON catalog for all editor-owned upgrade data.
- The production map regression fixture now purchases Local Exploration instead
  of relying on the removed tab-only visibility rule. The complete production
  map, trails, aphid and territory browser suite passes at 320, 390 and 1280 px.
- The second-founding browser fixture also owns Local Exploration before
  checking that outer-ring surveys reveal their research upgrades.
