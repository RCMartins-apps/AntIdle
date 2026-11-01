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
`Actions.scala` or `TickUpdater.scala`. Existing saved capacities stay additive;
the version-1 save migration still runs only when loading old saves.

## Exploration progression prototype

Your first expedition brings back a dead insect. Assign **Carcass Harvesters** in
Tasks or directly in Explore. Each harvester consumes 0.8 food per second from
the oldest stored insect, offsetting the colony's sugar upkeep. Surplus food is
consumed rather than stored; assigning more workers finishes the insect sooner
but takes those workers away from other tasks. Up to three insects can be stored.
When the last insect is exhausted, harvesters return to the idle worker pool.

A nearby insect contains 120 food and yields 5 protein only when fully harvested.
The first protein reveals the **Nursery Chamber** in Nest. Its first level costs
100 tunneling space and 3 protein and supports three scouts; later levels cost
more. **Raise Scout Egg** costs 50 sugar and 2 protein. The egg matures in 30
seconds, uses normal egg/colony capacity, and becomes a scout rather than a worker.
Scouts eat sugar at home and are reserved while guiding an expedition. Like
expedition workers, scouts away from the nest do not consume the colony's food.

One available scout unlocks a **Woodland Edge** expedition: 45 seconds instead of
15, twice the sugar cost per worker, and a guaranteed insect with twice the food
and protein if storage has room. Full stores produce a sugar find instead. Near
expeditions after the first can find an insect, sugar, or nothing. Tuning values
live in `game/Constants.scala`; harvesting, scout growth, and expedition rules live
in their progression helpers, keeping tick processing small.

Version-1 and version-2 saves migrate to version 3 on load. Existing capacities
are preserved, and the prior version-1 Nest capacity bonus is applied only once.
New fields use defaults, so old saves do not need manual editing.

Run the complete progression browser checks after building:

```sh
python3 tests/progression_smoke.py
```

These cover discovering and harvesting an insect, temporary food support,
protein costs, Nursery construction, scout maturation, woodland expeditions,
saving/reloading at intermediate stages, and phone/desktop layouts.
