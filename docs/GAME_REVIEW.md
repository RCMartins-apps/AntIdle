# Game state review — 2026-11-09

Baseline: merged main `a3b5ac1` (PR #49). This is a source/design review against
VISION and DECISIONS, not a newly timed playthrough or a player-telemetry study.
The last merged CI passed 242 engine tests, and relevant phone/desktop smoke
checks were run during that work. No builds/tests were rerun for this document.
Assessments and priorities below are recommendations, not approved new features.

## Overall assessment

The game has a coherent first-colony foundation and a recognizably ant-shaped
economy. The largest content gap is what makes the second and third foundings
meaningfully different. Technical correctness is better established than whole-run
pacing, economic balance or long-term engagement.

## Areas with a solid foundation

### Ant identity and connected resource chains

Aphid tending exchanges protein for sugar; Dry Clearing foraging exchanges water
for protein; repletes exchange a worker role for living water capacity. Royal
rearing uses nurses and sustained feeding after resin-funded infrastructure.
These connections give expansion a biological purpose instead of attaching ant
names to independent currencies. Keep extending these relationships.

Evidence: AphidHusbandry.scala, OutpostProgression.scala, RepleteProgression.scala,
RoyalBroodProgression.scala in `src/main/scala/pt/rcmartins/antidle/game/`.

### Population and brood mechanics

Founding workers provide a fast opening, then standard-worker research changes
future brood. Laying order, brood places, larval feeding and nurse throughput
are distinct constraints. Aging, dangerous raids and territory create replacement
demand; Queen's Routine and emergency recovery support managing losses.
This is a good mechanical structure. It does not establish that hatch times,
nurse efficiency or recovery generosity are optimally balanced in a full run.

Evidence: BroodProgression.scala, PopulationAging.scala, AutomaticBrood.scala,
EmergencyRecovery.scala, RaidProgression.scala, TerritoryProgression.scala.

### First exploration arc

Ten destinations and six discoveries connect XP, research, water, resin, territory
and royal development. Locked destinations stay hidden; discovery badges now
identify unfinished opportunities. Party caps/presets, extra-scout speed, visible
costs/risk and the mobile carousel address several concrete usability problems.
This is a useful first progression spine, rather than just a resource button.

Evidence: ExplorationDestination.scala, DiscoveryProgression.scala,
ExplorationProgression.scala and MainForm.exploreDiv.

### Engine organization and verification

Most gameplay rules have separate immutable-state modules, with shared calculations
for several costs, capacities, forecasts and displayed rates. Save loading
reconciles aging, assignments and visibility. PR CI builds the production bundle
and runs engine tests. This is a strong prototype foundation, not a claim that
all state combinations or UI behavior are covered.

## Areas needing improvement

### 1. Prestige depth — largest concrete gap

Prestige.found currently gives exactly one DNA and an additive +10% sugar bonus.
Prestige.purchase exposes one purchasable Understanding, Shared Paths. After it
is learned, further DNA has no additional purchase in the current catalog.
There is no implemented challenge-colony path or variable-DNA short/long-run
choice. The agreed roughly three/four introductory prestiges are a direction,
not existing content.

Next content priority: design the next introductory founding/Understanding around
a distinct colony-management decision, with a clear reason to raise another
queen. Do not assume waste is that mechanic before its timing is chosen. Avoid
adding a broad prestige tree before the introductory sequence has a purpose.

Evidence: Prestige.scala, model/Lineage.scala; VISION's prestige section.

### 2. Whole-run balance and pacing — needs measurement

Basic sugar has diminishing marginal collector yields and upkeep, so pressure
exists in the model. Aphids, nectar trails, outpost protein and inherited output
change that economy substantially. Source inspection alone cannot show where
scarcity becomes too weak, queues become too slow, or one strategy dominates.
Knowledge demand ends with the current finite research catalog (21 upgrades).
Resin/water have meaningful but concentrated uses around existing content.

Before adding another maintenance burden, time representative runs: new game to
standard workers, exploration, outpost, royal nursery, first founding and then a
second run. Track waits, worker allocation, stockpiles, losses and what causes
inactivity. Existing run/lifetime statistics help, but do not yet constitute a
complete pacing trace. The opening still depends on manual Gather Sugar; decide
whether that short clicking phase is intentional or should become queen
provisioning. Later emergency recovery does not replace the fresh-colony opening.
Tune demonstrated bottlenecks and surpluses instead of
assuming every resource needs another sink.

Evidence: Foraging.scala, TickCalculator.scala, model/StatisticsData.scala,
model/UpgradesData.scala.

### 3. Mid/late exploration and outpost decisions — good templates, little breadth

Discovery goals are finite; completed sites increasingly become repeat resource
hauls. Useful choices exist already (risk, party size, travel speed, storage),
but destination specialization and reasons to revisit sites could deepen.
There is one satellite outpost with two extensions and a seed-foraging chain,
not yet a system of contrasting colony locations.

Prefer a small number of destinations/outposts with distinct requirements and
tradeoffs over many copies with larger rewards. Reuse the existing exploration,
water, workforce and discovery systems. Evaluate repetition/automation against
actual play before automating away the remaining choices.

### 4. Nest space and maintenance — still shallow

Tunnelling is primarily an excavated-space construction budget with a Nest-scaled
cap. It is not a spatial nest simulation or an ongoing maintenance system.
That abstraction is workable, but does little by itself after building demand
slows. Waste could make workforce and space investment matter longer; it could
also become a repetitive labor tax. Keep it tentative until a full-run review
identifies where maintenance would add decisions. See IDEAS.md.

### 5. UI consistency and regression protection — improved, not finished

Costs, rates, capacity timing, concise help, discovery indicators and tab separation
are much better. Recent bugs still show two recurring risks: rebuilding reactive
sections resets open controls, and remembered resource visibility can accidentally
bypass current-colony progression. Their fixes should become reusable patterns.

MainForm.scala is still roughly 2,200 lines covering most screens. Extract Nest,
Explore and Lineage components gradually as they change, keeping domain rules
outside UI code and sharing requirement/bonus view models. Avoid a full rewrite.

The Python browser smoke suite is extensive, but `.github/workflows/deploy-test.yml`
only runs `sbt fullOptJS` and `sbt test`. Add a small reliable mobile/desktop browser
subset to CI covering navigation, disclosure persistence and founding/reset gates.
Prioritize these failure classes over more cosmetic snapshot tests. A concise
next-goal indicator may help progression if playthroughs show players getting
lost; avoid adding tutorials by default.

### 6. Save longevity — deliberate prototype limitation

SaveLoad accepts the current save version; there is no general cross-version
migration system. The owner previously accepted breaking prototype saves, so this
is not an urgent defect or a reason to slow all content work. Plan migration and
restore compatibility before external players depend on long-lived colonies.
Keep reset/load/visibility reconciliation tests as new permanent unlocks appear.

## Recommended order

1. Measure a complete first and second run and identify actual pacing/economy gaps.
2. Define the next introductory prestige reward/goal; this is the largest content
   gap. Use the measurements to place it and choose its resource demands.
3. Add a small browser CI gate and extract UI components incrementally alongside
   feature work, especially shared visibility and live-control patterns.
4. Deepen one exploration/outpost branch with distinct decisions rather than
   adding breadth everywhere.
5. Reassess waste against the identified maintenance gap. Its timing is undecided;
   this review does not authorize implementing it.

If choosing one substantial content project now, choose the next meaningful
prestige step. If choosing one small engineering improvement, choose the browser
CI gate. Neither recommendation is an approved implementation task yet.
