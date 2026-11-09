# Ant Idle

For development context, start with [AGENTS.md](AGENTS.md). The repository keeps
the [game vision](docs/VISION.md), [agreed design decisions](docs/DECISIONS.md)
and [latest handoff](docs/HANDOFF.md) so work can continue across Codex sessions.
These distinguish implemented behavior, approved direction and tentative ideas;
the sections below document existing mechanics and setup. The dated
[game-state review](docs/GAME_REVIEW.md) separates current strengths from proposed
priorities; [recorded ideas](docs/IDEAS.md) preserves possibilities without
turning them into implementation commitments.

A Scala.js incremental game. Build with Java and the sbt version pinned in
`project/build.properties`:

```sh
sbt fastOptJS
python3 -m http.server 8000
```

Open the served `index.html`. Use `sbt '~fastOptJS'` to rebuild while developing.
Production validation follows CI: `sbt fullOptJS test`.

PR tests and deployment share a lightweight change check. Changes limited to
`docs/**` and root Markdown files skip game builds/tests and deployment; a mixed
or non-documentation change runs normal validation. The workflows still report
completed/skipped job statuses, so documentation PRs do not leave required checks
pending. Manual PR-test dispatch always validates the game. Main deployment and
same-repository `debug*` PR deployment retain their existing branch restrictions.
Unknown comparisons run validation conservatively. Non-documentation PRs also
run a small production-bundle browser gate at 390px and 1280px, covering navigation,
disclosure stability, task controls, launching, founding and remembered resources.
Failure screenshots are uploaded as workflow artifacts. The broader smoke suite
remains available for feature-specific checks. Run this gate locally after
`fullOptJS` with `python3 tests/ci_browser_smoke.py`. Check the change policy with
`python3 tests/ci_changes_test.py`.


On phones (up to 767 CSS pixels), panels fit the screen, tabs scroll horizontally,
and actions use full-width touch targets. Tap the **info icon** beside an action to expand its
explanations and effects. The icon has a 44px touch target and also supports
keyboard activation; closed details add no extra row, even for disabled actions. Costs remain visible beside every action. **Action amount** provides the same ×1, ×10, ×25, and
×250 options as desktop Ctrl/Shift shortcuts for laying eggs and assigning workers.
Resource totals use compact numbers and negative income is red. Upgrades use
one column on phones and two on larger screens, with a short empty state when
there is no research to buy. Explore shows unmet party minimums beside routes
and expedition-slot availability below the launch area.
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
Run `python3 tests/ui_polish_smoke.py` for focused checks of readable totals,
research layout and empty states, and expedition availability feedback.

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
50 sugar and 2 protein when laid, developing in about 90 seconds at full base
care with another 20 sugar and 2 protein in larval feeding. A scout can guide a 45-second
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
Nursery unlocks scout eggs without imposing a separate scout limit. Brood capacity
limits eggs, larvae and pupae together; pending worker/scout orders reserve
future colony housing. Save format 6 stores this as `maxAnts`; older save formats are no longer supported.

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
the action. Reward odds and capacity requirements are available through mobile
**info icons** on phone and desktop. Locked destinations are
hidden: scout routes appear after Nursery/scout training, and Rotten Log also
requires level 3. Unlocked routes remain visible when scouts are away or resources
are insufficient. Details omit already-satisfied unlock levels and focus on
current costs, staffing and slots. Supplies appear at level 2; later ranks appear at their unlock
levels rather than as locked actions.
Sugar costs are 10, 20, and 30 per worker for the three destinations respectively.

Party size improves both XP and all resource rewards, with diminishing returns:
1 worker gives 1×, 5 give 2×, 17 give 3×, and 37 give 4×. New parties cap at 37 workers.
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
| Brood Care | Exploration level 4 and a Nursery | 2,000 sugar, 40 protein, 180 Knowledge | +25% brood-care throughput when supplied (about 20% less development time) |

Nectar Meadow trades workers, a scout, sugar supplies and an expedition slot for
a predictable sugar return without carcass storage. Base rewards are 300 sugar,
8 Knowledge and 45 XP; party size, supplies and exploration level scale these
using the existing expedition rules. A five-worker party at level 4 costs 200
sugar and returns 1,050 sugar plus 28 Knowledge before any supplies bonus. The
compact route summary shows the sugar return; costs stay visible on the action.
Rewards obey normal storage caps, and in-flight expeditions survive save reload.

Reinforced Mandibles updates task descriptions, resource income estimates and
actual tick production together. Brood Care raises care throughput for existing
and new developing brood by 25% when supplied; legacy already-laid eggs retain
their earlier growth model. Both upgrades are permanent and saved.

Run `sbt fastOptJS fullOptJS test`, then `python3 tests/colony_smoke.py` to verify
hidden content, purchase costs, tunneling production, maturation times, meadow
returns and save persistence on phone and desktop.

## Exploration discoveries

Completed expeditions now survey their destination. Survey progress appears as
one compact line in the Discoveries tab; discovery names and rewards are hidden
until found. Each discovery is guaranteed after a fixed number of returns,
announced once, and saved. There are no random discovery rolls or duplicate
rewards. Departures and unrelated destinations do not advance surveys.

- **Wild Aphids:** five Woodland Edge returns reveal Aphid Tending research.
  Pay 1,500 sugar, 20 protein and 100 Knowledge to unlock Aphid Tenders in Tasks.
  Research opens the Aphid Chamber in Nest. Each chamber level adds exactly
  one tender slot. Each staffed slot produces 0.8 sugar/s and consumes 0.01
  protein/s, independently of Foraging Techniques. Tending pauses without
  protein; a partial final feed produces proportional sugar without debt.
  Chamber upgrades cost tunneling space (starting at 75, growing by 14%) and
  protein (2 per target level). Assigned/capacity counts stay visible in Tasks;
  full chambers disable assignment. Batches, default workers, returning workers,
  new brood and save reload all enforce capacity, leaving overflow idle when
  Aphid Tender is the default task. Sugar and protein income estimates match
  actual fed production.
- **Seed Cache:** three Nectar Meadow returns reveal Seed Gathering once the
  colony reaches exploration level 5. The route needs at least ten workers and
  one scout, costs 25 sugar per worker, and takes 120 seconds. Base rewards are
  10 protein, 12 Knowledge and 60 XP, scaled by the existing expedition rules.
  Protein is guaranteed, obeys its storage cap and needs no carcass retrieval.

