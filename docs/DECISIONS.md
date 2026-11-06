# Design decisions

These are lasting decisions from the owner, captured on 2026-11-05. Consult
source, tests and README for exact current balance; this file preserves intent.
Edit an entry when the owner changes it rather than accumulating contradictions.

## D1: Ant biology drives the design — approved direction

Use believable colony needs and adaptations to create gameplay choices. Balance
and clarity take precedence over exhaustive realism. Uplifting provides room
for the colony to grow beyond natural ant behavior. See [VISION.md](VISION.md).

## D2: Understandings are inherited prestige unlocks — approved, not implemented

Reserve **Understandings** for the most powerful permanent unlocks, inherited
through queens across prestiges. A young queen transfers the preceding
generation's Understandings into a new colony. Knowledge remains a separate
research resource. Prestige rewards and the exact reset matrix are undecided;
do not turn illustrative Understanding names or bonuses from chat into rules.
Existing descriptions calling research "permanent" mean saved ownership today,
not an agreed promise of persistence across future prestige resets.

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
