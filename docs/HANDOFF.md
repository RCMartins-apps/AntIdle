# Handoff

## 2026-11-24 — Brood Priority

- `feature/brood-priority` starts from main a73e408 (#110). A completed Nursery
  unlocks Balanced, Workers first and Scouts first in the Brood panel.
- Mixed brood shares the existing budget. The chosen caste has twice the
  weight per brood and either caste can use spare care. Ordinary feeding
  forecasts use the same allocation. The queen's laying order, emergency care
  and royal brood remain independent of the setting.
- Priority saves with the colony. Older saves and daughter colonies start
  Balanced. See D70 in [Decisions](DECISIONS.md).
- Passed 327 engine tests and the development build. Browser checks at
  320/390/1280 px verified unlock, live care, save and reload, and layout.
  Captured the control at phone width.