Known discoveries stay listed in the Discoveries tab, with optional [i] descriptions of what each
unlocks. Survey counts default to empty for saves that do not contain them;
existing XP does not automatically grant discoveries. Research restores the new
task immediately, including on load.

After `sbt fastOptJS fullOptJS test`, run `python3 tests/discovery_smoke.py` for
phone and desktop checks of survey completion, hidden content, research costs,
actual aphid income, default workers, seed expeditions and save/reload behavior.

Resource rows remain visible permanently once unlocked, including at zero amounts.
Visibility is reconciled on actions, ticks and save load; protein no longer depends
on having a positive balance, and scout visibility survives losing every scout.

**Protein Storage** becomes available after exploration, protein discovery and
unlocking Food Storage. It costs 1,000 sugar, 25 protein and 75 Knowledge. Research
makes every Food Storage Chamber level add 100 protein capacity alongside its
sugar capacity. Existing levels count immediately: level 3 gives a 400 total
protein cap (100 base + 300 from chambers), and level 4 gives 500. Chamber details
show both bonuses. Capacity is derived from research and chamber level so loading
or reconciling repeatedly never duplicates the bonus; research does not grant
stored protein. Run `python3 tests/protein_storage_smoke.py` after `sbt fastOptJS`
to check purchase costs, both chamber bonuses and saved capacity on phone/desktop.

## Colony lifecycle

In new colonies, the first small founding adult activates aging immediately.
Small workers live 30–40 simulated minutes; standard workers/scouts live
45–60. Legacy standard-only colonies still activate aging with their first
Nursery. Existing adults receive a full grace period when aging begins. All durations use simulated ticks: loading does not consume stored
time or instantly age the colony. The existing catch-up multiplier advances
aging alongside other game systems when the player enables it.

`PopulationAging` stores counts in ten-second expiration cohorts rather than
one record per ant. Seeded random placement spreads a birth batch across the
45–60 minute range; expiry ticks are assigned once and never rerolled on load.
Idle workers are used first. After Queen's Routine is unlocked, the selected
default pool absorbs further home-worker aging losses before other jobs are
reduced. Scouts age independently. Home deaths never remove expedition workers, carriers or
reserved scouts. Expeditions and carcass retrievals carry their cohorts with
them with unchanged expiry ticks. Aging continues while away, but deaths are
deferred until return: overdue ants complete the trip and die immediately on
arrival. Non-expired ants keep their original expiry, so repeated expeditions
cannot make them immortal. Travel cohorts and home cohorts survive saving/loading, and
simultaneous returns preserve both worker and scout ages. Starvation also
respects away-worker reservations and reconciles the remaining home cohorts.

Brood shows a compact forecast of home losses in the next five minutes, with
lifespan rules under optional details. Aging deaths generate at most one summary
per simulated minute, rather than one message per ant. Resource rows remain
visible after the population reaches zero.

**Queen's Routine** appears at exploration level 3 with a Nursery. It costs
1,200 sugar, 20 protein and 100 Knowledge and unlocks opt-in automatic worker
eggs in Brood. The player sets a worker target and sugar reserve; automatic eggs
cost the current worker type's egg price, just like manual orders. Small and
standard adults, developing worker brood and queued manual worker orders count
toward the target. Scouts remain manual and share the queen's laying cooldown
and brood places. Automation runs after upkeep/care, lays at most one egg when
the queen is ready, respects its reserve and stops when the target is covered.
Configuration is saved; a compact status explains when it is paused, limited by
slots, holding reserves or already meeting the target.

Buying Queen's Routine also maintains jobs after **old-age losses**, even while
automatic egg laying is paused. Idle/default workers fill aging vacancies;
when the pool runs out, missing jobs are remembered and filled as workers hatch
or return. Standard workers can replace small workers in their jobs, respecting
shared scraps/nurse capacities; spare small sugar collectors can fill their
supported jobs too. Reassignment does not reset lifespans or create workers.
Expedition/carrier/territory crews are never borrowed for this maintenance.
Starvation and combat do not create these aging replacement requests.

Pending jobs appear under Queen's Routine and survive save/reload. Editing a
job cancels its outstanding aging request; **Cancel replacements** clears all
pending requests. Making a job the default also removes its fixed replacement
request. Recalled or depleted nectar trails discard their requests. Run
`python3 tests/aging_jobs_smoke.py` for pool preservation, delayed nurse
replacement, small-to-standard transitions, cancellation and phone/desktop UI.

After `sbt fastOptJS fullOptJS test`, run `python3 tests/lifecycle_smoke.py` for
phone and desktop checks of grace periods, actual deaths, replacement, travel
death deferral, save/reload, targets/reserves and the stored-time multiplier.

## Construction planning and colony nutrition

**Construction Planning** unlocks at exploration level 2 for 500 sugar and 50
Knowledge, increasing construction queue capacity from one to three. Planned
upgrades use the chamber level after earlier plans of the same type. Cancelling
an entry reprices later entries, and completing construction charges the proper
next-level cost. Loading repairs stale queued prices. Queued upgrades show their
target level and resource costs; select the title or press Enter/Space to cancel.
A full queue disables further chamber plans. Only the first entry shows a wait
estimate; later entries say Queued. Protein shortages now correctly show Never
until supplies arrive, and estimates round up rather than showing zero too soon.

**Colony Nutrition** unlocks at exploration level 5 once aging is active. It costs
2,500 sugar, 60 protein and 200 Knowledge. New adults live 20% longer (54–72
simulated minutes), with the same seeded lifespan jitter. Existing expiry ticks
stay fixed, including while traveling. Aging details show the current lifespan
range. Research and construction planning survive saving/loading.

Run `sbt fastOptJS fullOptJS test`, then `python3 tests/management_smoke.py` for
phone/desktop checks of upgrade gates, queue pricing/cancellation, protein waits,
actual spending, nutrition and saved lifespans.

## Dry Clearing foundations and statistics

