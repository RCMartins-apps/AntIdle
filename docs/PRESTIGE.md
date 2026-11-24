# Queen's lineage and introductory prestige

Founding with a ready daughter queen grants 1 DNA and adds ten percentage points
to inherited sugar production. First founding gives +10%, second +20%, and so on;
it is additive, not a new 10% multiplier applied to the previous bonus. It affects
Sugar Collectors and Aphid Tenders, not resource hauls or trail efficiency.

## Founding flow

1. Rewards: current/next generation, DNA earned/available/spent/remaining and current/
   next inherited sugar bonus. Resource stocks/capacities, population, chamber
   levels, research names and other current-colony reset values are reviewable.
2. Understandings: select permanent unlocks using saved DNA plus this founding's
   reward. Existing ownership carries automatically. Selection is only a preview;
   Back does not charge DNA. Leaving DNA unspent is allowed.
3. Confirmation: review the resulting lineage and reset values, explicitly accept
   the colony reset, then found. The final action revalidates the live state and
   applies learning, DNA payment and reset atomically. No browser confirm dialog.

The first colony can learn Shared Paths (1 DNA) at its first founding. The second
introductory Understanding is Beyond the Woodland (1 DNA), requiring Shared Paths
and two completed foundings including the one being planned. It opens three
handcrafted distance-two sites after the six original reveals, in fixed order.
Banked DNA may also buy an available Understanding outside the founding flow,
for compatibility with existing lineages. Preview choices are not saved purchases.

## Current-colony visibility

Founding resets resource and population rows to the same visibility as a fresh
game. Sugar remains visible; other resources and castes unlock through this
colony's actions. Spending the last unit or losing ants keeps earned rows visible
for that colony. Lifetime statistics, milestone rewards, DNA and Understandings
remain permanent, but do not reveal optional current-run resources.

Older saves at an untouched daughter-colony start are repaired on load if all
current-colony state and statistics are still initial. Active runs keep their
saved discovery history; empty stocks alone never imply an undiscovered resource.
No save reset or new schema is needed.

## Beyond the Woodland

The inner ring, its deposits and progression remain unchanged. No extra protein
source or currency is added. Outer sites only offer scout studies: Knowledge is
finite, paid in the same 50% / 30% / 20% sequence. Three studies reveal research:

| Site | Knowledge | Research | Cost | Effect |
| --- | ---: | --- | --- | --- |
| Aphid Thicket | 800 | Aphid Shelters | 2,000 sugar, 200 Knowledge, 20 resin | 10 → 20 aphid places per chamber level |
| Fallen Oak | 900 | Trail Logistics | 3,000 sugar, 300 Knowledge | 60% → 75% of equivalent manual haul rate |
| Root Hollow | 1,000 | Coordinated Expeditions | 4,000 sugar, 400 Knowledge | +1 expedition slot |

Aphid Shelters creates housing, not animals; relocation and one tender per ten
aphids still apply, with unchanged per-tender rates. Trail Logistics scales
supplies alongside collection, preserving supply cost per haul; guide quotas and
normal ant upkeep remain. All three research purchases are current-colony
upgrades, so they reset on founding; their permanent source of access is the
Understanding. The second-ring budget of 2,700 Knowledge covers their 900 cost.
Existing save version 7 defaults the new Understanding to unowned and continues
normally. A new colony still begins with the queen, no workers and no revealed
hexes, research or aphids. This step adds no challenge colony or arbitrary DNA
scaling. Later introductory Understandings and challenge rules remain undecided.

## Initial pacing checks — 2026-11-14

The real-engine balanced policy ran with seeds 42 and 137, a six-hour budget and
three foundings as its goal. It chooses Shared Paths at first founding and Beyond
the Woodland at second, and can research the outer upgrades. Both runs reached
all three foundings, studied the outer sites and purchased all three new upgrades.
Neither had starvation deaths. Existing natural aging still replaced workers.

| Seed | First daughter ready | Second founding / new Understanding | Outer sites studied | Third founding |
| --- | ---: | ---: | ---: | ---: |
| 42 | 105.5 min | 210.8 min | 279.5 min | 313.6 min |
| 137 | 103.1 min | 208.0 min | 277.1 min | 311.3 min |

These are two scripted policies/seeds, not proof that every strategy is balanced
or enjoyable. No pre-change controlled comparison is claimed for the new content.
Reproduce with scripts/balance.py --policy balanced --seed 42 --seed 137 --hours 6
and a copy of tools/balance/default.json with foundings set to 3. Build the runner
first. Temporary reports/checkpoint saves were produced under
/tmp/antidle-second-prestige-pacing; they are not durable repository artifacts.

## Satellite Nests

At the third founding, a lineage with Beyond the Woodland can learn Satellite
Nests for 1 DNA. Earlier colonies keep their original costs. Once inherited,
Dry Clearing costs 1,500 sugar and 150 Knowledge to establish, down from 3,000
and 300. Water, protein, tunneling space and discovery requirements still apply.

After establishing the outpost, choose one specialization for the current colony.

| Station | Effect | Tradeoff |
| --- | --- | --- |
| Seed Station | +50% protein per Seed Forager | +25% water per Seed Forager |
| Scout Station | 40% shorter site studies with 2–3 scouts | 5 water per supported study |

Both stations keep ordinary outpost housing and Seed Forager slots. Scout
Station leaves one-scout studies and map reveals at their ordinary costs
and times. Study duration bonuses stack multiplicatively with Scout Wayfinding.
Stations leave finite Knowledge rewards unchanged. The permanent Understanding
survives founding, while the outpost and station choice reset.

## Dry Season challenge founding

Starting with the fourth founding, a lineage that already owns Satellite Nests
can choose Dry Season in the founding confirmation. The daughter colony follows
ordinary progression, with Dew Gatherer output reduced from 0.15 to 0.075 water/s
per worker. Site deposits and water expedition costs retain their normal values.

To complete it, found again with Dry Clearing Outpost established and at least
two living repletes. The usual ready daughter queen requirement applies. Founding
before meeting both targets exits Dry Season without the reward and allows a
future retry. Successful completion ends the challenge and permanently raises
water capacity per replete from 100 to 125. The reward applies to all future
colonies, and successful lineages retire the challenge. Standard DNA and sugar
bonuses apply to both challenge and ordinary foundings. Existing saves default
to an inactive, incomplete challenge.

## Founding Provisions

The fourth and final introductory Understanding costs 1 DNA after Satellite
Nests. It adds 15 starting sugar, enough for three extra small-worker eggs.
Buying it with banked DNA affects future foundings. Provisions are starting
stock, so they do not count toward sugar gathered or carry across resets.
