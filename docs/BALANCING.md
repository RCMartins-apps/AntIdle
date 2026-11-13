# Changing balance and unlock timing

This guide covers the current upgrade and exploration definitions. They are
Scala values checked at build time; there is no separate configuration service.
Save version 7 uses a handcrafted finite map; older XP pacing baselines are historical.

## Where to edit

Paths below start at `src/main/scala/pt/rcmartins/antidle/`.

| Change | Definition |
| --- | --- |
| Map coordinates, reveal order, finite Knowledge/deposits and scout durations | `game/ExplorationMap.scala` |
| Upgrade price, title/help or reveal prerequisites | `game/balance/UpgradeBalance.scala`, the named upgrade's row |
| Stable upgrade/save identity and display order | `model/UpgradesData.scala`; preserve existing `name` values |
| Collection discovery, duration, crew, costs or rewards | `game/ExplorationDestination.scala`, the destination's named arguments |
| Scout studies needed to find a discovery | `game/DiscoveryProgression.scala`, the discovery's `trips` |
| Expedition Supplies tiers/prices/reveal requirements | `game/balance/UpgradeBalance.scala`, `ExpeditionSupplies1`–`3` |
| Natural second expedition slot | `ExplorationProgression.maxParties` (four revealed sites) |
| Linear haul scaling, 5–50-worker bounds and step | `game/ExplorationProgression.scala` |
| Automatic trail efficiency and guide ratio | `game/PheromoneTrails.scala`; see [rate analysis](COLLECTION-TRAILS.md) |
| Supplies/scout multipliers | `game/ExplorationProgression.scala` |

For example, to reveal Brood Care later, change its
`minimumRevealedSites` in `UpgradeBalance`, retaining `nursery = true` and
`explorationTab = true`. To change Collect Resin's access, edit its site studies or `requiredDiscovery` in the destination catalog. Its UI and departure validation
share `progressionUnlocked`; no second copy of that discovery check needs editing.
These are examples of tuning points, not recommended balance changes.

## What an unlock means

`UpgradeRequirements` describes when a colony **earns visibility**. Fields combine
with AND. Upgrade prerequisites require purchases, not merely visible research.
A discovery requires its survey count; revealing the hex alone does not find it.

Most visibility is earned history: `status.show` persists even if a prerequisite
later falls below its threshold. For example, losing a small worker doesn't hide
Standard Worker Development again. Purchased research remains visible in state.
One existing exception is retained: unpurchased Knowledge-priced upgrades are
hidden until exploration is available, including stale saved `show` flags.
Known resource visibility is separate and survives founding as before.

There are two exploration flags in the upgrade requirements, retained for save and
transition compatibility: `explorationTab` requires the unlocked tab flag;
`explorationAvailable` also accepts the purchased Unlock Explorer Task research
before the next reconciliation updates that flag. `earlyResearch` retains the
existing Nest/Upgrades-tab/known-Knowledge alternative checks. Most future
exploration research can follow the `explorationTab` pattern.

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