Four Rotten Log returns discover the **Dry Trail**. It reveals **Dry Clearing**
at exploration level 6: a 120-second survey expedition needing ten workers and
two scouts, costing 35 sugar per worker, with 75 base XP and 25 base Knowledge.
Three clearing surveys discover **Dew-covered Plants**, unlocking **Scavenge
Water**. That 90-second route needs five workers and one scout, costs 25 sugar
per worker and grants 20 base water, 5 Knowledge and 30 XP. Party size, supplies
and exploration level scale rewards using the existing rules. Neither new route
creates carcasses. Names and routes stay hidden until their discovery gates.

Water starts with 100 storage capacity and a local droplet SVG. Its resource row
appears on first collection and never disappears, including at zero after a
reload. Water is saved, storage-capped, supported by resource costs/affordability,
and ready for future outpost projects. This step provides exploration and water
supplies; the Dry Clearing outpost below adds water-dependent production.

Settings has a collapsed **Colony statistics** view. Both current-colony and
lifetime counters are persisted for future milestones. Resource gains count what
was actually collected, excluding discarded overflow at full stores; passive
sugar used directly for upkeep is counted once. Spending never reduces collection
progress. Protein from retrievals and seed missions and water from scavenging use
the same award path as other resources. Launched and completed expeditions are
separate counters; duplicate completion attempts cannot award progress twice.
Maturation, retrievals, aging losses, starvation losses and observed peak workers
are tracked as well. Counters saturate rather than wrap when reaching integer
limits. Existing statistics are retained; missing historical events are not
invented or reconstructed from current resource balances.

Run `sbt fastOptJS fullOptJS test`, then `python3 tests/water_smoke.py` for the
complete discovery chain, water collection/caps, permanent visibility and saved
statistics on phone and desktop.

## Expedition staffing and discovery navigation

Discoveries and survey progress now live in their own **Discoveries** tab, which
appears after the first survey progress is earned. Only unlocked areas appear as
survey targets; discovery names stay hidden until found. Explore contains the
party planner, route actions, supplies and active/retrieving parties.

New parties are limited to 37 workers, the existing 4x reward ceiling. The numeric stepper
and engine enforce this limit; quick picks expose the useful 1/5/17/37 worker
reward milestones. Selection clamps to remaining available workers after a
launch or population change, so displayed costs always match the actual party.
Existing saved parties still return their full worker count.

Players may add up to three **extra scouts**, beyond each route's required
scouts. Each adds 25% travel speed and costs 10 sugar in expedition supplies.
Travel time is rounded up to whole ticks: a Woodland Edge trip with its required
scout plus three extras takes 25.8s instead of 45s. The same per-trip rewards and
XP arrive sooner, freeing slots and advancing surveys more often. Route previews
show the real duration and sugar cost; scout requirements show the total needed.
The planner hides extra-scout controls until scouts are unlocked. Active trips
show reserved scout counts; saved parties retain their speed, supplies, ages and
reservations. Expiry ticks remain fixed and overdue ants still die on return.

After `sbt fastOptJS fullOptJS test`, run `python3 tests/staffing_smoke.py` and
`python3 tests/discovery_smoke.py` for mobile/desktop navigation, caps, costs,
reservations, saved timing and discovery progression checks.

Expedition worker selection uses a numeric field with 48px minus/plus buttons
instead of a slider. Mobile browsers show a numeric keyboard; direct typing,
presets and button taps update costs immediately. Invalid/out-of-range values
clamp to available workers and the reward limit; an empty edit restores the
current selection on blur. Buttons disable at either boundary, and the field
disables when no workers are available.

## Repletes: living water storage

Discovering Dew-covered Plants reveals **Replete Storage** research (1,500 sugar,
20 protein, 150 Knowledge). It unlocks the **Replete Chamber** and **Promote
Replete** in Nest. Each chamber level houses one replete; building housing alone
does not create storage ants or fill water. Chamber construction starts at 100
tunneling space and 5 protein, with space costs growing by 25% and protein costs
by 5 per target level.

Promotion costs 100 sugar, 5 protein and one available worker. The oldest living
home worker is removed from worker age cohorts and converted into a replete,
with a fresh randomized 90–120 minute lifespan from promotion (108–144 minutes
with Colony Nutrition). It is a worker conversion, not an egg: population stays
the same, and normal sugar upkeep continues. The idle/default worker pool is
used; other assigned jobs and away ants are not borrowed. Repletes cannot take
worker tasks or join expeditions. Promotion is irreversible, and its fresh
expiry is saved rather than rerolled on load.

Water capacity is 100 base plus 100 per living replete. Repletes have their own
age cohorts, housing counter, permanent resource row and promotion/aging
statistics. Their deaths reduce capacity and free housing. Existing water is
never truncated when capacity falls, during later resource awards/ticks, or on
load. Over-capacity water remains spendable, while collection pauses until the
amount is below the new cap or another replete restores enough capacity. New
storage capacity never grants free water.

Run `sbt fastOptJS fullOptJS test`, then `python3 tests/replete_smoke.py` to check
research, construction, promotion costs, oldest-worker selection, fresh life,
conserved population/upkeep, housing limits, saved cohorts and loss-free water
capacity changes on phone and desktop.

## Dry Clearing Outpost

After reaching exploration level 6 and discovering Dew-covered Plants, **Nest →
Outposts** reveals **Establish Dry Clearing Outpost**. Establishment costs 3,000
sugar, 40 protein, 300 Knowledge, 750 tunneling space and 150 water. The base
water cap is only 100, so living water storage is useful preparation. All costs
stay visible, including a red requirement when it exceeds current storage.

Establishing the outpost is a one-time purchase. It adds 50 shared colony
capacity and five **Seed Forager** slots in Tasks. It extends the current colony,
without a prestige reset, a new queen or a separate population to manage.

Before further research, each forager gathers 0.04 protein/s and consumes 0.02 water/s, plus normal ant
sugar upkeep. Five staffed slots produce 0.2 protein/s for 0.1 water/s. The
protein represents edible seeds gathered near the satellite nest; water supports
foraging in the dry area. Production pauses when water runs out or protein
storage is full. Partial water and remaining protein storage allow proportional
production; no water is spent on overflow. Aphid feeding frees storage before
that tick's harvest, so foragers can replace protein used by tenders.

