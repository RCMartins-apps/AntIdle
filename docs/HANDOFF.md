# Handoff

## Last verified: 2026-11-07 — queued scout eggs

- Branch `fix/queued-scout-eggs` starts from merged main `d63c345` (PR #43).
- Fixed scout orders being rejected during the queen's laying cooldown. Scouts
  can also queue while brood places are full, provided Nursery/standard-worker,
  current supply and future housing requirements are met. Ready, unqueued scouts
  still lay immediately. Sugar/protein and egg statistics change only when laid.
- Manual worker/scout orders share FIFO. Each scout stores the number of pending
  worker orders ahead of it; worker batches remain compact counters. Queue state
  survives saves; old worker-only queues remain valid without a version bump.
- Queued scouts reserve future housing for worker/scout/royal actions. Automatic
  workers wait behind manual orders and queued scouts do not satisfy worker
  targets. Missing supplies retain the queue and are shown in brood status.
- Cancel egg orders clears both unlaid types without removing developing brood
  or refunding unpaid costs. Scout details explain queued costs; ordering no
  longer requires a currently free brood place in UI requirements.
- Verified development and production builds, all 233 engine tests (8 new scout
  queue tests), scout_queue_smoke at 320/390/1920px, and existing progression_smoke
  at 320/390/1920px including retrieval, scouts, exploration XP and save/load.
  Browser coverage includes busy-queen acceptance, actual mixed FIFO, no automatic
  overtaking, visible costs, save/reload, cancellation and full beds/housing.
- README and D20 document behavior; corrected stale scout-development text in
  earlier README sections. No changes to maturation costs/duration or aging.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
