# Handoff

## 2026-11-28

Brood removes its outer divider lines and block spacing. A single aging
forecast uses caste icons with accessible names. Positive upcoming losses
use bold red counts. The five-minute window, active-aging gate, home
scope, and Brood options disclosure still apply.

Verified Scala formatting, the development build, and Brood layout at
320px, 390px, and 1280px, including neutral zero counts, highlighted
upcoming losses, and removal of the outer spacing and rules.

See [issue #186](https://github.com/RCMartins-apps/AntIdle/issues/186).

Run live tick accounting on the browser monotonic clock. On reload, clamp backward wall-clock elapsed time to zero and retain the existing cap on forward elapsed time. Preserve partial ticks in the saved wall-time anchor.

Verified the clock model and deterministic balance suites, along with browser coverage for live clock shifts, backward and forward reload, and existing autosave behavior.

See [issue #175](https://github.com/RCMartins-apps/AntIdle/issues/175).

Settings resets saved disclosures, resource expansion and the selected map site in the active browser namespace. It applies defaults immediately and clears only UI preference keys.

Verified the Scala.js builds, mobile navigation at 320px and 390px, and packaged PR preview isolation for colony, backup, playtest and other preference namespaces.

See [issue #172](https://github.com/RCMartins-apps/AntIdle/issues/172).

## 2026-11-28

The installed app can check for a deployed release from the footer. A staged
release can be applied after saving the active colony and closing other game
windows. The game stays open if saving or installing fails.

The production build, packaging tests, and offline browser tests at 390px and
1280px passed before syncing the interface preference reset from main. The
browser tests covered offline installation, save failure, and multiple windows.
