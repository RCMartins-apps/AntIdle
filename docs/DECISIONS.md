# Design decisions

These are lasting decisions from the owner, initially captured on 2026-11-05 and
updated as the direction develops. Consult
source, tests and README for exact current balance; this file preserves intent.
Edit an entry when the owner changes it rather than accumulating contradictions.

## D1: Ant biology drives the design — approved direction

Use believable colony needs and adaptations to create gameplay choices. Balance
and clarity take precedence over exhaustive realism. Uplifting provides room
for the colony to grow beyond natural ant behavior. See [VISION.md](VISION.md).

## D2: Understandings are inherited prestige unlocks — first loop implemented

Reserve **Understandings** for the most powerful permanent unlocks, inherited
through queens across prestiges. A young queen transfers the preceding
generation's Understandings into a new colony. Knowledge remains a separate
research resource. DNA is the prestige currency used to purchase inherited
Understandings (owner decision, 2026-11-07). D13 records the first implemented
reward and reset matrix; later rewards and challenges remain undecided. Do not
turn illustrative Understanding names or bonuses from chat into rules.
Existing descriptions calling research "permanent" mean saved ownership today,
not an agreed promise of persistence across future prestige resets.

On 2026-11-07 the owner clarified two forms of prestige:
- An upgrade form, where the new colony has starting unlocks and/or permanent
  production or capacity boosts. Numeric bonuses are explicitly valid; do not
  impose a rule that every Understanding must change a mechanic.
- A challenge form, where a new colony starts in a less ideal location and
  completing its challenge unlocks something new. Exact challenges, completion
  rules and rewards are not yet selected.

The owner also endorsed raising daughter queens as a development direction,
including opportunities for new chambers and job types. Royal Nursery and resin
construction are subsequently approved in D11. D12 records the next implemented
step: Royal Nurses and daughter rearing, with current balance documented in
README. The first Understanding and founding loop follow in D13.
Challenge rewards' relationship to Understandings and the interaction between
normal and challenge foundings remain open design questions.

## D3: Resources need a purpose — implemented foundation, ongoing direction

Population pressure should make improved resource methods desirable. Basic sugar
collection has diminishing returns; avoid an exploitable minimum yield that
lets arbitrarily many collectors support arbitrary population. Displayed rates
must match actual production. Exploration should offer useful alternatives.

Use **sugar** for the resource previously called food. Keep **Food Storage
Chamber** as its name: it can support other food types. Protein Storage research
makes each Food Storage level add 100 protein capacity. Knowledge replaced
colony points, comes from exploration and has no storage cap. Pre-exploration
progression cannot require Knowledge. Tunneling space stays capped; each Nest
level adds 500 to that cap.

## D4: Reveal progression gradually and show costs — implemented UI rules

- Hide advanced exploration actions until unlocked; discoveries have their own tab.
  Once an action is visible, omit its already-satisfied unlock-level requirement
  from details, including expedition supplies. Retain actionable resource,
  staffing and slot requirements.
- Show action costs beside the action, not only inside Details. Keep lengthy
  explanations, forecasts and XP descriptions tucked away. Mobile action cards
  use a compact info icon beside the action to expand details, with a 44px touch
  target and native keyboard support. Closed details must not add a separate row;
  the info control stays usable when the action is disabled.
- Resource totals use compact, readable numbers without invisible suffix padding;
  negative income is red. Upgrades use the available grid width and distinguish
  future-research gaps from finishing the current catalog without spoiling locks.
- Show unmet expedition party minimums beside disabled actions and fleet capacity
  above destinations, so a hidden detail is not needed to explain a blocked trip.
- Show worker availability compactly: colored `available` followed by standard
  `/required workers`. Use green when sufficient, red when insufficient.
- Make impossible requirements, including costs above storage maximum, visibly
  red. Queue `Never` is red. Full resource amounts are blue and bold, and
  displayed collection is zero when capped.
