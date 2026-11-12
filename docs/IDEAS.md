# Recorded ideas

These are possibilities to evaluate, not an approved implementation backlog.

## Waste and colony maintenance

Recorded 2026-11-09. The owner finds waste management interesting and asked to
preserve the idea. **When to introduce it is undecided.** No implementation,
unlock stage, balance values or prestige placement has been approved.

The intended opportunity is meaningful colony maintenance tied to workforce
allocation and investment in nest space, while preserving the ant-colony identity.

Suggested starting design (assistant proposal, not approved rules):

- Population, larval feeding and/or carcass consumption generate waste. Choose
  sources carefully rather than charging the same activity multiple times.
- Waste Carriers transport refuse to an external midden, competing with other
  productive jobs for workers.
- Refuse chambers cost tunnelling space and buffer waste. Excavation funds the
  infrastructure; avoid continuously consuming the tunnelling resource merely
  to represent accumulated dirt.
- Excess waste could cause a gradual, capped brood-care penalty. Cleaning and
  emergency recovery must remain possible; avoid a self-reinforcing collapse.
- Keep presentation compact: amount/capacity, net rate, time to full and any
  active penalty. No repeated maintenance clicking or tutorial paragraphs.

A Nursery-stage introduction was suggested previously, but the owner has not
chosen that timing. It could instead fit later-colony pressure or an introductory
prestige; none of those placements is decided. Disease/contamination would be a
separate later possibility, not part of an approved first version.

If introduced through prestige, pair the pressure with a meaningful new capability
or reward rather than making founding itself an unavoidable punishment.

Before implementing, decide the unlock point, production/removal rates, carrier
eligibility, capacity/overflow behavior, default-worker and automation interaction,
recovery behavior, and reset/save rules. Remembered resource visibility must not
unlock maintenance mechanics early after a prestige (D29).

Evaluate whether waste creates worthwhile worker/space choices beyond existing
food upkeep and brood nursing. If it is only an additional compulsory labor tax,
revise or defer it. The owner has authorized recording and merging this design proposal as
documentation, not building the mechanic.

## Shared Paths after the map rework

Owner feedback on 2026-11-12: the first prestige Understanding's pheromone trail
felt effectively useless; a screenshot shows the generic “No nectar source” panel.
This is feedback and a redesign proposal, not approval of new gameplay rules.

Verified source behavior:

- Before the hex-map change, returning Nectar Meadow expeditions could reveal
  another 1,200-sugar source after the previous one depleted.
- The map rework calls discovery only at the first Meadow study, requires Shared
  Paths to be owned then, and reserves its source from the finite 20,000-sugar
  deposit. No later study/haul replenishes it. Learning the Understanding after
  the first study never reconciles that missed source.
- Ten workers produce 8 sugar/s before inheritance, or 8.8 after the first
  founding. At that rate the 1,200 source lasts about 136 simulated seconds,
  before allowing for storage pauses. Marking costs 50 sugar and reserves a scout.
- “No nectar source” conflates an unstudied site, a missed one-time unlock and an
  exhausted source. The screenshot alone cannot identify the player's exact state.
- Existing tests cover discovery with ownership already present, but not late
  purchase or an actual ongoing post-prestige collection loop. README still
  described repeat expedition discoveries and has been corrected in this branch.

Recommended direction to discuss: make Shared Paths unlock maintained collection
from the studied Meadow's whole remaining finite deposit, replacing the arbitrary
1,200 sub-source. Workers follow a scout-maintained route without repeated manual
expedition launches or occupying expedition slots. Keep collection finite and
account for pending haul cargo so resources cannot be duplicated. Recognize an
already-studied Meadow when the Understanding is purchased; do not restore spent
nectar. Put trail availability/controls on the relevant selected hex, with clear
study/staffing/exhaustion states instead of a permanently empty global panel.

This gives the first Understanding a colony-management capability rather than a
brief sugar reward. Exact staffing, throughput, marking costs, automation and any
later expansion to other resources still require agreement and balance checks.
Do not silently restore unlimited renewable discoveries: the owner deliberately
chose finite hexagon resources in the exploration redesign.
