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
there is no research to buy. Explore shows a hex map and selected-site requirements, reserves and mission availability.
Resource limits and game rules are shared across both layouts. Game saves remain
in the browser's local storage.

The responsive styles live in `index.html` so the existing GitHub Pages deployment
includes them without an additional asset-copy step.

## Browser regression checks

The maintained production checks cover phone/desktop controls and the version-7 map:

```sh
sbt fullOptJS test balance/test
python3 -m pip install -r tests/requirements-browser.txt
python3 -m playwright install chromium
python3 tests/ci_browser_smoke.py
ANTIDLE_PRODUCTION=1 python3 tests/map_browser_smoke.py
```

The scripts serve the repository themselves, use isolated browser contexts and
leave your own saves untouched. `CHROMIUM_PATH` can select a system browser.
Inherited HTTPS proxies are supported, with localhost bypassed. Browser libraries are bundled locally, so the published release can cache
every runtime dependency for offline play.

For fast iteration, run `sbt fastOptJS` then `python3 tests/map_browser_smoke.py`.
See [tests/README.md](tests/README.md) before using older feature-specific browser
scripts: XP/carousel fixtures are historical and no longer supported against v7.

## Chamber bonuses

Define chamber effects in `game/ChamberBonuses.scala` (`forChamber`). Action details,
completed upgrades, and capacity changes use this shared source. Balance values remain in `game/Constants.scala`. Changing an existing
capacity bonus or assigning it to another chamber does not require changes to
`Actions.scala` or `TickUpdater.scala`. Saved capacities stay additive.

## Handcrafted exploration map

Explore starts at the nest, surrounded by six hidden hexagons. Scouts reveal
neighbors in a fixed order and study each site for diminishing, finite Knowledge.
Workers collect finite deposits from studied sites. Exhausted hexagons remain
visible as history; there is no exploration XP or level-based reward inflation.

See [Exploration map](docs/EXPLORATION.md) for scout roles, fixed budgets,
discoveries, deposit conservation, renewable maintenance and the first-ring design.
This redesign uses **save version 7** and intentionally rejects older saves.

## Colony capacity

Workers, scouts, and growing brood share one ant capacity, including ants away on
expeditions. Nest, Queen, and Nursery upgrades increase that shared limit. The
Nursery unlocks scout eggs without imposing a separate scout limit. Brood capacity
limits eggs, larvae and pupae together; pending worker/scout orders reserve
future colony housing. Save format 6 stores this as `maxAnts`; older save formats are no longer supported.

## Exploration missions

Scout-only **Explore Surroundings** reveals one neighboring hexagon. **Study Site**
extracts Knowledge and makes discoveries; crews of one to three scouts trade
supplies/upkeep for faster missions. Workers cannot perform these actions.
Worker collection never grants Knowledge or advances studies. Scout studies and
worker hauls share mission slots; guides for optional raids and Shared Paths are
reserved separately from available scouts.

Every study and deposit is finite. Supplies/crew size improve haul capacity, not
total resources or available Knowledge. Costs, crew requirements, duration,
remaining reserves and combat risks are visible at the selected site. Use the
same map on phone and desktop; no carousel or list of unrelated destinations.

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

Knowledge is what scouts learn from unfamiliar sites. The first ring has a fixed
budget, claimed in three declining survey rewards per site. It has no storage cap
and cannot be farmed from worker trips. The budget exceeds all current research,
supplies and outpost-extension Knowledge costs, so optional research spending does
not block founding. Sugar-only founding research still precedes exploration.

Resource statistics count retained gains, not spending or discarded overflow.
Tunnelling capacity remains limited and grows with Nest chambers. Permanent
resource visibility is separate from current-colony unlocks and survives founding.

## Running build version

Settings and the page footer show an automatic short Git commit ID for the
running bundle, including offline. Local uncommitted builds append `-dev`.
If **Update ready** appears, save and close all game windows to apply it on the
next launch. See [offline updates](docs/OFFLINE.md) for build identity and caching.

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

**Expedition Coordination** becomes available after two hexagons are revealed for 1,000
sugar and 100 Knowledge. It permanently adds one expedition slot on top of the
fourth-reveal mission slot, allowing up to three expeditions with both unlocks.
Costs, availability, capacity indicators, and the engine use the same slot limit.
The upgrade is saved and checked by the shared visibility rules.

Run `python3 tests/foraging_smoke.py` after `sbt fastOptJS` to check live rates,
actual tick income, upgrade effects, population pressure, slot purchases and
save reloads on phone and desktop. `ForagingSuite` also tests the sustainable
population boundaries, assignment cycling, and engine slot limits.