Housing remains available when water runs out. Repletes losing water capacity
still do not discard stored water, which remains available to the outpost.
Assignment, batches, the default-worker pool, expedition departures/returns,
aging and save restoration use the same rules as other normal tasks. Foragers
cannot exceed the current outpost staffing limit even if an imported save over-assigns them. Establishment,
assignments and existing colony totals persist without granting housing twice;
saves without outpost ownership default to an unestablished outpost. Resource rates and collected
protein statistics follow actual tick production.

`OutpostProgression.scala` defines the founding cost, housing, staffing and
production rules. Run `sbt fastOptJS fullOptJS test`, followed by
`python3 tests/outpost_smoke.py` for phone/desktop establishment, costs,
water/storage shortages, production, pause/resume, defaults and save checks.

## Outpost growth and specialization

The Dry Clearing Outpost can grow from level 1 to level 3. Expansion actions
stay hidden until their exploration-level gate is met. Each paid expansion adds
25 shared colony capacity and five Seed Forager slots without creating workers.
At level 3 the outpost contributes 100 housing and supports 15 foragers in total.

| Target outpost level | Exploration level | Sugar | Protein | Knowledge | Tunneling space | Water |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 2 | 7 | 5,000 | 75 | 500 | 1,000 | 250 |
| 3 | 8 | 8,000 | 125 | 800 | 1,500 | 350 |

The water costs make larger living reserves useful: collecting 250 water needs
capacity from at least two repletes above the base 100, and 350 needs three.
Existing water retained above a reduced cap remains spendable. The second expansion's
125 protein also exceeds the initial protein cap, giving Protein Storage a
practical role. All costs stay visible, with over-cap requirements shown in red.
Housing survives dry periods, and default Seed Foragers fill new slots only from
existing available workers. The engine prevents repeat purchases beyond level 3.

Two research upgrades extend this path; both require an established outpost and
remain hidden before their exploration-level gates:

| Research | Level | Sugar | Protein | Knowledge | Water | Effect |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Covered Trails | 7 | 3,000 | 60 | 300 | 100 | Sheltered routes reduce water use by 25%, to 0.015 water/s per forager |
| Seed Processing | 8 | 6,000 | 100 | 700 | 150 | Husking and processing yields 50% more protein, to 0.06 protein/s per forager |

Both effects stack with expanded staffing. Fifteen foragers with both upgrades
produce 0.9 protein/s and use 0.225 water/s, plus normal sugar upkeep. Partial
water and free protein storage limit actual production; water spending rounds
up to the smallest fixed-point unit needed for the protein retained. Full stores
or empty water still pause production. Aphid feeding and gathered-protein
statistics use the same shared tick calculation.

Task descriptions, resource estimates/tooltips and outpost summaries show the
upgraded values. Small rates such as 0.075 water/s retain their precision;
negative tooltip rates retain their minus sign. Expansion counts and research
are saved without reapplying housing bonuses. Existing outpost saves missing the
extension field load as level 1, and imported extension counts cannot exceed two.

Run `sbt fastOptJS fullOptJS test`, then
`python3 tests/outpost_progression_smoke.py` for level gates, purchases, staffing,
actual upgraded production, rates, old/current saves and phone/desktop layouts.

## Woodland resin and the Royal Nursery foundation

Resin is the first material used for advanced chamber construction. Its amber
SVG, storage (200 resin), costs and gathered statistics use the same resource
paths as sugar, protein and water. Discovering its source reveals the resource
row at zero; the row remains visible after spending the last unit. Statistics
count only material retained, excluding overflow. Existing early chambers keep
their costs and do not require resin.

| Expedition | Unlock | Duration | Workers / scouts | Sugar per worker | Base rewards |
| --- | --- | --- | --- | ---: | --- |
| Fallen Pine | Exploration level 7 | 120s | 10 / 1 | 30 | 80 XP, 30 Knowledge; surveys the area |
| Collect Resin | Level 7 and Resin Seep discovery | 120s | 10 / 2 | 35 | 5 resin, 50 XP, 10 Knowledge |

Three completed Fallen Pine surveys discover **Resin Seep**, announced once.
Collection stays hidden before the discovery; workers and scouts are reserved
normally and return to the default worker pool. Rewards scale with party size,
supplies and exploration level, including a level reached on return. Neither
expedition uses carcass storage. In-flight collection trips survive saves.

**Royal Nursery Planning** becomes visible at exploration level 8 after the
resin discovery, with an ordinary Nursery already built. Research opens a
**Royal brood** section in Nest with the one-time **Royal Nursery** build action.

| Stage | Sugar | Protein | Knowledge | Water | Tunneling space | Resin |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Planning research | 5,000 | 100 | 700 | 150 | 0 | 0 |
| Nursery construction | 3,000 | 50 | 0 | 100 | 1,500 | 80 |

All costs remain visible. Construction uses the existing queue: it can be
planned while material is missing, shows a red Never wait until resources
arrive, and charges all costs only on completion. Duplicate royal plans and
construction after the first nursery are rejected. Loading repairs stale costs
and removes invalid or duplicate royal plans without spending resources.

Building the nursery unlocks **Royal Nurse** in Tasks and **Raise Royal Egg**
in Nest. It does not add ordinary egg slots or colony housing.

### Raising a daughter queen

Royal Nurses are assigned from ordinary workers, with a maximum of five. They
keep normal sugar upkeep and aging, and can be selected as the default task;
excess workers stay idle when its five slots are filled. Expeditions can borrow
from that default pool, reducing royal care until nurses return.

Raise Royal Egg costs **500 sugar, 50 protein and 20 water**. It requires a
built nursery, at least one assigned Royal Nurse and one free colony housing
slot. It reserves that housing slot throughout rearing; ordinary egg slots are
independent. One royal brood or mature daughter occupies the nursery.

| Stage | Time with five nurses | Sugar/s per nurse | Protein/s per nurse | Water/s per nurse |
| --- | ---: | ---: | ---: | ---: |
| Royal egg | 1 minute | 0 | 0 | 0.01 |
| Royal larva | 8 minutes | 0.5 | 0.05 | 0.01 |
| Royal pupa | 1 minute | 0 | 0 | 0.01 |

