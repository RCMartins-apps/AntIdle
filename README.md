# Ant Idle

A Scala.js incremental game. Build with Java and the sbt version pinned in
`project/build.properties`:

```sh
sbt fastOptJS
python3 -m http.server 8000
```

Open the served `index.html`. Use `sbt '~fastOptJS'` to rebuild while developing.
Production validation follows CI: `sbt fullOptJS test`.

On phones (up to 767 CSS pixels), panels fit the screen, tabs scroll horizontally,
and actions use full-width touch targets. Expand **Details** to see an action's
explanations and effects. Costs remain visible beside every action. **Action amount** provides the same ×1, ×10, ×25, and
×250 options as desktop Ctrl/Shift shortcuts for laying eggs and assigning workers.
Resource limits and game rules are shared across both layouts. Game saves remain
in the browser's local storage.

The responsive styles live in `index.html` so the existing GitHub Pages deployment
includes them without an additional asset-copy step.

## Browser regression checks

After `sbt fastOptJS`, install Playwright in your Python environment and a browser:

```sh
python3 -m pip install playwright
python3 -m playwright install chromium
python3 tests/browser_smoke.py
```

The script starts its own local static server and checks phone, tablet, and desktop
widths, unlocked tabs, touch targets, action details, save restoration, resizing,
and identical game results for mobile action amounts and desktop modifiers. It
uses isolated browser contexts and simulated save data, without changing your saves.
It uses a system `chromium` if available; `CHROMIUM_PATH` can select another binary.
An inherited HTTPS proxy is supported, with localhost bypassed.

The page loads Bootstrap, jQuery, Font Awesome, and Popper from external CDNs;
network access to those assets is required for the complete interface.

## Chamber bonuses

Define chamber effects in `game/ChamberBonuses.scala` (`forChamber`). Action details,
completed upgrades, and capacity changes use this shared source. Balance values remain in `game/Constants.scala`. Changing an existing
capacity bonus or assigning it to another chamber does not require changes to
`Actions.scala` or `TickUpdater.scala`. Saved capacities stay additive.

## Exploration progression prototype

Expeditions discover dead insects in **Explore**. Select **Retrieve Carcass** to
send idle workers: nearby insects need 5 carriers for 10 seconds; woodland
insects need 10 carriers for 20 seconds. Carriers return automatically and cannot
work other jobs while away. No harvesting job is required.

At base party size and level, each returned carcass grants protein immediately
(5 nearby, 10 woodland), up to
the protein storage limit. Its sugar reserve then feeds the colony before stored
sugar. Nearby insects hold 120 sugar; woodland insects hold 240. Reserves drain
oldest-first, only as needed for upkeep, and their slots free when empty.

The nest starts with one carcass slot. Each Nursery level adds one slot as well
as its shared ant capacity bonus. Returning parties reserve a slot when sent;
new discoveries can wait outside even when storage is full.

The Nursery costs 3 protein plus tunneling space. It unlocks scout eggs costing
50 sugar and 2 protein, maturing in 30 seconds. A scout can guide a 45-second
woodland expedition that discovers a larger carcass. Nearby expeditions take
15 seconds; the first guarantees a carcass, while later ones can find carcasses,
sugar, or nothing. Scouts on expeditions and workers on retrievals remain part
of colony population but do not consume nest sugar while away.

Balance values live in `game/Constants.scala`; retrieval, scout growth, and
expedition rules live in `CarcassProgression`, `ScoutProgression`, and
`ExplorationHelper`.

After `sbt fastOptJS` and browser setup, run:

```sh
python3 tests/browser_smoke.py
python3 tests/progression_smoke.py
```

These cover discovery and retrieval, carrier and slot reservation, immediate
protein, sugar priority and exhaustion, Nursery/scout progression, save reloads,
and phone/desktop layouts.

## Colony capacity

Workers, scouts, and growing brood share one ant capacity, including ants away on
expeditions. Nest, Queen, and Nursery upgrades increase that shared limit. The
Nursery unlocks scout eggs without imposing a separate scout limit. Egg capacity
still limits how many eggs can incubate at once. Save format 6 stores this as `maxAnts`; older save formats are no longer supported.

## Exploration progression

Every completed expedition earns XP, even if it finds nothing. Levels cost
100, 200, 300… XP each, with 19,000 total XP to reach the level-20 cap.
Levels use the gentler curve.
Every level adds 25% to resource rewards. The trip that levels up receives the
new resource bonus. XP progress is visible in Explore; milestone hints and longer explanations stay
inside **XP details**.

| Destination | Duration | Base XP | Base Knowledge | Main reward | Requirements |
| --- | --- | --- | --- | --- | --- |
| Surroundings | 15s | 10 | 2 | First trip guarantees a carcass; then 45% carcass, 44% sugar, 11% no resources | 1 worker |
| Woodland Edge | 45s | 30 | 6 | Guaranteed carcass with 10 protein and 240 sugar reserve | 1 worker, 1 scout |
| Rotten Log | 60s | 60 | 20 | Guaranteed 20 knowledge; no carcass storage needed | Level 3, 5 workers, 1 scout |