**Established Trails** unlocks after the first hexagon is revealed for 600 sugar and 60
Knowledge. It reduces the marginal collector penalty by 20% (0.005 to 0.004
sugar/s), expanding productive collectors from 80 to 100. Base output reaches
20.2 sugar/s, enough for 101 workers before Foraging Techniques bonuses. Its
effect stacks with all three techniques and updates live rates and predictions.
Knowledge-priced upgrades stay hidden until exploration is unlocked, including
unpurchased upgrades revealed prematurely in an existing save.

## Colony development

Reinforced Mandibles requires two revealed sites and increases tunnelling output
by 50%. Brood Care requires three revealed sites and a Nursery, increasing ordinary
brood-care throughput by 25% when supplied. Their prices and help are in the shared
upgrade catalog; actual effects remain in ColonyDevelopment/BroodProgression.

The Nectar Meadow offers finite sugar collection after scout study. Base capacity
is 300 sugar, scaled by workers and supplies; collection has no Knowledge reward.
A five-worker crew pays 50 sugar and can bring back 600 when room/supply permit.

## Exploration discoveries

Scout surveys make discoveries at fixed thresholds: Wild Aphids after three
Woodland studies; Seed Cache after two Meadow studies; Dry Trail after two Log
studies; Dew-covered Plants and Resin Seep after three studies of their respective
sites. Observing the Rival Cache reveals contested ground without requiring combat.

Discovery unlocks are deterministic. They enable aphid research, seeds, water,
repletes, outposts and resin/royal-nursery progression. Water discovery also opens
Dew Gatherers (five places), providing renewable water after the deposit runs out.
The Discoveries tab retains learned information independently of site exhaustion.
Aphid Chambers house ten aphids per level, with one tender per ten animals. The
first chamber receives ten; later groups require a safe relocation mission from
Woodland Edge. Per-tender production and protein consumption are unchanged. See
[aphid husbandry](docs/APHIDS.md) for costs, reservations and save migration.

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

**Queen's Routine** appears after two hexagons are revealed with a Nursery. It costs
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

**Construction Planning** unlocks after the first hexagon is revealed for 500 sugar and 50
Knowledge, increasing construction queue capacity from one to three. Planned
upgrades use the chamber level after earlier plans of the same type. Cancelling
an entry reprices later entries, and completing construction charges the proper
next-level cost. Loading repairs stale queued prices. Queued upgrades show their
target level and resource costs; select the title or press Enter/Space to cancel.
A full queue disables further chamber plans. Only the first entry shows a wait
estimate; later entries say Queued. Protein shortages now correctly show Never
until supplies arrive, and estimates round up rather than showing zero too soon.

**Colony Nutrition** unlocks after three hexagons are revealed once aging is active. It costs
2,500 sugar, 60 protein and 200 Knowledge. New adults live 20% longer (54–72
simulated minutes), with the same seeded lifespan jitter. Existing expiry ticks
stay fixed, including while traveling. Aging details show the current lifespan
range. Research and construction planning survive saving/loading.

Run `sbt fastOptJS fullOptJS test`, then `python3 tests/management_smoke.py` for
phone/desktop checks of upgrade gates, queue pricing/cancellation, protein waits,
actual spending, nutrition and saved lifespans.

## Dry Clearing and resource statistics

The fourth hexagon is the Dry Clearing. Three scout studies discover Dew-covered
Plants, opening water collection, renewable Dew Gatherers, replete research and
the Dry Clearing Outpost. No random drop or mandatory raid gates this path.

Water collection draws from a finite deposit. Empty capacity blocks departures;
space for in-flight hauls is reserved. Storage that fills while a party travels
leaves unused cargo at its source instead of destroying essential resources.
Statistics count launched/completed missions, retained resource gains, births,
age/combat/starvation losses, replete promotions and other progression events.

## Scout and worker staffing

Scouts have exclusive reveal/study actions. They cannot fill normal worker jobs,
occupy housing, need larval feeding and age normally. Local scout missions continue
charging sugar upkeep; original expiry ticks apply on return. Scouts guiding a
nectar trail or another mission cannot be booked twice.

The map's scout-crew selector chooses 1–3 scouts. A selected site's collection
crew uses a bounded numeric field and touch buttons, using groups of five from 5–50 workers. Extra
workers raise haul capacity linearly; they do not create a larger deposit. Worker controls disappear on unstudied or exhausted sites.

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