Rearing uses **1,200 sugar, 120 protein and 30 water** beyond the starting cost
and normal nurse upkeep. The total is the same with fewer nurses, but care
takes proportionally longer: one nurse needs 50 simulated minutes. Feeding is
charged only for care actually performed, including partial supplies and the
last tick of each stage. Existing Brood Care research changes ordinary worker/
scout development, not this royal timeline. Stored-time multipliers advance
royal care with the rest of the simulation.

Nest displays the current stage, overall progress, staffing, stage feeding rates
and estimated remaining time with supplied care. Missing nurses, sugar, protein
or water show the relevant reason; partial supplies slow care. **Pause care**
stops feeding and preserves progress, with a saved **Resume care** control.
Nurses remain ordinary workers with upkeep while care is paused or no brood
exists. Adult upkeep is charged first, then royal care, then automatic worker
eggs; fresh sugar production can feed royal brood directly even at full storage.
Resource rate estimates include actual affordable care.

Maturation replaces the brood reservation with a **daughter queen**, counts it
in population, updates royal egg/maturation statistics and announces completion
once. Its resource row remains visible after unlocking, including at zero. A
daughter consumes normal adult sugar upkeep; it does not use worker aging
cohorts, but can starve after other home adult ants. It remains in the nursery
until a future founding feature uses it. This step grants no Understanding and
does not reset or create a colony; the first permanent reward and reset rules
still need design.

Old saves default to zero resin, an unbuilt royal nursery and unowned planning
research. Current saves retain material, discovery, queued costs, the completed
chamber and resource visibility without granting anything twice. Run
`sbt fastOptJS fullOptJS test`, then `python3 tests/royal_nursery_smoke.py` for
hidden gates, surveys, gathering, statistics, costs, queue waits, completion,
old/current saves and phone/desktop layouts.

Royal brood uses additive save fields: older saves default to no royal brood,
no daughters and zero royal statistics. A completed nursery restores its nurse
task on load without assigning workers. Progress, manual pause, nurses and
mature daughters survive reload without repeating maturation rewards. Run
`python3 tests/royal_brood_smoke.py` after `sbt fastOptJS` for phone/desktop
staffing, costs, stages, rates, shortages, pause/reload and completion checks.

## First prestige: DNA and Shared Paths

The **Lineage** tab appears with a Royal Nursery, a mature daughter queen or a
previous founding. It holds generation/DNA, readiness, Understandings and
**Review founding**, which unlocks with a mature daughter queen. The preview and
confirmation list the reset before **Found daughter colony** takes effect:

- Reset colony resources, ants/brood, chambers, research, expedition XP,
  discoveries, trails, queues and worker assignments.
- Keep DNA, purchased Understandings, lifetime statistics, discovered resource
  visibility, settings and stored simulation time. The new colony begins with
  its queen and no workers, using the normal initial economy.
- Each founding awards **1 DNA** and automatically adds **+10% sugar production**,
  stacked additively (+10%, +20%, etc.). Collectors, aphids and trail foragers
  receive this bonus. Sugar gathering clicks, expedition rewards, costs, upkeep,
  aging and consumption are unchanged. Per-colony statistics restart at zero.

The first Understanding, **Shared Paths**, costs **1 DNA** once. Ownership is
inherited across later foundings. This is the first introductory prestige
mechanic; later introductory Understandings, challenges and milestone-based
DNA earnings remain future content. Repeated founding currently awards the
same DNA amount and adds the same production bonus.

After Shared Paths, returning **Nectar Meadow** expeditions reveal a finite
**1,200 sugar** source when no source remains. The normal expedition reward is
unchanged. Explore shows one source/trail, without exposing locked destinations.

**Mark trail** costs **50 sugar** and reserves **one scout**. Assign up to ten
workers with the trail card's controls. Each gathers **0.8 sugar/s**, improved
by the inherited production bonus; output is limited by actual storage room
and remaining supply. The bonus increases harvesting speed, not source size.
The card shows workers, sugar/s and remaining sugar. At full storage, gathering
pauses without wasting supply. **Recall trail** frees workers and the scout,
keeping unharvested sugar; marking it again costs 50 sugar. Depletion releases
staff automatically. Workers return to their selected default task, respecting
its capacity, with any overflow idle.

Trail workers and the guiding scout retain ordinary upkeep and saved age
cohorts. They do not gain new lifespans when assigned or recalled. A lost guide
ends active gathering and releases workers while preserving the source.
Lineage/DNA/Understanding state is stored in `ants.lineage`; the source and
marking state are in `exploration.sugarTrail`. Older saves receive safe defaults.

Verification: `sbt fastOptJS fullOptJS test`, then
`python3 tests/prestige_smoke.py` checks founding cancellation/confirmation,
DNA purchases, early unlocks after reset, trail discovery and management,
save/load and phone/desktop layout. Engine tests cover resets, lifetime totals,
bonuses, finite supply, scout reservations, upkeep, aging and defaults.

## Dangerous expeditions: Rival Food Cache

**Raid Rival Food Cache** unlocks at exploration level 5. Existing routes have
no combat losses. A raid takes 120 simulated seconds, requires 10–37 workers
and one scout, and costs 30 sugar per worker. Extra scouts use the existing
10-sugar cost and travel-speed bonus; they do not reduce combat losses.

| Party | Guaranteed worker combat losses | Protein haul before storage cap |
| --- | ---: | ---: |
| 10 workers | 2–4 | 200–350 |
| 37 workers | 4–10 | 740–1,295 |

Intermediate parties use a gradually lower percentage loss range with integer
rounding. Counts, certainty, reward range and costs are shown directly beside
the action, not only in Details. Protein storage room is visible and red if it
cannot hold the maximum haul. Storage overflow is discarded; it does not reduce
casualties. Scouts have no combat losses. The in-flight party retains its risk
notice. There is no additional confirmation dialog for an already disclosed risk.

The loss count is uniform over its displayed inclusive integer range. The
protein roll is uniform over integer values 200–350 per ten workers, then scaled
linearly with party size. Exploration level, supplies and inherited sugar
production do not increase this raid protein range or change casualties. Normal
XP, Knowledge and extra-scout speed rules still apply.

