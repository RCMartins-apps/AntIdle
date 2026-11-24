# Handoff

## 2026-11-24 — playable nest refuse prototype

- `design/nest-refuse-pressure` makes larval feeding fill a refuse buffer after
  Nursery level 2. Clean brood places become a limit on new eggs. Carriers clear
  backlog and a Sealed Refuse Chamber adds 150 capacity. Existing midden
  purchases keep their backlog and staffing. See [Nest refuse](MIDDEN.md).
- Verified 332 engine tests and the Brood browser checks at 320, 390 and 1280 px.
  Browser coverage includes live capacity, carrier availability before purchase,
  and queued eggs resuming after the chamber opens a place.
- Open tuning work is a longer playtest of ordinary and heavily staffed
  nurseries. The branch integrates merged Brood Priority from PR #111.