- Once a resource is unlocked, its row never disappears, even at zero or after reload.
- Keep mobile controls usable. Expedition staffing uses a numeric stepper and
  caps at the useful 37-worker reward limit; do not restore the awkward slider.
- Avoid repetitive maturation announcements. Use concise or grouped feedback.

## D5: Worker availability includes the selected default task — implemented

After Default Workers research, idle-worker requirements may borrow from the
selected default-task pool. Do not silently steal other assigned jobs or ants
away on expeditions/retrieval. Returning and matured workers rejoin the pool.
Carcass retrieval uses workers and carcass storage reservations, not an
expedition slot. Visible requirements should explain the actual shortage.

## D6: Adults have finite, saved lifespans — implemented

Aging uses simulated expiry ticks with randomized birth-cohort jitter; avoid
one record per ant and synchronized deaths for every batch. Offline time is
banked for player-selected accelerated simulation, not separately simulated
offline production. Aging follows the same simulated ticks as other mechanics.
Away ants keep aging but cannot die in transit; overdue ants die on return.
Repeated expeditions must not make the same ants immortal.

Repletes are promoted workers, not a new egg type. Each Replete Chamber level
houses one; each living replete adds 100 water capacity. Promotion always uses
the oldest living home-worker age cohort and grants a fresh randomized long
lifespan from promotion, not twice its remaining life. Current range is
90–120 simulated minutes, or 108–144 with Colony Nutrition.
Deaths reduce water capacity but never destroy stored water. Above-cap water
remains spendable; collection waits for free capacity. Use living storage
rather than a cistern. Aphid Chamber levels similarly limit tender slots, and
tending has a small protein cost.

## D7: Loading restores valid visibility — implemented

Reconcile upgrade visibility across the whole catalog on load and during play;
do not depend solely on one-time unlock events. Preserve earned resource
visibility and enforce worker/chamber limits after restoration. Persist and
update statistics so later milestones can use meaningful gathered-resource
and completed-expedition totals; no milestone reward scheme is agreed yet.

## D8: Save compatibility during the prototype — current policy

The owner explicitly allowed breaking saves during this early prototype rather
than adding migration complexity. Do not assume backward compatibility is
required for every model change. Version incompatible formats deliberately and
report the impact. Revisit this policy if the project gains real players or the
owner changes it; do not treat it as a permanent production policy.

## D9: Branch, commit and push development work by default — standing authorization

The owner wants progress preserved remotely without needing to request a push
each time. Each distinct development task or lasting design decision starts on
a descriptive branch from current `origin/main`; related follow-ups stay on that
branch. Record lasting discussion decisions even when there is no code change.
After appropriate checks, commit and push before handing off changed work.
Unfinished work must also be saved as clearly labeled checkpoint commits on the
same branch, with remaining work, failures and missing checks in the handoff.
Push useful intermediate checkpoints during long tasks when practical; a backup
does not imply completion, passing tests or readiness to merge.

