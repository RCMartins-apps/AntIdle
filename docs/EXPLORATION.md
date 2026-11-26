# Handcrafted first-ring exploration

Save version 7 replaces the XP expedition prototype. Older saves are deliberately
rejected, as approved for this redesign. The map restarts on founding; inherited
Understandings persist, and current-colony resource visibility resets.

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

Aphid relocation also needs one scout to escort five workers carrying ten aphids.
It is a separate safe housing mission, not a resource haul; see [Aphids](APHIDS.md).

## Fixed landscape and budgets

Axial coordinates `(q,r)` describe six distance-one neighbors of the nest.
No terrain or essential reward is rolled randomly. The reveal order is fixed:

| Site | Knowledge | Opportunity |
| --- | ---: | --- |
| Woodland Edge | 800 | One guaranteed carcass from first study; finite insects; aphid discovery and relocation |
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
See [Shared Paths](#shared-paths-collection-automation) for staffing, costs and balancing.

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

Beyond the Woodland opens three handcrafted distance-two sites after the six
original reveals. They have finite scout Knowledge and unlock colony research;
see [Prestige](PRESTIGE.md). They add no ordinary resource deposits. Additional
outer sites, distance-three range and procedural generation remain future work.

## Map presentation

The map uses inline SVG terrain symbols with short labels and distinct terrain
colors. Unrevealed sites show only fog; selected and exhausted states retain
explicit accessible names and visible indicators. Full site names, finite rewards,
costs and missions remain in the selected-site card. All seven hexagons remain
native keyboard-accessible buttons. This presentation changes no deposits,
Knowledge, unlock requirements, scout roles or save data.

Safe collection cards offer **Fit crew**. It selects the smallest crew capable
of carrying the most available cargo, accounting for in-flight reservations and
remaining storage. Selection never sends a mission.

## Site survey orders

Starting a survey order immediately sends the first study. It can then repeat at
the same site with the chosen scout count until the advertised discovery.
Only one order can be active. Each trip pays its supplies and uses the ordinary
mission slot, age and scout checks. Canceling stops future departures and lets
an active mission return normally. Orders save with the current colony and
clear when founding again.

## Shared Paths collection automation

Each safe studied collection route can have its own trail. Rival Cache
raids remain manual because they involve a deliberate combat-loss decision.

### Staffing and finite resources

- Manual collection crews and trail targets use groups of five, from 5 to 50.
  Each trail reserves one scout per ten target workers, rounded up: 5/10 use one,
  15/20 two, 25/30 three, 35/40 four, 45/50 five. Reservations add across trails.
- Assignment counters show the requirements for the next +5 workers, including
  the whole resulting crew's guide quota. Expansion requires enough scouts after
  other trails' reservations; an existing guide shortage cannot bypass this check.
- Workers remain ordinary home workers with normal upkeep and aging. Desired
  staffing stays saved; Queen's Routine refills aging vacancies as before. Missing
  guides shrink the target to what remaining scouts can guide (10 workers per
  scout), returning excess workers to the default task. With zero guides the
  trail is recalled. New scouts do not automatically restore the old target.
  Competing routes keep their existing destination-ID priority, matching collection.
  Recall releases staff normally.
- Studied-site eligibility is checked directly, so buying Shared Paths later works.
  Site-local controls replace the empty global Pheromone trail panel.
- Actual collected units consume the same deposit used by manual expeditions.
  Manual cargo is reserved on departure; trails cannot collect it or occupy its
  reserved storage room. Full storage and missing sugar pause without cost
  or depletion. Exhaustion releases workers/guides. Trails use no mission slots.
- Old saved nectar reserves are returned to the Meadow deposit once, capped by
  recorded depletion; their old staff is released. No resources are generated by
  loading, and no save-version reset is needed. In-flight manual cargo is retained.

### Rates and tuning

Manual collection now scales linearly. Preserve the previous five-worker reward
(twice the destination's base reward): each worker carries two fifths of that base
reward. Supplies tiers retain their additive haul bonuses. No new reward
multiplier comes from inheritance for either collection method.

`PheromoneTrails.EfficiencyPercent = 60` makes automatic collection slower than
manual collection at the same crew size. Ongoing sugar supplies use the same
sugar-per-unit-haul ratio; workers and guides additionally keep their home upkeep.
Output rounds down and supplies round up in fixed-point units, preventing free
collection through rounding. Final partial harvests pay proportionally. Costs and
preview rates are derived from these engine values, including supplies research.

Base per-worker rates before supplies upgrades (resource/s):

| Route | Manual output | Trail output | Trail sugar supplies |
| --- | ---: | ---: | ---: |
| Woodland insects | 0.08889 protein | 0.05333 protein | 0.26667 |
| Rotten Log | 0.13333 protein | 0.08000 protein | 0.30000 |
| Nectar | 1.33333 sugar | 0.80000 sugar | 0.06667 |
| Seeds | 0.03333 protein | 0.02000 protein | 0.12500 |
| Water | 0.08889 water | 0.05333 water | 0.16667 |
| Resin | 0.01667 resin | 0.01000 resin | 0.17500 |

Values are rounded here; the engine rounds each tick at 1/10,000 resource units.
Upkeep is additional (0.2 sugar/s per normal worker/scout). For example ten Nectar
trail workers and one guide net about 5.13 sugar/s after base supplies/upkeep;
ten manually travelling workers return about 12.22 sugar/s net of supplies over
repeated trips, before guide requirements or additional travel-speed research.
Automation trades efficiency for fewer launches and free mission slots. Renewable
collectors, aphids, scraps and outpost jobs retain value because deposits run out.
These figures explain tuning, not proof that pacing is fun; playtesting remains
necessary. Main tuning locations: ExplorationProgression (crew/linear rewards),
ExplorationDestination (costs/durations/base rewards), PheromoneTrails (efficiency).

The six-policy/seed pacing results are recorded in
[TRAIL_RESULTS.md](../tools/balance/TRAIL_RESULTS.md). All six reached first
founding without starvation; four reached two within four simulated hours. These
are reproducible heuristic checks, not a claim about player enjoyment.

Trail Logistics is outer-map colony research: efficiency rises from 60% to 75%,
with supplies scaled by the same factor (+25%). Guide quotas and normal upkeep
are unchanged. Current previews derive the percentage from research flags.
