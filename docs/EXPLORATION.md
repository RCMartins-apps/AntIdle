# Handcrafted first-ring exploration

Save version 7 replaces the XP expedition prototype. Older saves are deliberately
rejected, as approved for this redesign. The map restarts on founding; inherited
Understandings and known resource visibility retain their existing behavior.

## What scouts do

- **Explore Surroundings:** reveal one of six neighboring hexagons, in fixed order.
  One scout takes 60 simulated seconds; two/three take 30/20. No workers required.
- **Study Site:** learn about a revealed site. One scout takes 180 seconds;
  two/three take 90/60. Three studies claim 50%, 30%, then 20% of its Knowledge.
- **Guide:** Shared Paths reserves one scout per ten trail workers, rounded up,
  separately for each safe collection route. Rival Cache raids also need a guide.
  Busy guides cannot join studies or another trail.

Scout missions cost ten sugar per participating scout. Local reveal/study crews
still consume their usual adult sugar upkeep. Scouts need housing and feeding,
provide no ordinary task output, and keep their original age expiry while away;
o aging reset or repeated-mission immortality. Larger crews cost more and finish
sooner, but do not multiply Knowledge. Normal worker hauls need no scout except
the dangerous Rival Cache; workers cannot reveal or study a site themselves.

## Fixed landscape and budgets

Axial coordinates `(q,r)` describe six distance-one neighbors of the nest.
No terrain or essential reward is rolled randomly. The reveal order is fixed:

| Site | Knowledge | Opportunity |
| --- | ---: | --- |
| Woodland Edge | 800 | One guaranteed carcass from first study; finite insects; aphid discovery |
| Nectar Meadow | 1,000 | Finite nectar and seeds; automated trails |
| Rotten Log | 1,100 | Finite insects; Dry Trail discovery |
| Dry Clearing | 1,200 | Water deposit; dew collection, repletes and outpost progression |
| Fallen Pine | 1,400 | Finite resin; royal-nursery progression |
| Rival Cache | 1,500 | Safe scouting from a distance; optional dangerous raids and territory |

All 7,000 Knowledge is available through safe scout studies. It exceeds **all**
current upgrade, supplies, establishment and both outpost-extension Knowledge
costs combined. A test calculates that budget from actual prices. Spending on
optional research cannot consume the last Knowledge needed for founding.

Site Knowledge is finite, not a reward on worker trips. Once a site is studied,
worker collection draws from its separate finite deposit. Party size and supplies
raise what can be carried per trip; they never increase the deposit or study yield.
Nectar supplies cost ten sugar per worker so every useful 5–50-worker crew
has a positive full-load sugar return without relying on XP multipliers.
Optional raid casualties/rewards remain random and disclosed, but no required
resource or discovery depends on combat or a lucky result.

The first study opens basic collection. Fixed subsequent study thresholds find
aphids/seeds/dew/resin and unlock their associated mechanics. Advanced actions
remain hidden until their discoveries; merely revealing a hex doesn't grant them.
There are no XP levels or level-based resource multipliers.

## Depletion without destroying progression

Cargo is reserved at departure, against both the source and free destination
capacity, including other in-flight cargo. If storage fills during travel,
unretained cargo stays at the source. Identical simultaneous parties each return
once; no worker is stranded and no cargo can be replayed for another reward.

A depleted hex remains recognizable and selectable, with no remaining mission.
Its discoveries, purchased research and jobs remain learned. Scouts studying sites
and workers collecting them share expedition slots. The fourth reveal opens a
second slot; Expedition Coordination adds a third when purchased.

Sugar collectors, scraps gatherers, aphids and outpost workers remain renewable
colony jobs. Three Dry Clearing studies open **Dew Gatherers**: up to five standard
workers gathering 0.15 water/s each. Their normal sugar upkeep and opportunity cost
remain. This prevents exhausted water deposits from permanently blocking ongoing
outpost/royal maintenance. Shared Paths automates gathering from those same finite
safe deposits; it does not reserve an extra nectar sub-source or regenerate finds.
See [Collection trails](COLLECTION-TRAILS.md) for staffing, costs and balancing.

This is protection against resource/content softlocks, not immunity to poor
staffing or starvation. Existing queen recovery remains available after collapse.
All current required progression can be reached without a raid or random find.

## Editing and verification

- `game/ExplorationMap.scala`: ring coordinates/order, Knowledge, deposits,
  reveal/study duration, scout costs and reservation guards.
- `game/ExplorationDestination.scala`: collection crews, costs and cargo capacity.
- `game/balance/UpgradeBalance.scala`: `minimumRevealedSites` and discovery gates.
- `game/DiscoveryProgression.scala`: study thresholds and unlocked jobs.
- `game/ExplorationHelper.scala`: finite hauling, raid outcomes, overflow/returns.
- `game/ExplorationProgression.scala`: mission slots and haul multipliers; supply
  bonuses derive from standard purchased upgrades. Their tier requirements/prices
  live in `game/balance/UpgradeBalance.scala`. Old supply ranks migrate on load.

Paths start at `src/main/scala/pt/rcmartins/antidle/`. Run engine/balance tests,
production CI smoke and `tests/map_browser_smoke.py` at phone/desktop sizes.
The balance lab now uses scout studies and resource collection; pre-map XP timing
baselines are historical. See [BALANCING.md](BALANCING.md) and the lab's README.

Distance-two/three rings and their prestige/range unlocks remain future work.
No procedural generation, new Understanding or extra ring is included here.

## Map presentation

The map uses inline SVG terrain symbols with short labels and distinct terrain
colors. Unrevealed sites show only fog; selected and exhausted states retain
explicit accessible names and visible indicators. Full site names, finite rewards,
costs and missions remain in the selected-site card. All seven hexagons remain
native keyboard-accessible buttons. This presentation changes no deposits,
Knowledge, unlock requirements, scout roles or save data.
