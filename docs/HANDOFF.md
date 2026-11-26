# Handoff

## 2026-11-26

Colony Workshop renders upgrade bonus controls from the game catalog's field
definitions. Production and expedition slot effects can be combined on owned
upgrades. The two existing expedition upgrades still grant one slot each.
The tree wraps at a selectable one to eight cards per row. Dependency edits
update links immediately, with placement refreshed via Rearrange tree.

Chamber prices and selected capacities are already editable. Remaining
catalog candidates include Queen Chamber laying speed, replete and aphid
housing, and population capacity. Population capacity needs care because
the saved total also includes upgrades and outpost effects.

See [Colony Workshop](BALANCING.md#colony-workshop) for the shared catalogs and
how to add another effect.

Verification passed: 347 game tests, three simulator tests, Scalafmt, and the
desktop and mobile Workshop browser smoke covering tree controls and catalog
save/reload.