This includes documentation-only branches and does not require a PR. PR creation,
merging and deployment remain separate user requests. Ordinary branch pushes are
authorized; destructive Git operations and direct pushes to `main` are not.
See [AGENTS.md](../AGENTS.md#branch-and-push-by-default) for the operational rules.

## D10: Dry Clearing outpost extends the current colony — approved scope

The owner approved implementing the Dry Clearing outpost with a water
requirement and an ongoing benefit. It is an extension of the current colony;
queen-founded prestige and Understandings remain separate future work.

The implemented first version provides extra shared housing and a capped Seed
Forager task that consumes water to gather protein. Dry or full storage pauses
foraging without removing housing, killing ants or discarding stored resources.
Exact founding costs, rates and staffing limits are current balance choices,
documented in README and defined in `OutpostProgression.scala`.

The subsequent delegated content update extends this outpost to three levels,
with a capped workforce and water-dependent research for sheltered routes and
seed processing. These are current-colony expansions and research, not inherited
Understandings. Existing outposts begin at level 1 when the saved extension count
is absent; loading preserves paid housing rather than granting it again.

## D11: Royal Nursery foundation and resin — approved scope, 2026-11-07

The owner approved starting with material collection and the Royal Nursery
chamber, leaving nurse jobs and daughter-queen rearing for the next step. They
selected resin collected from woodland sources as the first building material.
Use it to make advanced construction require a distinct supply and hauling path;
existing early chambers keep their costs. Exact quantities and gates are current
implementation balance choices rather than settled prestige rules.

The nursery prepares one royal brood slot. Building it does not rear a queen,
reset the colony, grant an Understanding or choose challenge rules. Those remain
separate design/implementation steps. Costs, storage shortages and queue waits
must handle resin explicitly, and its resource row stays visible after unlock.

## D12: Raise the daughter queen before adding founding — 2026-11-07

After merging the nursery foundation, the owner asked to start the next work
toward prestige. This step implements ordinary Royal Nurse workers and royal
egg/larva/pupa rearing in the existing nursery. At this stage, founding and
Understandings were separate work; D13 now records their first implementation.
Challenge rules remain unchosen.

Current implementation balance: up to five nurses; ten simulated minutes with
full staffing; water throughout and extra sugar/protein during the larval stage.
Shortages, missing nurses or manual pause preserve development without charging
unperformed care. Nurses retain normal upkeep, aging and default-task behavior.
A royal egg reserves one common housing slot; its mature daughter replaces that
reservation and occupies the royal slot. Daughters have normal sugar upkeep and
can starve after other home ants, but do not use worker aging cohorts. A mature
daughter grants no permanent bonus and does not trigger a reset. These quantities
are balance choices, not a finalized prestige contract.

## D13: DNA funds Understandings — approved direction, 2026-11-07

The owner approved starting the prestige system after merging royal brood
rearing and selected DNA as the prestige currency for purchasing Understandings.
DNA is distinct from colony Knowledge and ordinary resources.

The owner refined the direction: the first roughly three or four prestiges
should be mostly linear, introducing new mechanics and meaningful colony
management gradually. Later, players choose shorter or longer runs for different
DNA earnings. The remaining introductory mechanics, exact count and later earning formula
are not chosen. This replaces the earlier proposed immediate three-way shop;
only Shared Paths is offered by the first implementation below.

The owner approved implementing the first prestige with Shared Paths and an
automatic +10% sugar-production bonus per founding, stacked additively. Apply
the bonus to collectors, aphids and trail production; keep consumption, aging,
prices, manual sugar gathering and expedition rewards unchanged. DNA purchases
Understandings; the production bonus is automatic and costs no DNA.

Current first implementation balance: founding with a mature daughter awards
1 DNA; Shared Paths costs 1 DNA once. The reset preview explicitly lists colony
resources, ants/brood, chambers, research, exploration/discoveries, trails, queues
and assignments as reset. Retain DNA, Understandings, lifetime statistics,
resource visibility, settings and banked time. Exact future introductory rewards
and scaling DNA remain future work. This implements only the first mechanic.

Shared Paths reveals finite nectar sources from Nectar Meadow returns after
exploration is unlocked. One trail at a time; marking costs 50 sugar and
reserves one scout. Up to ten ordinary workers gather from 1,200 sugar supply
at base 0.8 sugar/s each, including the inherited production bonus in speed.
Full storage pauses collection; recall/depletion returns workers to their
default pool and frees the scout. Trail workers and guides age and consume
normal upkeep. No strength meter or recurring maintenance clicking is added.
These quantities are implementation balance choices, not future content promises.

## D14: Voluntary danger can cause substantial losses — first raid implemented

The owner wants dangerous actions where ants can die to create meaningful
population pressure before adding further brood-care Understandings. Large
losses are acceptable when the risk is clear before committing; the player
judges whether rewards justify casualties. Do not impose the assistant's
earlier suggested 0–2-worker cap as a requirement. Queen's Routine already
provides early automatic worker replacement; do not invent another prerequisite
automation system to justify danger.

The owner approved implementing the optional raid, then specified that oldest
ants should be sent first but deaths should be random within the expedition
party. Reuse oldest-first home-worker cohort selection; sample casualties
uniformly by individual within the departing party. Survivors retain expiry
ticks. Store the seeded outcome at departure so reloads cannot reroll danger;
remove casualty cohorts before resolving overdue survivors' aging on return.

Current implementation balance: Rival Food Cache, exploration level five,
10–37 workers and at least one scout, 120 seconds before extra-scout speed,
30 sugar per worker. Ten workers lose 2–4; 37 lose 4–10 (guaranteed combat
losses, uniform over integer range). Protein is rolled uniformly from 200–350
per ten workers and scales linearly with party size. Free storage caps retained
rewards without reducing losses. Extra scouts/supplies do not change these
combat or protein ranges; normal XP/Knowledge/speed rules still apply. Scouts
have no combat losses but can die from overdue aging. Ranges, certainty and
storage room are visible beside the action, with an additional warning if
oldest selected cohorts expire before return. Existing destinations remain
safe from combat. Raid deaths have separate run/lifetime statistics. These
quantities are current balance choices.

Expanded brood allocation and ongoing outpost supply costs remain unadopted
suggestions, not this implementation.

## D15: Optional territorial contests before more brood progression — approved

The owner chose territorial contests as the next feature: holding an optional
contested location produces resources at recurring population cost, with clear
risk and player-controlled staffing/withdrawal. Temporary exposed food sources
and worker-to-soldier specialization were liked but explicitly deferred. Keep
those ideas for later; do not implement them in this step.

First implementation balance: the first Rival Food Cache return discovers
Contested Foraging Ground, opening a separate Territory tab. A fixed target
of 10–37 workers holds it; entry costs 20 sugar per worker, with no scout or
expedition slot. Harvest accumulates at 0.3 protein/s per holder and is delivered
after each 60-second contest if workers survive. Combat uses the raid's
size-dependent casualty range (2–4 at ten, 4–10 at 37); smaller remaining crews
can be wiped out. Full storage does not stop combat or casualties.

Workers are recruited oldest-first from the available/default pool. Reserved
cohorts retain expiry ticks; aging resolves at contests or withdrawal, preventing
indefinite aging immunity. Combat selects individual holders randomly, with
saved seed/round/timer for reload-stable outcomes. Ordinary sugar upkeep and
starvation still apply. Optional automatic reinforcement is off initially; it
borrows only idle/default workers and respects other assigned jobs. Queen's
Routine provides the existing replacement pipeline.

Withdrawal safely returns surviving workers and their ages but abandons the
unfinished haul; no free resource income can be banked by withdrawing just
before every fight. A defeated crew loses the site and pending haul, without
automatic reclaim. Entry, potential haul rate, countdown, casualty range,
storage and aging warnings are visible. Exact numbers are current balance
choices, not a finalized economy. This is current-colony content, not a new
Understanding or a challenge-prestige implementation.

## D16: Brood pipeline and small founding workers — implementation approved

The owner approved gradual laying, whole-development brood capacity, larval
feeding and nurse throughput, with Queen's Routine using the same pipeline.
They approved a fast small-worker opening followed by an unlock switching
future eggs to standard workers. Existing adults and already-laid brood keep
their type. Small workers must perform tasks at exactly half standard output,
and have shorter lifespans with ample time to unlock standard workers.

Current implementation choices: small workers develop in about 15 seconds,
lay at a base 5-second interval, cost 5 sugar per egg, and live 30–40 simulated
minutes (36–48 with Colony Nutrition). They have 50% task output and upkeep.
Safe founding tasks are sugar, tunnelling, nearby scraps and brood nursing;
standard worker pools alone staff expeditions, territories and promotions.
Standard Worker Development becomes visible after five small workers and
costs 75 sugar plus 10 tunnelling space. It opens capped nearby scraps gathering
to supply protein before scouts/expedition resources are required.

Standard workers take about two simulated minutes without nurses, with 10
sugar per egg and 20 sugar/2 protein in larval care. Scouts use the same brood
space/care system. Queen laying is rate-limited; manual batch actions queue
orders instead of creating instant eggs, and costs are charged when laid.
All stages occupy brood places, increased by Queen chambers and Nurseries.
Nurse throughput competes for workers; small nurses contribute half standard
care. Existing Brood Care speeds care; Queen chamber levels improve laying.
These quantities are balance choices under test, not final economy promises.

Preserve existing standard adults and legacy already-laid brood on old saves;
fresh colonies and prestige start with the founding phase. Opening and mature
replacement throughput must both be tested. Temporary food opportunities and
soldiers remain deferred. Prior example opening timings were not binding rules.

## D17: A collapsed colony must remain recoverable — approved

The founding queen survives starvation and Gather Sugar stays available. After
standard-worker research, losing all workers opens a manual emergency small
worker egg for 5 sugar, with no protein care cost. One surviving worker (including
workers away on expeditions or territory duty), existing small brood or a legacy
self-sufficient worker egg prevents another emergency egg. Research, existing
brood, orders and other progression are retained.

To cover larvae stalled for protein and full brood/housing, the queen can tend
one emergency brood outside the ordinary limits. It counts in population, so
ordinary laying remains blocked at capacity; the temporary extra place is not
available to other eggs. Recovery brood receives priority care and becomes a
normal small sugar collector, with the usual half output/upkeep and lifespan.

With Queen's Routine unlocked, enabled and a positive worker target, recovery
uses 5 stored sugar only above the configured reserve. Otherwise the queen
spends 25 simulated seconds gathering protected provisions for that one egg.
These cannot fund ordinary storage, upgrades, adult upkeep or repeated eggs
while a worker survives. Progress is saved; disabling automation or a worker
returning clears unfinished emergency foraging. Existing brood/orders do not
prevent this safety net. This is opt-in automatic recovery, not free colony-wide
production. A compact red starvation warning shows the current sugar deficit.


## D18: Aging should preserve job staffing after Queen's Routine — approved

The owner reported nurses, scraps gatherers and tunnelers being removed in
reverse UI order as home age cohorts expired. Aging has shared caste cohorts,
not job-specific ages; job list order must not drain specialized staffing while
the default pool could replace it.

After Queen's Routine is purchased, aging consumes idle/default workers first.
Any remaining vacancies are recorded and refilled from the idle/default pool as
workers become available, including newly matured/returning workers. This job
maintenance belongs to the unlock; the automatic-egg checkbox only controls
laying. With no selected default, idle workers are the replacement pool.
Small-worker job losses can be filled by standard workers; spare small sugar
collectors can fill supported jobs. Respect shared job caps and retain all
survivors' expiry ticks. Do not create ants or borrow occupied travel/territory
crews. Combat/starvation remain separate and do not create aging vacancies.

Only aging vacancies are remembered, not arbitrary historical job targets.
Save pending requests; show them compactly with a cancellation control. Explicit
job edits cancel that job's pending request, changing a job to the default drops
its quota, and recalling/depleting a nectar trail cancels its request. A fully
staffed colony keeps the same UI. Existing saves default to no pending requests.


## D19: Visible resource full/empty estimates — approved

The owner wants time to capacity or depletion visible below resource names,
especially for food, using the existing two-line mobile resource rows. Show a
compact approximate countdown at the current net resource rate, red when
emptying, and Full when already at capacity. Hide stationary countdowns and
full-capacity forecasts for unlimited resources. Estimates use simulated time
and round up fractional ticks; they are not promises about future purchases,
expedition deliveries or staffing changes. Use the same estimate in the income
tooltip. On mobile put the rate below the numeric amount/capacity, alongside the
name/estimate column, to avoid an extra full-width line.


## D20: Scout eggs must queue while the queen is busy — approved

The owner reported scout raising being rejected during the queen's laying
cooldown instead of accepting an order. Scout orders now share manual FIFO
ordering with worker batches, including while brood places are full. Keep
Nursery, standard-worker, supply and future-housing checks when accepting an
order; cooldown/current brood space gate actual laying. A ready queen can still
lay an unqueued scout immediately. Charge sugar/protein and increment egg
statistics when laid, never when merely queued. If supplies run out, retain the
order and show the missing resource.

Reserve housing for queued scouts across worker/scout/royal actions. Automatic
workers wait for manual orders; scouts do not satisfy worker population targets.
Cancellation clears unlaid orders of both types, preserving existing brood.
Save mixed ordering without breaking existing worker-only queues. Store worker
orders ahead of each scout rather than expanding worker batches into individual
records. Show queued caste counts, visible scout costs and concise details.


## D21: Keep action eligibility and visible blocking feedback consistent — polish pass

The owner requested further fixes like the scout queue inconsistency. This pass
keeps reserved-housing counts shared between worker/scout/royal actions and their
UI, reports actual housing blockage in the laying queue, and explains full or
duplicate construction queues only while a button is blocked. Enqueue messages
and eligibility come from the same check. No new costs, content, automation or
save fields are introduced. An idle laying cooldown describes when the queen
is ready, not an egg that has not been ordered.


## D22: Much less default UI text, with explicit information controls — approved

The owner identified phrases such as nurses providing half care as redundant
clutter and asked for a broad reduction in text. Remove repeated explanations;
put genuinely useful mechanics behind [i] buttons on phone and desktop. Keep
costs, rates, counts, progress and relevant blocking/danger warnings visible.
Do not turn hidden hover tutorials into visible paragraphs with future features.

This pass covers tasks and small-worker jobs, brood/aging, Queen's Routine,
default workers, royal care, territorial instructions, exploration XP, carcass
storage and discovery descriptions. Native keyboard-operable disclosures expand
in flow, with 44px information targets. Action details are available explicitly
on desktop as well as mobile; action/task prose no longer appears on hover.
Material care costs and danger disclosures remain direct, not hidden in help.
Mechanics and saves are unchanged. Existing smoke checks should open [i] when
asserting explanatory content rather than depending on old always-visible prose.


## D23: Keep mobile expedition selection and launch together — implemented

The owner reported excessive scrolling between party controls and expedition
actions. On phones, use an unlocked-destination picker and show only its action
card directly below workers/scouts. Preserve visible prices, risk and shortages;
keep locked routes absent. Provide a destination-specific minimum-worker preset
without silently changing the chosen party. Keep the 37-worker cap and stepper.

Place active expeditions and secondary trail/XP/supply panels after launch.
Desktop retains all unlocked cards for comparison. This changes layout only,
not expedition mechanics, automation or saves.


## D24: Action descriptions follow shared schemas — approved

The owner reported inconsistent worker/scout egg help. Related actions must
show equivalent information in the same order, using shared rendering instead
of independently written prose. Egg details use Queue, Development and Larval
care; times derive from Developing.totalCare and are explicitly base times
because nurses, research, competition and supply affect actual development.
Worker caste changes update the displayed values. Emergency exceptions occupy
the same fields. Laying costs remain next to the button, with later larval
resource costs identified separately.

Common action help orders explanation, Requires, then Result, omitting empty
sections. Egg actions omit the redundant result count: their names identify the egg. Keep default screens concise and action-family schemas in code; future
actions must follow their family's structure. No gameplay or save changes.


### D24 follow-up: redundant egg results and manual sugar

Remove the extra +1 egg result from worker/scout/emergency actions; action
names and batch badges identify the order. Keep Gather Sugar for now: fresh
colonies start with no sugar or workers, and emergency recovery requires standard
workers. Replacing clicking with slow queen foraging is a suggestion only, not
an approved or implemented opening change.


### D24 follow-up: action amounts belong to their screen

Nest brood batches and Tasks assignments use independent mobile amounts, both
initially ×1. Tab switches retain each choice without changing the other.
Desktop Ctrl/Shift shortcuts are unchanged. These are session UI values, not
saved gameplay settings.


### D24 follow-up: reserve touch-control height

Task rows must reserve enough height for their 44px info control even without
assignment buttons or an output rate. Reserve a 52px row and align the info
control's 4px top inset with the assignment-button margin. Expanded help stays
in flow. Geometry checks cover passive and assignable jobs, adjacent controls,
phone bounds and opening/closing each task disclosure.


### D24 follow-up: scout eggs honor the brood batch amount

Scout ordering uses Nest's amount and desktop modifiers, like workers. Clamp
the batch to available sugar, protein and future colony slots, displaying its
actual badge and total laying costs. Append the batch to shared FIFO, preserving
worker orders ahead of it. Only the first scout may lay immediately; remaining
orders wait for normal cooldown, brood space and supplies. Charge on laying.
No new save format or accelerated development.


## D25: Numeric chamber help and concise exploration outcomes — approved

The owner requested that chamber actions show only bonus lists, with consistent
names and numbers, instead of prose. Resource storage uses “sugar/protein/water/
tunneling space capacity”; population uses “colony slots”; brood uses “brood
places”; job/storage slots use consistent singular/plural labels. Show all
useful effects: Nursery +3 brood places/+5 Brood Nurse slots, and the Queen's
actual reduction to the next laying interval, alongside existing bonuses.
Derive preview-only effects from the model; do not add derived capacity to saved
base values or alter gameplay.

Exploration retains its visible duration/reward summary, cost and danger. Its
[i] shows Finds/outcome chances without repeating that summary or generic
“no carcass storage needed” prose. Keep raid distribution and combat-modifier
rules in help while keeping danger visible before departure.


## D26: Mark exploration destinations with discoveries remaining — approved

Show a compact Discovery badge on unlocked expedition cards while any mapped
discovery is unfinished. Also mark those destinations in the mobile picker so
players can choose without opening every card. Do not disclose discovery names,
rewards or locked destinations. Derive the indicator from the discovery catalog
and saved survey counts; update it after completion and on load. No extra save
field or gameplay rule.


## D27: Replace the mobile destination picker with a carousel — approved

The owner found the picker too many taps for comparing actions. Supersedes the
mobile selector in D23 and picker hints in D26. Keep one visible destination
card below shared party controls, with swipe navigation and previous/next buttons
immediately above the card. Show destination name and current/unlocked count;
wrap at the ends. Preserve party choices, Min presets, costs, outcomes, danger
and the card's Discovery badge. Locked destinations remain absent; desktop keeps
its comparison grid. Horizontal gestures navigate; vertical movement scrolls
normally. No engine or save changes.


### D21 follow-up: outpost disclosure stability

Outpost sections project only visibility, established state and expansion
availability before distinct/rendering. Tick-by-tick exploration updates must
not recreate their action controls and close help. Costs, eligibility, level,
capacity and production remain reactive inside the stable section.


## D28: Concise current facts, dedicated Lineage and caste-aware actions — approved

Use the owner's shorter copy verbatim: “Small founding workers: 50% task output
and upkeep; live 30–40 simulated minutes.” General help should not append obvious
upgrade-improves-this claims or potential upgraded number variants. Keep concrete
upgrade effects on their own upgrade actions and important mechanics behind [i].
Removed the equivalent Colony Nutrition qualifier from replete promotion help.

Queen's lineage is its own Lineage tab, with generation/DNA/production, daughter-
queen readiness, Understandings and founding review/reset disclosures. Reveal
the tab when the Royal Nursery exists, a daughter queen is ready or a previous
founding exists; keep it across founding resets. Royal rearing stays in Nest.

The worker action says Raise Small Worker Egg during founding, then Raise Worker
Egg after Standard Worker Development. Its help label/title change with it.
Names reflect newly raised caste, without changing existing adults or gameplay.


### D28 follow-up: concise effect-first research and action facts

Implemented a further UI pass requested by the owner. Research descriptions
lead with concrete percentage/capacity/slot effects rather than generic prose
or flavor; retain relevant tradeoffs, future-only scope and retroactive effects.
Outpost establishment/expansion use bonus lists (including the formerly omitted
5 Seed Forager slots); ongoing rates belong to the live job/outpost display.
Replete promotion uses short Worker/Lifespan/Upkeep/Role/Storage facts, with
upkeep drawn from the shared constant. XP help keeps current bonus and thresholds
without a redundant next-level forecast.

Royal brood follows the same structural projection rule as outposts: unrelated
construction or research updates must preserve open help and control identity.
No balance, costs, unlock rules or save-format changes.


## D29: Resource visibility must not bypass founding progression — Nursery fix

Protein's discovered flag intentionally survives prestige so the resource never
disappears. It must not reveal Nursery Chamber immediately in the new small-
worker colony. Nursery visibility now requires standard workers and known protein,
or an already-built Nursery. Standard Worker Development restores normal access;
existing chambers are retained. No resource history is cleared and no new save
field is needed. Regression coverage includes actual founding/reload and the
subsequent standard-worker research transition.


## D30: Skip expensive game jobs for documentation-only changes — approved

The owner requested avoiding game tests and deploys when only documentation
changes. Shared detection treats `docs/**` and root Markdown as documentation;
source/build/test/workflow files and mixed changes still run validation. Use
job-level skips after a lightweight classifier so required check statuses finish
instead of remaining pending. PR comparison uses the merge base; pushes compare
the entire before/after range. Include both sides of renames. Unknown/empty
comparisons conservatively validate; manual test dispatch always validates.
Preserve main and same-repository debug* deployment restrictions.


## D31: Record waste management; introduction timing remains undecided

On 2026-11-09 the owner requested preserving the waste/colony-maintenance idea
without choosing when it should enter the game, then authorized a PR and merge
for that design record. See [IDEAS.md](IDEAS.md). Waste carriers, refuse chambers
funded by tunnelling space and capped overflow effects remain proposals, not
approved implementation requirements. Do not infer a Nursery-stage unlock or
a prestige placement from the earlier discussion.


## D32: Add a focused production browser CI gate — implemented improvement

For the owner's request to work on improvements needing no new gameplay design,
add a small phone/desktop regression gate to non-doc PR validation. It serves the
production bundle at 390/1280px and covers founding labels/help, stable outpost
details, task-info geometry, navigation/launch and prestige/reset resource gates.
Use existing save/clock helpers; upload screenshots on failure. Keep larger
feature-specific suites available separately. Docs-only PRs still skip the whole
game test job, including browser installation and checks. No gameplay changes.

## D33: Measure pacing with real-engine simulations — approved

The owner requested a balance tool on 2026-11-09. Reuse real game ticks and costs,
seed randomness, make policy assumptions explicit, report milestone pacing and
waiting/stock/labor evidence, and export playable saves for short human checks.
Compare controlled baselines without treating every timing change as failure.
Automated strategies cannot establish fun or optimal play; policy waits are not
proof of global forced waits. Do not change game balance merely to satisfy a bot.
The headless Scala.js subproject shares pure unlock rules with the browser;
reports and runners stay out of the deployed game. See tools/balance/README.md.

## D34: Broad enjoyment and maintainable balance definitions — agreed direction

On 2026-11-09 the owner set an aspiration that at least 80% of players enjoy the
game and emphasized making balance easy to change personally, especially when
upgrades/mechanics unlock. This is not an achieved metric or an automated test
threshold. Player sampling, questions and stages still need definition before
claiming a satisfaction percentage. Simulation complements real player feedback.

When implementing or refactoring balance, prefer discoverable named values and
shared prerequisite predicates; keep eligibility, visibility, costs and displayed
numbers consistent. Distinguish revealing an upgrade from being able to afford
or purchase it. Expose the meaningful tuning points without building a generic
configuration framework prematurely. The balance lab is implemented; a wider
consolidation of existing gameplay definitions remains future work, not completed
by this documentation update. No unlock or economy values change here.
