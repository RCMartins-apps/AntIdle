# Game direction

Status: agreed direction, initially recorded on 2026-11-05 and refined for queens
and prestige on 2026-11-07. Future mechanics below are not implemented merely by
being recorded.

## Identity

Ant Idle is an incremental game about an ant colony gradually exceeding its
natural limits while remaining recognizably ants. Adrian Tchaikovsky's
*Children of Time* series is an inspiration for uplifting and inherited
understanding; the game's own setting and mechanics remain to be developed.

The owner wants some biological realism, balanced to feel like a game. Ant
biology should shape meaningful choices, resource flows and infrastructure.
Avoid a generic incremental economy with ant-themed names pasted onto it.
Realism is a design guide, not a requirement for exhaustive simulation.

Examples already supporting that identity include larvae/brood, scouting,
carcass retrieval, aphid husbandry, finite adult lifespans and living water
storage in repletes. Extend such connections rather than adding disconnected
currencies and arbitrary multipliers.

## Player enjoyment and balance iteration

The owner aims for at least 80% of players to enjoy the game, with broader
satisfaction preferred. This is a design aspiration, not a measured result or
something simulation can certify. Define the playtest question, player sample
and progression stage before interpreting a percentage; include struggling and
less active players rather than judging only successful optimized runs.

Balance must be easy for the owner to inspect and change when returning to a
full development environment. Unlock timing and prerequisites are a particular
priority, alongside costs, production, capacity and duration. Prefer clear,
shared definitions and small, testable changes over scattered special cases.
Use simulation to investigate pacing and human playtests to assess enjoyment.

## Progression and pressure

The colony should need better methods because supporting growth creates natural
constraints. Basic sugar collection has diminishing returns, giving exploration
and alternative food sources a purpose. New content should deepen decisions
and create longer-term goals without burying the player in explanations.
Default screens should show concise live data and controls. Important mechanics
belong behind [i] buttons; redundant descriptive prose should be removed.

Voluntary dangerous actions may cause substantial ant losses when the risk is
clear before committing. Players should decide whether the reward is worth
replacing workers; existing automatic egg production supports recovery. Build
meaningful population pressure before further brood-care progression. This
danger is introduced through the optional Rival Food Cache raid. Oldest home
workers depart first, but combat casualties are random within their party.
Exact balance remains adjustable; safe expeditions remain available. Optional
territorial contests extend this pressure into holding a productive location
with recurring casualties and player-controlled withdrawal/reinforcement.

Population losses can collapse a workforce, but must leave a recovery path
through the founding queen. Emergency recovery restores a foothold; it must
not sustain an established colony for free. See D17 in DECISIONS.

Outposts should eventually have distinct environmental requirements and/or
resources. The agreed first direction is a dry area with water scavenging and
living water storage. Dry Clearing exploration, water, repletes and a satellite
outpost are built. The outpost extends shared colony housing and supports a
team gathering protein-rich seeds with a water cost. The outpost now has a
small expansion and research path; additional locations remain future work.

## Exploration map

Exploration is a visible landscape centered on the nest. The first six neighboring
hexagons are handcrafted with a fixed reveal order and safe progression, rather
than random access to essential resources. Scouts exclusively reveal and study
sites; workers collect deposits. Knowledge declines with repeated study and is
finite per site; resource reserves are finite too. Exhausted places remain visible
as colony history. Managed aphids/outposts and renewable worker jobs retain value.
Exploration XP is removed. Further rings can open through future range research
or Understandings; those later unlocks are not designed or implemented yet.

## Understandings and prestige

**Understandings** is the reserved player-facing term for the most powerful
unlocks: they persist between prestiges and belong to the queen's lineage.
DNA is the prestige currency used to purchase them; Knowledge remains colony
research currency.

The agreed foundation is generational inheritance through queens. A young queen
carries the previous generation's Understandings into a new colony. The owner
specifically chose queens as the bridge between generations, adapting the
uplifting inspiration to ants and the incremental prestige structure.

Knowledge is the existing colony's research resource. Ordinary upgrades and
map discoveries are distinct from inherited Understandings.
Do not relabel those systems as Understandings or imply that current research
is already a prestige system.

The owner wants two forms of prestige:

- **Upgrade progression:** the new colony begins with an ability already
  unlocked and/or a permanent production or capacity boost. Permanent numeric
  bonuses are valid rewards; Understandings need not all introduce new mechanics.
- **Challenge progression:** the daughter colony is founded in a less suitable,
  more challenging location. Completing that challenge unlocks something new.
  The specific locations, objectives and rewards are still undecided.

Raising a daughter queen is an approved direction for connecting generations.
The owner likes the opportunities this creates for new chambers and worker
jobs. The approved first foundation is woodland resin collection and Royal
Nursery construction, preparing one royal brood slot. Royal Nurses and sustained
daughter-queen rearing are implemented, with founding following in the first
prestige loop.
Keep the distinction between expanding the present colony through satellite
outposts and founding a new generation through a daughter queen.

The first roughly three or four prestiges should introduce mechanics in a
mostly linear sequence so players learn colony management gradually. Afterwards,
shorter and longer runs should offer meaningful choices in DNA accumulation.
Remaining introductory rewards, exact count and later DNA scaling remain undecided.

The first implemented Understanding is Shared Paths, automating safe collection
from studied sites with workers and one guiding scout per ten workers, rounded up.
Manual and automatic collection draw from the same finite deposits. Founding gives an automatic
+10% sugar production per completed prestige, stacked additively; DNA buys
Understandings separately. The new colony starts with the normal initial queen
and no workers. The reset preview lists what is lost and inherited. The founding
action is called Found daughter colony. How challenge rewards interact with Understandings,
whether normal and challenge foundings can be combined, and how completion or
abandonment works also need design before implementation.

## Possibilities, not commitments

Further Understandings and challenge colonies follow the first founding loop. Beyond the
royal brood humidity and feeding implemented in that step, wider humidity,
pheromone routes, carrying capacity, waste management, injuries and chemical
records remain suggestions rather than an approved backlog. The owner asked to
preserve interest in waste/maintenance on 2026-11-09; its introduction point and
rules remain undecided. See [recorded ideas](IDEAS.md#waste-and-colony-maintenance). The existing Brood
Care upgrade speeds ordinary brood; royal care uses its own nurse/feeding system.

Deferred ideas the owner liked: temporary vulnerable food sources that reward
fast replacements, and worker-to-soldier specialization trading ordinary labor
for protection. Revisit after territorial contests; neither is implemented by
that feature, and exact rules remain proposals.

The owner approved redesigning brood around gradual laying, development-space
limits, larval feeding and nurse throughput, with Queen's Routine using the same
pipeline. An approved small founding worker phase keeps the opening quick before an
unlock switches future brood to ordinary workers. Small workers provide half
standard task output and live somewhat shorter lives, with a generous transition
window. Existing adults retain their type. This founding phase and the staged
brood pipeline are implemented; exact timings and costs remain adjustable.
See D16 in DECISIONS.
