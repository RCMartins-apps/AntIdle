# Handoff

## 2026-11-26

Colony Workshop edits all chamber costs, Nest tunneling space, Food Storage
sugar and researched protein capacity, and Nursery brood, nurse and carcass
slots. The [shared catalog](BALANCING.md#chamber-costs-and-capacities) controls
rules in both the game and simulator. The shipped defaults and save format are
unchanged. Reduced storage caps preserve existing stock.

Remaining catalog candidates include Queen Chamber laying speed, replete and
aphid housing, and population capacity. Population capacity needs care because
the saved total also includes upgrades and outpost effects.

Verification passed: 346 game tests, three simulator tests, Scalafmt, and
a 390px Chromium save/reload of edited Food Storage and Nursery capacities
using a temporary catalog.