Carcasses still need retrieval before their protein and sugar reserve are usable.
Nearby carcasses start at 5 protein and 120 sugar reserve. Forecasts on destination
details include selected workers, supplies, and the level reached on return.
Cards show duration, XP, and Knowledge, with live resource and worker costs beside
the action. Reward odds and capacity requirements are available in mobile
**Details** and desktop tooltips. Locked destinations are
hidden: scout routes appear after Nursery/scout training, and Rotten Log also
requires level 3. Unlocked routes remain visible when scouts are away or resources
are insufficient. Supplies appear at level 2; later ranks appear at their unlock
levels rather than as locked actions.
Sugar costs are 10, 20, and 30 per worker for the three destinations respectively.

Party size improves both XP and all resource rewards, with diminishing returns:
1 worker gives 1×, 5 give 2×, 17 give 3×, and 37 or more give 4×.
Costs continue scaling with workers, so smaller parties are more sugar-efficient.
Larger carcass rewards do not increase carrier requirements or retrieval time.

Milestones unlock expedition supplies at level 2, Rotten Log at level 3, a second
expedition slot and supplies II at level 5, and supplies III at level 8.
Each supplies rank adds 20% XP and rewards (up to +60%), stacking with party and
resource-level bonuses. The three ranks cost 500/20, 1,500/80, and 4,500/200 sugar/Knowledge.
Upgrades affect future departures; trips already away retain their original supplies.
Both workers and scouts are reserved for each expedition, independently of carcass
retrievals and their storage reservations.


Run `sbt test` for the exploration engine tests. After `sbt fastOptJS`, run
`python3 tests/exploration_smoke.py` for destination gating, previews, supplies,
concurrent parties, save reloads, and phone/desktop layouts.

## Default workers

After exploration is unlocked, **Default Workers** becomes available in Upgrades
for 500 sugar and 50 knowledge. It unlocks a **Default task** selector in
Tasks, with **Keep idle** and the colony's unlocked normal jobs (Sugar Collector
and Tunneler). Expeditions and carcass carrying cannot be selected as defaults.

Choosing a default assigns idle workers to it. Hatchlings, returning explorers
and carriers, and workers removed from other jobs automatically join it.
Its workers remain available for actions that previously required idle workers,
including exploration, carcass retrieval, and assigning workers to another job.
Those actions remove workers from the default task while they are occupied;
workers assigned to other jobs are never borrowed implicitly.

Changing the default moves the previous pool to the new task and leaves other
assignments and away parties intact. **Keep idle** releases the pool back to idle.
The default job's +/− buttons are disabled because its allocation is automatic.
Worker costs display the active pool's name and available amount. The default remains idle until the upgrade is bought and a task is selected.

`DefaultWorkersSuite` covers allocation, returns, default changes, maturation,
upkeep, starvation and save compatibility. Run `python3 tests/default_workers_smoke.py`
after `sbt fastOptJS` to check the upgrade, selector, concurrent borrowing,
persistence, shortage indicators, and phone/desktop layouts.

## Upgrade visibility

Every `UpgradeType` defines `shouldShow` alongside its cost and description.
`UpgradeVisibility.reconcile` evaluates all registered upgrades on save load,
after purchases, and during game ticks. Adding an upgrade therefore requires an
explicit visibility rule, and existing saves can reveal it without replaying a
past unlock event. Previously shown or purchased upgrades stay visible; the check
never spends resources, grants purchases, or adds tutorial messages.

## Knowledge economy

Knowledge replaces colony points and has no storage limit. Every expedition earns
it, including trips that find no resources: base rewards are 2 nearby, 6 at the
Woodland Edge, and 20 at the Rotten Log, scaled by party size, exploration level,
and supplies. Chambers and worker population no longer generate it passively.

The first Nest expansion reveals the early upgrades. All prerequisites for
exploration cost only sugar, so a colony can unlock it with zero Knowledge.

| Upgrade | Sugar | Knowledge |
| --- | ---: | ---: |
| Unlock Queen's Chamber | 100 | 0 |
| Foraging Techniques 1 | 150 | 0 |
| Unlock Food Storage Chamber | 400 | 0 |
| Unlock Explorer Task | 750 | 0 |
| Foraging Techniques 2 | 750 | 75 |
| Foraging Techniques 3 | 2,500 | 300 |
| Default Workers | 500 | 50 |
| Expedition Coordination | 1,000 | 100 |
| Expedition supplies I / II / III | 500 / 1,500 / 4,500 | 20 / 80 / 200 |

Tunneling space remains capped. Every Nest level adds 500 capacity alongside its
ant capacity bonus. Nest excavation prices taper before they exceed that capacity,
so expansion remains possible.

Save format 6 deliberately rejects earlier prototype saves. There are no legacy
currency aliases or economy migrations; older saves start a fresh colony.

