# Handoff

## 2026-11-28

Run live tick accounting on the browser monotonic clock. On reload, clamp backward wall-clock elapsed time to zero and retain the existing cap on forward elapsed time. Preserve partial ticks in the saved wall-time anchor.

Verified the clock model and deterministic balance suites, along with browser coverage for live clock shifts, backward and forward reload, and existing autosave behavior.

See [issue #175](https://github.com/RCMartins-apps/AntIdle/issues/175).
