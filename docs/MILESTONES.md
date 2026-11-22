# Current-colony milestone research

Milestones turn the existing statistics into optional goals that open ordinary,
paid research. They grant no free resources or permanent bonuses. Understandings
remain the inherited system; these upgrades and their goals reset on founding.

| Research | Current-colony goal | Prerequisite | Price | Effect |
| --- | --- | --- | --- | --- |
| Scout Wayfinding | 20 expeditions completed | Exploration research | 500 sugar, 50 Knowledge | −20% scout reveal/study duration on new departures |
| Nurse Training | 25 standard workers raised | Brood Care | 1,000 sugar, 30 protein, 100 Knowledge | +50% Brood Nurse care |
| Queen's Rhythm | 50 standard workers raised | Queen's Routine | 2,000 sugar, 50 protein, 150 Knowledge | −20% worker/scout laying time for new eggs |

Completed expeditions count actual returns, not launches or attempted actions.
Standard workers raised are workersMatured minus smallWorkersMatured: the existing
first counter intentionally includes both castes. Scouts and small founding workers
do not count toward the worker goals. Lifetime totals do not unlock current-colony
milestones. Existing saves with sufficient this-run history can research them.

Upgrades previews one relevant unfinished goal, its progress, effect and full price.
Once the goal is met, research appears in the ordinary upgrade list with normal
cost/affordability feedback. Completed goals move out of the preview; purchased
research remains inspectable in purchased history. No recurring milestone messages,
new tab, claim clicking or separate currency is introduced. Base progression does
not require any milestone purchase, and advanced research actions remain hidden
until their prerequisites and goals are met.

## Timing and care details

Scout Wayfinding changes a one-scout reveal from 60 to 48 seconds and a study from
180 to 144. With three scouts the durations are 16 and 48 seconds. It does not
change sugar cost, Knowledge, worker hauls, cargo, scout quotas or existing parties'
target ticks. Action previews use the same calculation as departure.

Nurse Training changes each standard nurse's base contribution from 2 to 3 care
units, with small nurses retaining half power. The queen's base care, per-brood
speed cap, Royal Nurses and food required per finished brood are unchanged. It
increases throughput when several brood share care. Brood Care's existing bonus
continues to apply to the combined budget; forecasts use the same tending logic.
Faster care can spend feeding resources faster, not create brood without food.

Queen's Rhythm changes the level-one queen's standard/scout laying time from
20 to 16 seconds. It combines with existing Queen Chamber improvements using the
same rounded tick calculation. An egg already being laid keeps its remaining time.
New worker and scout eggs use the shorter laying time. Development, costs and brood-space
limits remain. Queen Chamber previews account for the active rhythm upgrade.

## Editing

Goal counts, prices and reveal prerequisites live in UpgradeBalance. Effect
percentages live in MilestoneResearch. BroodProgression and ExplorationMap own the
shared engine/preview calculations. No extra save fields or version bump are
needed; upgrades use ordinary saved purchase flags and existing statistics.

## Verification

MilestoneResearchSuite exercises current-run thresholds, caste exclusion, paid
purchases, mission target times, unchanged Knowledge/costs, actual brood care and
feeding forecasts, worker/scout laying, chamber previews, reload and founding.
The production CI browser gate reaches goals through a real return and real
worker maturation, checks the compact goal progression, buys all three upgrades
and verifies the updated scout preview at phone and desktop widths.


Two four-hour balanced-policy runs (seeds 42/137) bought all three research upgrades
in both colonies and reached two foundings without starvation deaths or negative
sampled resources. First daughter readiness was 108.5/109.8 simulated minutes;
second readiness was 216.5/217.3. Reproduce after building the runner with:

```sh
python3 scripts/balance.py --skip-build --out /tmp/milestone-pacing \
  --policy balanced --seed 42 --seed 137 --hours 4
```

These policies now prioritize the optional purchases. Their spending made these
runs a few minutes slower than the earlier second-prestige runs; this is not a
controlled comparison or an enjoyment measurement. Nurse care matters under brood
load, while laying speed matters when laying is the bottleneck. The upgrades are
optional choices, not a promise that buying everything immediately is optimal.

## Permanent achievements

The **Milestones** tab tracks lifetime achievements separately from the paid
current-colony research above. Each reached tier automatically multiplies all
sugar gains by ×1.01. N completed tiers gives 1.01^N, rather than 1 + N/100.
Ten tiers give ×1.1046 (rounded only for display). Resource arithmetic uses an
exact integer ratio and floors the final fixed-point gain; it does not repeatedly
round at each milestone. Negative amounts, supplies, upkeep and purchase costs
are never multiplied. The multiplier stacks with the existing inherited bonus.

| Goal | Tier targets |
| --- | --- |
| Sugar gathered | 100; 1,000; 10,000; 100,000; 1,000,000 |
| Protein, resin gathered | 10; 100; 1,000; 10,000 each |
| Knowledge gathered | 25; 100; 500; 1,000 |
| Water gathered | 25; 100; 1,000; 10,000 |
| Space excavated | 100; 1,000; 10,000; 100,000 |
| Standard workers raised | 10; 25; 50; 100; 500 |
| Workers alive at once | 10; 25; 50; 100; 250 |
| Scouts raised | 1; 5; 10; 25 |
| Expeditions completed | 5; 20; 50; 100 |
| Carcasses retrieved | 1; 5; 20 |
| Repletes promoted | 1; 5; 10 |
| Territorial contests completed | 1; 5; 20 |
| Daughter queens raised | 1; 3; 10 |

Targets and rewards are centralized in ColonyMilestones. Resource goals count
retained/used output, not discarded overflow. Sugar income includes collectors,
aphids, manual expedition cargo and automatic trails; a carcass offsets upkeep
rather than producing sugar and is unchanged. Finite deposits still debit raw
cargo; the bonus improves yield without regenerating deposits. Unstored return
cargo is refunded in raw deposit units. Mission and trail previews include the
bonus; costs remain base amounts. Existing parties receive the current multiplier
when delivering cargo. Founding provisions and protected recovery are unchanged.

The tab shows the combined multiplier and one row per introduced goal. Expand a
row to inspect its tiers. Disclosures survive live ticks. Resource rows also show
lifetime peak holdings. Peak worker population includes small and standard workers;
standard-worker birth goals exclude small workers and scouts. Lifetime goals do
not reset when a colony spends resources, loses ants or founds a daughter colony.
The founding preview explicitly lists the milestone multiplier as kept.

Statistics saves now include this-colony/lifetime maximum holdings for all six
resources. Older saves default absent peak fields to zero and seed them from
current stores on load; past holdings cannot be reconstructed. Existing lifetime
counts immediately qualify for achievements. The existing save schema remains
compatible; no save-version reset is needed. New colony counters reset while
lifetime totals and peaks persist.

With permanent achievements enabled, two balanced-policy four-hour-budget runs
(seeds 42/137) reached their two-founding goal in 166.9/165.1 simulated minutes.
First daughter readiness was 90.4/90.3 minutes; both runs had zero starvation deaths
and no negative sampled resources. This is faster than the earlier paid-research
runs recorded above, as expected from the new sugar multiplier. The samples do
not establish player enjoyment or an optimal strategy. Use the same reproduction
command above with a fresh rebuilt balance runner.
