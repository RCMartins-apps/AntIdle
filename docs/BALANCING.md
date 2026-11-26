# Changing balance and unlock timing

The game and the balance runner share upgrade and exploration definitions.
Save version 7 uses a handcrafted finite map. Older XP pacing baselines are historical.

## Colony Workshop

From the repository root:

```sh
python3 scripts/upgrade_editor.py
```

Open `http://127.0.0.1:8765`. The editor uses Python 3, Node.js and the
project's existing sbt installation and dependency cache. Run the normal
project setup once to populate that cache. The editor build uses cached
dependencies and the page calls only the local server. Use `--sbt /path/to/sbt`
if needed or `--skip-build` to reopen using the last engine build.
On Windows, the launcher automatically resolves `sbt.bat` or `sbt.cmd`
through `cmd.exe`. If sbt is not on `PATH`, pass its full path, for example
`python scripts/upgrade_editor.py --sbt C:\\tools\\sbt\\bin\\sbt.bat`.

Read the tree top to bottom. Lines link required upgrade purchases. Exploration
research uses Local Exploration as an ordinary prerequisite. The tree and cost
inputs use the game's resource icons.
Select an upgrade to edit its prices, required purchases, discovery and
other reveal gates. Add or remove links in **Prerequisites**. The new links
appear immediately. Use **Rearrange tree** to move cards into dependency order
when ready, or change **Per row** to wrap wide tiers between one and eight cards.
Search by title or stable ID and press Enter to jump to the matching node.
**Title and help** edits player-facing copy. **Bonuses** lets you add, edit,
or remove multiple typed effects. Production percentages target Sugar
Collectors, Scraps Gatherers, Aphid Tenders or Dew Gatherers. Expedition slots add to the concurrent
expedition limit. The game generates effect help from the catalog.

**Save changes** writes the edited fields to
[`src/main/balance/upgrades.json`](../src/main/balance/upgrades.json).
The game and simulator read this file at build time. Rebuild with
`sbt fastOptJS` to play locally, or run the balance lab for a new pacing report.
The editor checks for unknown references and dependency cycles before saving.
**Restore defaults** resets the current upgrade to the catalog snapshot embedded
in the current editor build.
**Revert edits** resets unsaved changes to the last loaded or saved values.

### Canonical upgrade catalog

The JSON file contains every upgrade's price, title, help, prerequisites and
reveal thresholds. The game and simulator load the same catalog by stable
upgrade ID. Saves keep ownership, so rebuilding after an edit applies the
new value to existing colonies. Cost values use the engine's fixed-point units.

The `bonuses` list can be omitted for upgrades with no catalog-driven effects.
Each production entry uses type `productionPercent`, a source
(`sugarCollectors`, `scrapsGatherers`, `aphidTenders`, or `dewGatherers`), and
a whole percentage from 1 to 100.
Production bonuses stack multiplicatively in stable upgrade declaration order,
then in list order within each upgrade, rounding down
to a fixed-point unit after each multiplier. Sugar bonuses apply to collectors
before inherited production and milestones. Protein bonuses apply to scraps
gatherers before storage limits. Aphid bonuses raise sugar output without
raising protein upkeep. Dew bonuses apply to the active region’s water rate. Other kinds of ongoing output and expedition
rewards retain their existing rules. Expedition slot bonuses add together
across owned upgrades. The two shipped expedition researches still add one
slot each. If carrying forward an older custom catalog file, add the new
`slotCapacity` entries to the expedition upgrades to preserve those effects.

When adding an upgrade effect, define its typed payload and gameplay behavior,
then declare its fields, choices, defaults and bounds in `UpgradeTuning`.
The editor and catalog validator read that declaration, so supported
choice and integer fields require no effect-specific browser code.

### Chamber costs and capacities

Open **Chambers** in Colony Workshop to edit the construction catalog
in [chambers.json](../src/main/balance/chambers.json). The editor shows ordinary
game units. The file stores fixed-point amounts like the upgrade catalog.
Each resource's price is its base multiplied by the multiplier raised to
the current level (up to exponent 100), plus the per-level amount times that
level. An optional cap limits the result to its base plus its own per-level
amount. Level zero is the first purchase.

Rebuild the game or simulator to apply edits. The game recalculates prices
for existing queue entries when loading the colony.

Nest tunneling space and Food Storage sugar capacity are editable as typed
`storageCapacity` effects. Both apply per purchased level. The starter Food
Storage level is already included in the colony's base capacity. Previously
saved capacities are migrated using the historical rates, and saves made with
tuned rates store their non-chamber capacity base for later recalculation.
Stored resources above the new cap stay available to spend.

Food Storage protein capacity also uses `storageCapacity`. It applies only
after Protein Storage research and counts every Food Storage level, including
the starter level. The Nursery uses whole-number `slotCapacity` effects for
brood places, Brood Nurse jobs, and carcass slots. These limits are calculated
from the active catalog and chamber level. A lower brood or carcass limit
blocks new commitments until there is room. Other chamber effects retain
their existing rules. The validator supplies the editor with supported bonus
fields and bounds, so the same compact rows work across chambers.

Nest, Queen Chamber and Nursery support editable colony places per purchased
level. Starter housing includes the initial Queen Chamber. Loaded colonies
recalculate the chamber share while preserving outpost housing and existing
population. A lower limit blocks new orders until there is room.

## Where to edit

Paths below start at `src/main/scala/pt/rcmartins/antidle/`.

