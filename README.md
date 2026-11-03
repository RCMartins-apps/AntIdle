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

Each returned carcass grants protein immediately (5 nearby, 10 woodland), up to
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

## Exploration experience

Every completed expedition earns XP, even if it finds nothing: 10 XP nearby or
30 XP at the woodland edge, independent of party size. Exploration starts at
level 1. Reaching level 2 costs 100 XP; each subsequent level costs twice as much
(200, 400, 800…), up to level 20. Each level adds 25% to sugar finds and newly
discovered carcasses' sugar and protein, without increasing carrier requirements
or retrieval time. The expedition that levels up receives the new bonus.
The Explore tab shows XP progress, current rewards, and the next bonus.
Experience persists in saves; existing saves start at zero XP.
