# Handoff

## 2026-11-27

The simulation tick no longer schedules or writes browser saves. The browser
controller saves the first snapshot at the deadline after completing the
batch, with one write attempt per batch and retry at the next interval.

Verified formatting, game and balance builds, and 40 focused Scala tests.
A 30-minute balanced run at seed 42 produced identical run data before and
after. Browser coverage verified normal autosave, a write failure and retry,
and the saved snapshot at the first tick of a two-tick catch-up batch.

See [issue #161](https://github.com/RCMartins-apps/AntIdle/issues/161).