After discovering Dew-covered Plants, **Nest →
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
stay hidden until their revealed-hexagon requirement is met. Each paid expansion adds
25 shared colony capacity and five Seed Forager slots without creating workers.
At level 3 the outpost contributes 100 housing and supports 15 foragers in total.

| Target outpost level | Revealed hexagons | Sugar | Protein | Knowledge | Tunneling space | Water |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 2 | 5 | 5,000 | 75 | 500 | 1,000 | 250 |
| 3 | 6 | 8,000 | 125 | 800 | 1,500 | 350 |

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

## Resin and the Royal Nursery

The Fallen Pine is the fifth hexagon. Three scout studies find Resin Seep, opening
resin collection and Royal Nursery Planning research (with an ordinary Nursery).
Workers then collect resin from a finite deposit. The safe first ring contains
more than the 80 resin required for Royal Nursery construction; full storage
cannot discard that essential supply permanently.

Royal Nursery Planning costs 5,000 sugar, 100 protein, 700 Knowledge and 150 water.
The chamber costs 3,000 sugar, 50 protein, 100 water, 80 resin and tunnelling space.
It opens one royal brood place and Royal Nurse jobs. A royal egg can be laid
without nurses, but development waits for staffed Royal Nurses. Up to five nurses
provide sustained care before founding; missing staff or supplies preserve progress.

## Introductory prestige: DNA and Understandings

The **Lineage** tab appears with a Royal Nursery, a mature daughter queen or a
previous founding. Its staged flow shows rewards/reset values, lets you choose
Understandings using banked plus earned DNA, then requires explicit confirmation.
Selections do not spend anything until **Found daughter colony** is confirmed
with a ready daughter queen:

- Reset colony resources, ants/brood, chambers, research, explored map,
  discoveries, trails, queues and worker assignments.
- Keep DNA, purchased Understandings, lifetime statistics, discovered resource
  visibility, settings and stored simulation time. The new colony begins with
  its queen and no workers, using the normal initial economy.
- Each founding awards **1 DNA** and automatically adds **+10% sugar production**,
  stacked additively (+10%, +20%, etc.). Sugar Collectors and Aphid Tenders receive this bonus. Sugar gathering clicks, expedition rewards, costs, upkeep,
  aging and consumption are unchanged. Per-colony statistics restart at zero.

The first Understanding, **Shared Paths**, costs **1 DNA** once. Ownership is
inherited across later foundings. This is the first introductory prestige
mechanic. **Beyond the Woodland** costs **1 DNA** at the second founding after
Shared Paths, opening three handcrafted outer sites with new colony research.
Later introductory Understandings, challenges and milestone-based DNA earnings
remain future content. Repeated founding currently awards the
same DNA amount and adds the same production bonus.

**Shared Paths** unlocks automatic collection trails on safe studied sites. Each
route can assign **5–50 workers in groups of five**, with **one guiding scout per
ten workers, rounded up**. Five or ten workers need one scout; fifteen need two.
Controls live beside the selected map action, including current output and supply
costs. Learning the Understanding after studying a site works immediately.

Trails gather continuously at **60% of the equivalent manual haul rate**, pay the
same proportional sugar supplies and retain normal worker/scout upkeep and aging.
They use no expedition slots. Full storage or missing sugar pauses collection.
Guide losses shrink crews to supported staffing and return excess workers;
depletion releases staff. Recall returns workers
to their default pool. The finite deposit and manual cargo/storage reservations
are shared, so automation cannot create or duplicate resources. Raids stay manual.

Manual collection is linear with crew size and uses the same **5–50, step-five**
controls. The former 37-worker cap and diminishing collection rewards are removed.
Old in-flight cargo remains intact; unused legacy nectar reserves return to the
Meadow deposit once on load, and their old workers are released. Saves remain
version 7. Trail assignments are stored in `exploration.collectionTrails`.

Trail Logistics, discovered in the outer map, raises the rate to 75% with +25%
supply use. Aphid Shelters doubles aphid housing per chamber level; Coordinated
Expeditions adds one mission slot. These research upgrades reset each colony;
the Understanding permanently retains access.

See [Prestige](docs/PRESTIGE.md) for exact costs and reset rules, and
[Collection trails](docs/COLLECTION-TRAILS.md) for tuning and resource/s math.
Engine tests cover guide reservations, finite resources, supply/storage pauses,
aging replacements, migration and coexistence with manual cargo. Maintained
map/browser checks cover mobile controls, reloads and live disclosure stability.

## Dangerous expeditions: Rival Food Cache

