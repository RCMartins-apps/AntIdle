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