| Change | Definition |
| --- | --- |
| Map coordinates, reveal order, finite Knowledge/deposits and scout durations | `game/ExplorationMap.scala` |
| Upgrade price, title/help or reveal prerequisites | Offline upgrade editor, with defaults in `game/balance/UpgradeBalance.scala` |
| Stable upgrade/save identity and display order | `model/UpgradesData.scala`; preserve existing `name` values |
| Collection discovery, duration, crew, costs or rewards | `game/ExplorationDestination.scala`, the destination's named arguments |
| Scout studies needed to find a discovery | `game/DiscoveryProgression.scala`, the discovery's `trips` |
| Expedition Supplies tiers/prices/reveal requirements | Offline upgrade editor, `ExpeditionSupplies1`–`3` |
| Natural second expedition slot | `ExplorationProgression.maxParties` (four revealed sites) |
| Linear haul scaling, 5–50-worker bounds and step | `game/ExplorationProgression.scala` |
| Automatic trail efficiency and guide ratio | `game/PheromoneTrails.scala`; see [rate analysis](EXPLORATION.md#shared-paths-collection-automation) |
| Supplies/scout multipliers | `game/ExplorationProgression.scala` |

For example, to reveal Brood Care later, change its
`minimumRevealedSites` in `UpgradeBalance`, retaining `nursery = true` and
the Local Exploration prerequisite. To change Collect Resin's access, edit its
site studies or `requiredDiscovery` in the destination catalog. Its UI and departure validation
share `progressionUnlocked`; no second copy of that discovery check needs editing.
These are examples of tuning points, not recommended balance changes.

## What an unlock means

`UpgradeRequirements` describes when a colony **earns visibility**. Fields combine
with AND. Upgrade prerequisites require purchases, not merely visible research.
A discovery requires its survey count; revealing the hex alone does not find it.

Most visibility is earned history: `status.show` persists even if a prerequisite
later falls below its threshold. For example, losing a small worker doesn't hide
Standard Worker Development again. Purchased research remains visible in state.
Resource visibility is current-colony history: it latches through spending/losses
and resets on founding. Lifetime statistics and milestone bonuses persist.

`earlyResearch` retains the existing Nest/Upgrades-tab/known-Knowledge
alternative checks. Exploration research declares Local Exploration in its
ordinary `upgrades` prerequisites.

`UpgradeResearch.isVisible` owns those history and Knowledge rules.
`canPurchase` additionally checks that the upgrade isn't bought and that its
current price is affordable. `purchase` validates again, spends once and applies
normal reconciliation. UI buttons, actions and the balance runner use it. Keep
these distinctions when adding a requirement: don't accidentally relock earned
research or treat affordability as its reveal condition.

Collection visibility checks revealed/studied sites and discoveries. A visible route still requires available workers,
and scouts when a raid needs a guide,
supplies and a free expedition slot to depart. Sending scouts away doesn't hide
routes. Global access to the Explore tab is checked separately by the launch
engine. Preserve this visibility-versus-current-availability distinction.

## Numbers and effects

- Resources/counts generally use fixed-point `u = 10000`; five sugar is `5L * u`.
- Destination `sugarPerWorker` is **whole sugar per worker**. The worker count is
  already in `u`; do not multiply that field by `u` a second time.
- Durations use ticks, at five ticks/second. Prefer `seconds * TicksPerSecond`.
- Reveal/study counts are ordinary integers, not fixed-point resources. Knowledge is fixed-point.
- Discoveries use completed scout studies, not worker-trip counts.

Descriptions in the upgrade catalog describe effects; the implementation of
those effects stays with the relevant mechanic. Changing help text alone never
changes production. Follow these locations and update/derive any matching help
when changing an effect:

| Effect | Implementation |
| --- | --- |
| Collector base yield, diminishing returns, Foraging Techniques | `game/Constants.scala`, `game/Foraging.scala` |
| Mandibles and Brood Care speed | `game/ColonyDevelopment.scala`, `game/BroodProgression.scala` |
| Aphid output/upkeep and slots | `game/AphidHusbandry.scala` |
| Protein storage and chamber bonuses | `game/ChamberBonuses.scala` |
| Lifespan/Colony Nutrition | `game/PopulationAging.scala` |
| Covered Trails and Seed Processing | `game/OutpostProgression.scala` |
| Default Workers, Queen's Routine, construction queue | `game/DefaultWorkerAssignment.scala`, `game/AutomaticBrood.scala`, `game/Construction.scala` |

Upgrade metadata/prerequisites and map/collection definitions remain shared. Brood, chambers, research effects and outpost mechanics
aren't all moved into one global balance file. Keep shared values with their
mechanic and extract further tuning points when working on that mechanic.

## A safe iteration

1. Save a baseline with `python3 scripts/balance.py --out /tmp/balance-before`.
2. Change one cost, site threshold or prerequisite. Preserve save IDs and hidden advanced
   content. Keep displayed prices/requirements derived from the same definitions.
3. Run `sbt fullOptJS test balance/test` and the relevant browser smoke checks.
4. Run the same policies/seeds with a fresh build and
   `--baseline /tmp/balance-before/report.json`; avoid a stale `--skip-build`.
5. Check milestone deltas, decisions and resource/labor curves. Budget exhaustion
   or a policy wait isn't proof of a softlock. Import a nearby milestone save and
   play at normal speed, including less optimized choices.

See [Balance lab](../tools/balance/README.md) for CLI details. Ask players whether
goals were clear, choices meaningful and waits satisfying. The ≥80% enjoyment
aspiration needs real feedback; faster simulated progress alone isn't success.