Departure selects the **oldest home-worker age cohorts first**. Combat deaths
sample individual workers uniformly without replacement from that party, rather
than always killing its oldest members. Surviving workers and all scouts keep
their original expiry ticks. An extra visible warning lists selected workers
and scouts whose lifespans expire before the scheduled return. Aging can cause
additional losses, including losing every surviving worker in an overdue party;
aging losses overlap combat deaths and are counted separately.

A seeded raid outcome is saved on departure, including its casualty-selection
seed. Reloading, other departures or delayed returns cannot reroll it. On return,
combat casualties and their age cohorts are removed before ordinary overdue
aging is resolved; only surviving workers return to the default-task pool.
The existing Queen's Routine can replace the resulting worker deficit without
any new automation research. Separate **Raid losses** run/lifetime statistics
persist across saves; prestige resets the run counter and retains the total.
Old non-raid parties default to no raid outcome and zero raid deaths.

Run `sbt fastOptJS fullOptJS test` and `python3 tests/raid_smoke.py` to check gates,
visible costs/risk/storage/aging warnings, oldest-first selection, random
casualties, saved outcomes, survivor lifespans, automatic replacements and phone/
desktop layout. Raid rule tests cover full return ticks and replay protection.

## Optional territorial contests

Returning from the first Rival Food Cache raid discovers **Contested Foraging
Ground**, opening a separate **Territory** tab. Existing safe play and prestige
remain available without holding it. No additional prestige Understanding,
scout or expedition slot is required.

Choose a fixed target of **10–37 workers** and pay **20 sugar per worker** to
**Hold Foraging Ground**. Workers are taken oldest-first from the available
idle/default pool; other assigned jobs remain protected. While holding, each
worker contributes **0.3 protein per simulated second** to an unfinished haul.
Every **60 simulated seconds**, rivals contest the site:

- Combat losses are guaranteed and uniformly drawn from the displayed count
  range: **2–4 of ten** or **4–10 of 37** workers. Casualties are random
  individuals among holders. Below ten, smaller remaining crews still face
  combat and can eventually be wiped out; a lone holder certainly dies.
- Combat survivors retain their expiry ticks. Overdue holder aging resolves
  after combat, without counting the same death twice. It can cause additional
  losses, including losing the whole remaining crew. Aging warnings show which
  holders expire by the contest.
- If workers remain, the haul arrives, limited by actual free protein storage.
  Ten workers present for a complete interval collect **180 protein** before
  that cap. Only delivered inventory counts as collected resource statistics.
- Full protein storage does **not** pause contests or casualties. If nobody
  survives, the site and unfinished haul are lost, without automatic reclaim.

**Withdraw** immediately stops the hold and returns workers to their default
pool, respecting task capacities. Original age cohorts are restored and overdue
aging is resolved; no new lifespan is granted. The unfinished haul is left
behind, while previously delivered protein remains. Reclaiming costs sugar
again and begins a new interval. This prevents banking resource income by
withdrawing just before every fight.

**Reinforce** fills missing places from available workers, up to the fixed
target, with no additional recruitment resource fee. **Maintain staffing
automatically** is initially off; enabling it performs the same recruitment
on simulation ticks. It may draw workers from the selected default task, but
never other assigned jobs. Newborn replacements from the existing Queen's
Routine can restore the colony while this setting maintains the holding crew.
Target changes require withdrawing and making a new claim.

Holders consume ordinary colony sugar upkeep and can starve. Their reserved
age cohorts remain separate from home workers; normal home aging cannot remove
the wrong holders. Their aging is settled at each contest or withdrawal, so
continuous occupation cannot make them immortal. `AntTask.agesAtHome` is
separate from upkeep for this purpose. General protein/s estimates describe
immediately available production; the Territory card shows the pending haul's
rate and its delivery countdown separately.

The tab displays staffing, reinforcement availability, countdown, haul/rate,
combat range, storage and age warnings, withdrawal and automatic staffing.
Saved `exploration.territory` contains its crew's cohorts, target, staffing
setting, next contest tick, seed, round and pending haul. Reloading does not
reroll combat or reset its countdown. Territory combat deaths, contest count
and claims have run/lifetime statistics; prestige resets the hold and current
run counters while retaining lifetime totals. Old saves default to no hold;
colonies with a completed raid in the current run recover the new discovery
on load, without repeating that raid.
Stored-time acceleration advances contests and worker aging with normal ticks.

Run `sbt fastOptJS fullOptJS test` and `python3 tests/territory_smoke.py` to verify
claim costs/discovery, recurring losses, delayed/capped deliveries, oldest
cohorts, fixed saved contests, aging/starvation, reinforcements, automatic eggs,
withdrawal and phone/desktop layout.

Temporary vulnerable food sources and soldier specialization are deferred
content ideas; neither is added by territorial contests.


## Founding workers and staged brood development

Fresh colonies and new prestige generations begin with **small founding
workers (nanitics)**. They develop in about 15 simulated seconds, so the opening
still produces useful workers quickly. Newly laid small eggs cost **5 sugar**;
founding care uses no protein resource. Remaining unassigned small workers
collect sugar automatically, while the Tasks panel can move them to tunnelling,
nearby scraps gathering or nursing when those jobs are available.

Small workers provide **50% of standard task output**, including tunnelling,
protein gathering and nurse contribution. They consume **half normal sugar
upkeep** and live **30–40 simulated minutes** from adulthood, or **36–48** with
Colony Nutrition. Their saved cohorts never reroll. They occupy housing and
count toward total-worker targets, but cannot substitute for the standard
worker pool used by expeditions, retrievals, territory holding or promotions.
Their resource row remains visible after discovery, including at zero.

**Standard Worker Development** appears after five small adults, opening the
Upgrades tab if needed. It costs **75 sugar and 10 tunnelling space**, with no
Knowledge or protein prerequisite. Research permanently changes future worker
eggs in this colony to standard workers. Existing small adults and already-laid
brood keep their type; queued orders that have not been laid use the new type
and price. Small workers continue their existing jobs until reassigned or lost.

Nearby **Scraps Gatherers** unlock with this transition, providing protein
before scouts or expedition rewards are required. There are **two shared
places**: standard gatherers produce **0.05 protein/s** each, small gatherers
**0.025/s**. Full protein storage limits retained production. These nearby
scraps are limited in staffing, leaving larger expedition/outpost sources useful.

