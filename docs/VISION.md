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

## Progression and pressure

The colony should need better methods because supporting growth creates natural
constraints. Basic sugar collection has diminishing returns, giving exploration
and alternative food sources a purpose. New content should deepen decisions
and create longer-term goals without burying the player in explanations.

Outposts should eventually have distinct environmental requirements and/or
resources. The agreed first direction is a dry area with water scavenging and
living water storage. Dry Clearing exploration, water, repletes and a satellite
outpost are built. The outpost extends shared colony housing and supports a
team gathering protein-rich seeds with a water cost. The outpost now has a
small expansion and research path; additional locations remain future work.

## Understandings and prestige

**Understandings** is the reserved player-facing term for the most powerful
unlocks: they persist between prestiges and belong to the queen's lineage.
DNA is the prestige currency used to purchase them; Knowledge remains colony
research currency.

The agreed foundation is generational inheritance through queens. A young queen
carries the previous generation's Understandings into a new colony. The owner
specifically chose queens as the bridge between generations, adapting the
uplifting inspiration to ants and the incremental prestige structure.

Knowledge is the existing colony's research resource. Ordinary upgrades,
discoveries and exploration XP are distinct from inherited Understandings.
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
daughter-queen rearing are now implemented as the next step before founding.
Keep the distinction between expanding the present colony through satellite
outposts and founding a new generation through a daughter queen.

The first roughly three or four prestiges should introduce mechanics in a
mostly linear sequence so players learn colony management gradually. Afterwards,
shorter and longer runs should offer meaningful choices in DNA accumulation.
Exact introductory rewards, count and DNA scaling remain undecided.

The first implemented Understanding is Shared Paths, opening finite nectar
trails managed with workers and a guiding scout. Founding gives an automatic
+10% sugar production per completed prestige, stacked additively; DNA buys
Understandings separately. The new colony starts with the normal initial queen
and no workers. The reset preview lists what is lost and inherited. The founding action is called Found daughter colony. How challenge rewards interact with Understandings,
whether normal and challenge foundings can be combined, and how completion or
abandonment works also need design before implementation.

## Possibilities, not commitments

First Understandings and founding follow daughter-queen rearing. Beyond the
royal brood humidity and feeding implemented in that step, wider humidity,
pheromone routes, carrying capacity, waste management, injuries and chemical
records remain suggestions rather than an approved backlog. The existing Brood
Care upgrade speeds ordinary brood; royal care uses its own nurse/feeding system.
