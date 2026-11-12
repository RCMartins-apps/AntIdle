# Handoff

## Last verified: 2026-11-12 — standard supply upgrades and purchase history

- Baseline main 5b0f454 includes merged playtest improvements #58–#64. New branch
  feature/standard-supply-upgrades starts from that verified origin/main.
- Expedition Supplies 1/2/3 are ordinary UpgradeType entries, defined/priced in
  UpgradeBalance with sequential prior-tier and 1/3/5 revealed-site prerequisites.
  Sugar/Knowledge prices and additive +20% base haul per tier are unchanged.
- Removed standalone supplies panel, action and purchase/catalog code. Engine haul
  bonuses derive solely from purchased upgrade flags. Old suppliesLevel survives
  only as legacy save input: load converts its clamped rank into purchased flags,
  clears it, preserves resources and is idempotent. Version 7 saves remain valid.
  Party cargo/rank snapshots remain intact; founding resets ordinary upgrades.
- The end of Upgrades has a default-collapsed Purchased upgrades (count) section.
  Readonly current-colony titles have [i] effects. It remains open across ticks and
  new purchases; a fresh load starts collapsed. Understandings remain in Lineage.
- Verified sbt test balance/test fastOptJS: 255 engine + 4 balance tests passed.
  Includes tier gating/prices/repeat rejection, old ranks (including malformed
  bounds), round-trip idempotence, resource preservation, founding reset and haul
  fixture coverage. Safe-ring Knowledge budget counts supplies once as research.
- Maintained CI/map browser functions passed on fresh dev bundle at 390/1280px;
  map script also passed 320/390/1280px with sequential supply purchases, history
  default collapse, live purchase/tick stability and reload persistence. Inspected
  opened-history screenshot at 320px; no overflow. git diff --check passed.
- README, balancing guide, exploration guide, tests README and D40 updated.
  No PR/merge requested for this task; commit/push the branch by default.
  fullOptJS/new GitHub CI not run yet. Logs /tmp/antidle-standard-supplies*.
- Preserve unrelated work/ and Python caches. Temporary screenshots under
  /tmp/antidle-map-browser are not durable user attachments.
- Offline updates apply after all game windows close; no cloud save backup.
