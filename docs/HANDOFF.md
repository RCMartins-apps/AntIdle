# Handoff

## 2026-11-28

Run live tick accounting on the browser monotonic clock. On reload, clamp backward wall-clock elapsed time to zero and retain the existing cap on forward elapsed time. Preserve partial ticks in the saved wall-time anchor.

Verified the clock model and deterministic balance suites, along with browser coverage for live clock shifts, backward and forward reload, and existing autosave behavior.

See [issue #175](https://github.com/RCMartins-apps/AntIdle/issues/175).

Settings resets saved disclosures, resource expansion and the selected map site in the active browser namespace. It applies defaults immediately and clears only UI preference keys.

Verified the Scala.js builds, mobile navigation at 320px and 390px, and packaged PR preview isolation for colony, backup, playtest and other preference namespaces.

See [issue #172](https://github.com/RCMartins-apps/AntIdle/issues/172).