### Orders, laying and development

**Raise Small Worker Egg** (then **Raise Worker Egg** after Standard Worker
Development) queues the chosen number of orders instead of creating
an instant batch. Orders are limited by currently affordable egg costs and
future housing space; sugar is charged only as each egg is actually laid.
The visible cost is the queued batch's current egg price. Orders can be
cancelled without a refund because unlaid eggs have not been paid for. If
supplies or space later run short, orders wait rather than disappearing.

**Raise Scout Egg** also accepts an order while the queen is laying or brood
places are full. Scouts and manual worker batches share FIFO order: later clicks
never overtake earlier orders. If the queen is ready and no earlier order exists,
the scout egg is laid immediately. Otherwise its **50 sugar and 2 protein** are
charged when it is laid, not when queued. The status shows pending worker/scout
counts and missing supplies. Cancel egg orders clears both types of unpaid orders
without removing developing brood.

Queued scouts reserve future housing alongside worker orders, including for
royal-brood purchases. Queen's Routine waits for manual orders rather than laying
automatic workers ahead of an unfunded scout; scouts do not count toward its
worker target. Queue state survives saves, and old worker-only queues remain
valid. Internally each queued scout stores the count of worker orders ahead of
it, preserving FIFO without expanding large worker batches into per-egg records.

`python3 tests/scout_queue_smoke.py` covers the busy-queen case, mixed FIFO,
costs, persistence, missing supplies, cancellation, full brood places and housing
on phone and desktop. `ScoutQueueSuite` covers these rules in the engine.

The queen lays one egg at a time, with base intervals of **5 simulated seconds
for small workers** and **20 seconds for standard workers**. Queen chamber
improvements above the initial level reduce this interval. Scout raising uses
the shared cooldown. Eggs, larvae and pupae occupy brood places until adulthood:
capacity is the existing Queen egg capacity plus **three places per Nursery
level**. The Resources panel calls this combined population **Brood**.

Standard workers require about **two simulated minutes at full base care**;
scouts require about **90 seconds**. Both spend one sixth of care as eggs, two
thirds as larvae and one sixth as pupae. Standard worker eggs cost **10 sugar**;
larval care additionally consumes **20 sugar and 2 protein**, charged exactly
as development advances. Scouts retain their 50-sugar/2-protein egg cost plus
the same larval feeding. Shortages pause affected care without losing progress.
The visible brood status names missing sugar/protein and shows pending orders,
occupied places and the queen's cooldown.

The queen supports three brood at base care. **Brood Nurses** add throughput:
a standard nurse supports two additional brood at base speed, a small nurse
one. There are five shared nurse places per Nursery level. Sufficient staffing
can double individual development speed; overloaded care is shared fairly
across brood. Brood Care research raises throughput by 25%. Laying rate,
development space, nutrition and care staffing are separate limits; Queen's
Routine uses this same pipeline and no longer restores a large loss in one tick.
Fresh sugar production can feed larvae directly at full storage, and resource
rate estimates include actual affordable feeding.

Older saves retain standard adults and legacy already-laid eggs/scouts; future
brood uses the new pipeline. New state is additive and needs no version bump.
Existing small-worker saves retain adults, job assignments, life cohorts,
orders and development progress. Loading restores earned task visibility.
The opening engine simulation, starting with 50 gathered sugar, unlocks standard
workers within five simulated minutes and reaches the first standard adult
within ten, with over fifteen minutes before the earliest small-worker deaths.
Those checks cover a deliberate opening strategy, not every possible allocation.

Verification: `sbt fastOptJS fullOptJS test` and
`python3 tests/brood_pipeline_smoke.py` cover the opening, exact half efficiency,
finite lifespans, type transition, staged nutrition, care capacity, shared job
caps, queued laying, saves and phone/desktop controls. Existing lifecycle,
raid/territory replacement and batch UI tests now expect the paced pipeline.

### Emergency colony recovery

The founding queen survives starvation, and **Gather Sugar** is always available.
If standard workers are unlocked but no workers remain, **Raise Emergency Worker**
costs **5 sugar** and rears one small worker without protein. It automatically
collects sugar on maturity. Existing research, brood and egg orders are kept.
Workers away from the nest still count as surviving workers; no emergency egg
is available while they, another worker, small brood or a legacy worker egg exist.

The queen can tend this one emergency brood even if unfed larvae fill normal
brood places or housing. It receives priority care, counts toward population,
and may temporarily put population above capacity; ordinary eggs still obey
normal limits. The Resources brood limit includes its temporary queen-tended
place while it is developing. Normal half output/upkeep and aging apply.

When **Queen's Routine** is unlocked and enabled with a positive target, it also
handles recovery. It spends 5 stored sugar above the player's reserve if possible;
otherwise the queen gathers protected supplies over **25 simulated seconds**, then
rears the emergency egg. These supplies are consumed directly by that egg and
never enter ordinary storage. The usual roughly 15-second development follows.
Saved progress resumes on reload; turning automation off or recovering a worker
clears unfinished foraging. Recovery stops once a worker survives. A red
**Colony is starving** warning in Brood shows the current sugar deficit.

`python3 tests/recovery_smoke.py` checks manual and unattended recovery, visible
costs, paused larvae/full beds, saved progress and mobile/desktop layout.


## Resource capacity and depletion estimates

The Resources panel shows **Full in ~…** below a resource name when its current
net income is positive, or **Empty in ~…** in red while reserves are draining.
Sugar, protein, water and tunnelling space use the same net rates as their
income displays, including existing upkeep/feeding forecasts. At capacity the
label reads **Full**; a draining full store still shows time to empty. Zero-rate
countdowns are hidden, and unlimited Knowledge/population counters have no
capacity forecast. Resin can show Full but has no continuous-income countdown.

Times are approximate simulated time at the current net rate; later purchases,
expedition deliveries and staffing changes can alter them. Fractional ticks
round up rather than displaying a premature zero. Income tooltips share the
same estimate. On phones the timer sits below the name, while the rate sits
below amount/capacity on the right, keeping the compact two-line arrangement.

After `sbt fastOptJS`, run `python3 tests/resource_timing_smoke.py` for filling,
draining, full/steady stores, protein feeding, live countdowns and phone/tablet/
desktop positioning. `ResourceTimingSuite` covers rounding, unlimited capacity,
over-capacity stocks and large values.