Run `python3 tests/knowledge_smoke.py` after `sbt fastOptJS` to check the early
upgrade chain, Knowledge income and display, current save restoration and Nest capacity.

Action costs use the shared compact available/required display on every action
card, including supplies, brood, chambers, upgrades, exploration, and retrieval.
Only the available number is green/red; requirements above a storage cap are red.
Scout reservations are visible alongside expedition costs. Free actions say
**Free**. Supplies purchases are visible once unlocked; only their explanations
are collapsed. Costs update with resources, party size, and action amounts. Worker egg batches
show the affordable quantity and actual price; when none can be bought, the
minimum one-egg cost stays visible rather than appearing free.

## Resource icons

`game/ResourceIcons.scala` defines a consistent embedded SVG set for sugar,
protein, Knowledge, tunneling space, eggs, colony population, workers and scouts.
Resource rows retain icon + name. Action costs use icon + available/required,
with a resource tooltip, accessible name and screen-reader text. SVGs are
self-contained and decorative to assistive technology; resource identity never
depends on color alone. Availability colors and full-storage highlights retain
their existing meanings. No external icon files or network requests are needed.

## Foraging pressure

Sugar collection has diminishing marginal returns across the entire colony.
The first collector contributes 0.4 sugar/s, the second 0.395, the third 0.390,
and each additional collector contributes 0.005 less, stopping at zero.
Total output never decreases when assigning another collector. Without upgrades
it plateaus at 16.2 sugar/s: enough for about 81 workers at 0.2 upkeep each, with
very little room for non-foraging work near that population.

Foraging techniques multiply the curve and its maximum output. The three existing
techniques raise the ideal self-supporting population from 81 to roughly 93,
107, and 123 workers. Carcass reserves and expedition rewards are additional
sources, so those figures describe basic collection alone.

`game/Foraging.scala` supplies the production calculation used by ticks and income
estimates. Task details show the current average sugar/s per collector, including
upgrades, and the next collector's contribution. Small rates such as 0.005 stay
visible. Yield depends on current total collectors, not worker identity or time
assigned, so cycling assignments cannot refresh full-rate collection. No positive
minimum yield is added for excess collectors.

**Expedition Coordination** becomes available at exploration level 3 for 1,000
sugar and 100 Knowledge. It permanently adds one expedition slot on top of the
existing level-five slot, allowing up to three expeditions with both unlocks.
Costs, availability, capacity indicators, and the engine use the same slot limit.
The upgrade is saved and checked by the shared visibility rules.

Run `python3 tests/foraging_smoke.py` after `sbt fastOptJS` to check live rates,
actual tick income, upgrade effects, population pressure, slot purchases and
save reloads on phone and desktop. `ForagingSuite` also tests the sustainable
population boundaries, assignment cycling, and engine slot limits.

**Established Trails** unlocks at exploration level 2 for 600 sugar and 60
Knowledge. It reduces the marginal collector penalty by 20% (0.005 to 0.004
sugar/s), expanding productive collectors from 80 to 100. Base output reaches
20.2 sugar/s, enough for 101 workers before Foraging Techniques bonuses. Its
effect stacks with all three techniques and updates live rates and predictions.
Knowledge-priced upgrades stay hidden until exploration is unlocked, including
unpurchased upgrades revealed prematurely in an existing save.

## Colony development

Exploration opens additional colony goals. All new content remains hidden until
its level and colony prerequisites are met:

| Unlock | Requirement | Cost | Effect |
| --- | --- | --- | --- |
| Reinforced Mandibles | Exploration level 3 | 1,200 sugar, 20 protein, 100 Knowledge | Tunnelers produce 50% more space |
| Nectar Meadow | Exploration level 4 and scout training | 40 sugar per worker; at least 5 workers and 1 scout | 90-second expedition with guaranteed sugar and Knowledge |
| Brood Care | Exploration level 4 and a Nursery | 2,000 sugar, 40 protein, 180 Knowledge | Worker and scout eggs mature in 20% less time |

Nectar Meadow trades workers, a scout, sugar supplies and an expedition slot for
a predictable sugar return without carcass storage. Base rewards are 300 sugar,
8 Knowledge and 45 XP; party size, supplies and exploration level scale these
using the existing expedition rules. A five-worker party at level 4 costs 200
sugar and returns 1,050 sugar plus 28 Knowledge before any supplies bonus. The
compact route summary shows the sugar return; costs stay visible on the action.
Rewards obey normal storage caps, and in-flight expeditions survive save reload.

Reinforced Mandibles updates task descriptions, resource income estimates and
actual tick production together. Brood Care affects existing eggs as well as new
ones: workers grow in 8 seconds and scouts in 24 seconds. Egg countdowns use the
same calculation as maturation. Both upgrades are permanent and saved.

Run `sbt fastOptJS fullOptJS test`, then `python3 tests/colony_smoke.py` to verify
hidden content, purchase costs, tunneling production, maturation times, meadow
returns and save persistence on phone and desktop.