**Raid Rival Food Cache** unlocks after scouts study its revealed hexagon. Existing routes have
no combat losses. A raid takes 120 simulated seconds, requires 5–50 workers in groups of five
and one scout, and costs 30 sugar per worker. Extra scouts use the existing
10-sugar cost and travel-speed bonus; they do not reduce combat losses.

| Party | Guaranteed worker combat losses | Protein haul before storage/deposit caps |
| --- | ---: | ---: |
| 5 workers | 1–2 | 100–175 |
| 10 workers | 2–4 | 200–350 |
| 50 workers | 5–13 | 1,000–1,750 |

Intermediate parties use a gradually lower percentage loss range with integer
rounding. Counts, certainty, reward range and costs are shown directly beside
the action, not only in Details. Protein storage room is visible and red if it
cannot hold the maximum haul. Overflow stays at the finite cache; it does not reduce casualties. Scouts have no combat losses. The in-flight party retains its risk
notice. There is no additional confirmation dialog for an already disclosed risk.

The loss count is uniform over its displayed inclusive integer range. The
protein roll is uniform over integer values 200–350 per ten workers, then scaled
linearly with party size. Supplies and inherited sugar production do not change combat outcomes. Raids grant no Knowledge and draw from a finite cache; extra guides can still shorten travel.

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
an instant batch. Orders are limited by future housing space, even with no
sugar or protein available; costs are charged only as each egg is actually laid.
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
the local map, carcass storage and individual discoveries. Action **[i]** details now work
on desktop too. Native disclosures support touch, Enter/Space and visible focus,
with 44px targets and inline panels that fit narrow screens. Open discovery help
stays open during ordinary resource updates. Action/task prose
is no longer shown automatically on hover; resource-rate breakdown tooltips remain.

Larval/royal material costs, live shortages, imminent aging warnings and dangerous
expedition losses remain visible.
`python3 tests/compact_ui_smoke.py` checks collapsed defaults, information controls,
keyboard interaction, visible costs/rates/warnings and phone/desktop layout.


## Mobile exploration map

The same seven-hex layout is used on phone and desktop: nest plus six neighbors.
Unknown sites show fog; revealed sites select a single action panel; depleted
sites retain their names without missions. Selection survives ordinary ticks.
Costs and missing resources stay visible. All map controls support keyboard use.

Run `python3 tests/map_browser_smoke.py` after `fastOptJS`, or set
`ANTIDLE_PRODUCTION=1` after `fullOptJS`. It checks 390/1280px geometry, scout-only
missions, deterministic reveals, finite Knowledge, capacity blocks, depletion,
selection and reload. The production CI smoke also exercises the new map.

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
effects without changing saved capacities. The map info control explains scout study and finite collection. Mission summaries
show current duration/yield without an XP forecast.
`python3 tests/chamber_details_smoke.py` checks every chamber and expedition
help at phone/desktop widths.

The map labels sites with remaining study/collection work and retains exhausted
locations. Advanced collection actions stay hidden until their discoveries.
`tests/map_browser_smoke.py` checks the current map, selection, depletion and reload.
Older XP/carousel-specific browser scripts are historical and require new fixtures
before use; the production CI smoke and map smoke are the maintained exploration gates.

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
facts. Exploration rules are explained behind the map info control. Royal brood help stays
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

For code locations and examples of changing unlock timing, costs and exploration
requirements, see [Changing balance](docs/BALANCING.md).

## Offline play and installation

After deployment, open the game online once and wait for **Available offline**.
It can then reopen without internet, in a browser tab or through Chrome's Install
option. Complete updates wait until all game windows close; saved colonies remain
in local browser storage. See [Offline support](docs/OFFLINE.md) for usage, update
behavior, development packaging and tests.

### Explore controls

Choose a scout crew with the 1 / 2 / 3 buttons below the map. Collection crew
controls appear only for unlocked deposits at the selected site. Expedition
Expedition Supplies 1–3 are standard sequential upgrades, revealed at 1/3/5 mapped
hexagons after buying the previous tier. Prices remain 500/1,500/4,500 sugar and
20/80/200 Knowledge; each adds 20% of base haul capacity. Existing saved supplies
ranks convert to purchased upgrades on load. The collapsed Purchased upgrades
section at the end of Upgrades lists current-colony purchases with effects in [i].

### Compact Nest controls

The Brood section collapses as a whole. Starter-worker resource/task controls hide
when no small adults remain. On mobile the build queue appears only while Nest is
selected; desktop retains access beside other tabs. Royal egg laying needs its
normal resources and housing, while development waits for assigned Royal Nurses.