## Action and queue feedback

Chamber buttons show a short reason only when blocked: **Build queue full** or,
for a Royal Nursery plan already present, **Already queued**. Cancelling a plan
clears the reason as soon as space is available. Enqueue eligibility and the
message use the same construction check; resource shortages still allow plans
to be queued and remain visible in their costs.

Worker, scout and royal-egg actions share the same future-housing calculation,
including unlaid orders of both ordinary castes. The Royal Egg's colony-slot
indicator therefore turns red when queued eggs reserve the remaining space.
An existing queued egg can still use its own reserved place when laid. If actual
population fills housing and stops laying, Brood says **Colony slots full**
instead of Queen ready. With no orders, the laying cooldown says **Queen ready
in …**, rather than implying another egg has been ordered.

`ActionFeedbackSuite` verifies shared reservations, queued-head laying, blocked
status and construction reasons. `python3 tests/action_feedback_smoke.py` covers
visible slot colors, full/duplicate plans, cancellation and phone/desktop layout.


## Compact screens and [i] help

Default screens focus on controls and live numbers: resource rates, task counts,
prices, progress, shortages and expedition risk. Repeated descriptions such as
small nurses providing half care are removed. Small-worker efficiency appears
once in its heading; task output rates remain visible.

Task and section **[i]** controls reveal useful explanations only when requested.
This includes Brood/aging, Queen's Routine, default workers, royal care, territory,
XP, carcass storage and individual discoveries. Action **[i]** details now work
on desktop too. Native disclosures support touch, Enter/Space and visible focus,
with 44px targets and inline panels that fit narrow screens. Open discovery help
stays open during ordinary resource updates. Action/task prose
is no longer shown automatically on hover; resource-rate breakdown tooltips remain.

Larval/royal material costs, live shortages, imminent aging warnings and dangerous
expedition losses remain visible. No game balance, automation or save changes.
`python3 tests/compact_ui_smoke.py` checks collapsed defaults, information controls,
keyboard interaction, visible costs/rates/warnings and phone/desktop layout.


## Mobile expedition carousel

On phones, Explore keeps worker/scout controls above a single destination card.
Swipe horizontally across the card, or tap previous/next, to compare unlocked
destinations without opening a menu. The title and position counter identify the
current destination; arrows wrap, and keyboard Enter/Space works on them. Vertical
swipes scroll normally. Costs, rewards, discovery badges and danger remain visible.
Changing destinations preserves the party; the Min preset matches each route.
Workers remain capped at 37. Active trips, trails, XP and supplies follow launch.
Desktop keeps the grid of all unlocked destinations.

`python3 tests/mobile_explore_smoke.py` checks 320/390/844/1920px layouts,
unlock visibility, arrow/keyboard/touch navigation, scroll/jitter discrimination,
minimum presets, worker caps, danger, costs, launches, full slots and missing
workers. Browser route helpers navigate the carousel on mobile.


## Consistent action details

Action [i] panels order explanation, requirements and results consistently,
omitting empty sections. Related actions share a renderer: worker, scout and
emergency eggs show Queue, Development and Larval care in that order. Base
development times come from the brood model; actual speed depends on care.
Laying costs stay beside the button; larval costs are identified separately.
`python3 tests/action_details_smoke.py` checks matching egg fields and results
on phone and desktop after `sbt fastOptJS`.

Egg action details omit redundant result counts; the action name and batch badge
identify the order. Manual Gather Sugar remains necessary for the zero-worker opening.

Nest and Tasks have independent mobile action amounts, initially ×1, retained
while switching tabs. Desktop Ctrl/Shift shortcuts are unchanged.
`python3 tests/action_amount_smoke.py` verifies separate choices and actual
egg orders/worker assignments on phone and desktop.

Scout eggs also honor the Nest action amount and desktop modifiers. Their
batch badge and total laying costs reflect the affordable batch, limited by
sugar, protein and future housing. Ordered scouts share the normal laying queue
and consume supplies only as laid.

Chamber [i] panels show numeric bonuses without descriptive prose. Capacity,
place and slot labels are consistent, including Nursery brood/nurse places and
the Queen's next laying-interval reduction. These previews include derived
effects without changing saved capacities. Exploration [i] shows outcome
chances without repeating the duration/reward line visible above its button.
`python3 tests/chamber_details_smoke.py` checks every chamber and expedition
help at phone/desktop widths.

Exploration destinations with unfinished discoveries show a **Discovery** badge.
It disappears when the destination's discoveries are complete. Locked routes and discovery identities stay hidden.
`python3 tests/discovery_badge_smoke.py` checks live completion, carousel selection stability,
save/reload and early-route hiding on phone and desktop.

Outpost action help stays open during live simulation updates. Costs, production
and level labels continue updating in place. `python3 tests/outpost_info_smoke.py`
checks establishment/expansion panels, live costs, level transitions and closing
help manually at phone/desktop widths.

General help states concise base/current facts without listing hypothetical
upgraded variants. Small-worker help is “Small founding workers: 50% task output
and upkeep; live 30–40 simulated minutes.”
`python3 tests/lineage_ui_smoke.py` checks founding labels/help, concise copy,
Lineage visibility and inherited access. Brood/prestige checks cover the live
caste transition, founding and DNA purchases from the new tab.

Research help leads with concrete effects and keeps important scope/tradeoffs.
Outpost construction uses numeric bonus lists; promotion help uses compact
facts, and XP help omits repeated next-level forecasts. Royal brood help stays
open across unrelated chamber/research updates.
`python3 tests/concise_ui_smoke.py` checks those details and live panel stability
at 320/390/1920px; outpost progression checks cover upgraded rates and staffing.

After founding, remembered protein stays visible but does not reveal Nursery
Chamber during the small-worker phase. Normal Nursery access returns with
standard-worker progression; already-built Nurseries remain visible.

## Balance experiments

Run `python3 scripts/balance.py --out /tmp/antidle-balance` for seeded real-engine
strategy comparisons, a portable interactive report and importable milestone saves.
See [Balance lab](tools/balance/README.md) for setup, assumptions and how to combine
simulation with short normal-speed playtests.
