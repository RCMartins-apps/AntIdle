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
existing costs and effects. **Action amount** provides the same ×1, ×10, ×25, and
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
completed upgrades, colony-point income, and income estimates use this shared
source. Balance values remain in `game/Constants.scala`. Changing an existing
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
still limits how many eggs can incubate at once. Save format 5 stores this as `maxAnts`; older save formats are no longer supported.

## Exploration progression

Every completed expedition earns XP, even if it finds nothing. Levels cost
100, 200, 300… XP each, with 19,000 total XP to reach the level-20 cap.
Existing XP is preserved; levels are recalculated using the gentler curve.
Every level adds 25% to resource rewards. The trip that levels up receives the
new resource bonus. XP progress and the next milestone are visible in Explore;
longer explanations stay inside **XP details**.

| Destination | Duration | Base XP | Main reward | Requirements |
| --- | --- | --- | --- | --- |
| Surroundings | 15s | 10 | First trip guarantees a carcass; then 45% carcass, 44% sugar, 11% no resources | 1 worker |
| Woodland Edge | 45s | 30 | Guaranteed carcass with 10 protein and 240 sugar reserve | 1 worker, 1 scout |
| Rotten Log | 60s | 60 | Guaranteed 20 colony points; no carcass storage needed | Level 3, 5 workers, 1 scout |

Carcasses still need retrieval before their protein and sugar reserve are usable.
Nearby carcasses start at 5 protein and 120 sugar reserve. Forecasts on destination
cards include selected workers, supplies, and the level reached on return.
Sugar costs are 10, 20, and 30 per worker for the three destinations respectively.

Party size improves both XP and all resource rewards, with diminishing returns:
1 worker gives 1×, 5 give 2×, 17 give 3×, and 37 or more give 4×.
Costs continue scaling with workers, so smaller parties are more sugar-efficient.
Larger carcass rewards do not increase carrier requirements or retrieval time.

Milestones unlock expedition supplies at level 2, Rotten Log at level 3, a second
expedition slot and supplies II at level 5, and supplies III at level 8.
Each supplies rank adds 20% XP and rewards (up to +60%), stacking with party and
resource-level bonuses. The three ranks cost 500, 1,500, and 4,500 sugar.
Upgrades affect future departures; trips already away retain their original supplies.
Both workers and scouts are reserved for each expedition, independently of carcass
retrievals and their storage reservations.

Old saves and in-flight nearby/woodland trips remain compatible. New destination
and supplies fields have defaults; neither XP nor progress is reset.

Run `sbt test` for the exploration engine tests. After `sbt fastOptJS`, run
`python3 tests/exploration_smoke.py` for destination gating, previews, supplies,
concurrent parties, save reloads, and phone/desktop layouts.

## Default workers

After exploration is unlocked, **Default Workers** becomes available in Upgrades
for 500 sugar and 250 colony points. It unlocks a **Default task** selector in
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
Worker costs display the active pool's name and available amount. Existing saves
retain their behavior until the upgrade is bought and a default is selected.

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
