# Recorded ideas

These are possibilities to evaluate, not an approved implementation backlog.

## Waste and colony maintenance

Recorded 2026-11-09. The owner asked to preserve the idea of waste as nest
pressure. The [Refuse Midden](MIDDEN.md) later shipped as an optional clean
nursery bonus. The pressure rules below are proposals.

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
revise or defer it.

### Nest refuse pressure proposal — 2026-11-24

A busy nursery fills with refuse and gradually loses room to start new eggs.
**Dig a buffer to absorb bursts, assign carriers to keep up, or grow in smaller
waves.** Food can be plentiful while the nest has too little clean space to
keep up. Leaf-cutting ants isolate waste in chambers or carry it to outside
piles, which gives both responses a natural model. [Waste management study](https://repository.si.edu/bitstream/handle/10088/1389/Bot_Currie_Hart_and_Boomsma.pdf)

- **Reveal at Nursery level 2.** Preview the mechanic on the upgrade. Start with
  a small buffer and access to the Refuse Carrier job. This leaves the opening
  alone and gives an established colony time to react. Make refuse reflect
  actual ordinary larval feeding so a hungry or resting nursery produces less.
- **Choose space or labor.** Carriers move refuse to an outside pile and compete
  with nurses, diggers, and collectors for workers. A sealed refuse chamber
  spends tunnelling space once to expand the buffer. More buffer space helps
  with bursts. Carriers handle sustained demand. A handful of stored batches
  should be practical without keeping a crew on duty around the clock.
- **Crowding reduces clean brood places.** Prototype the first lost place at
  roughly 80% full and cap the reduction at about one third. Clearing the
  backlog reopens places automatically. Existing eggs and larvae keep
  developing, outstanding orders keep their place, and the queen always has a
  recovery path. Refuse does not consume tunnelling capacity each tick.
- **Build on the existing midden.** Prototype replacing its clean-care bonus
  with the clean-space mechanic, so one meter has one consequence. Preserve the
  player's built midden, carrier assignments, and valid backlog when loading
  an existing colony. Already unlocked nurseries gain access to carriers. Start
  with larval refuse and leave carcasses outside the first prototype.
- **Show one compact meter in Brood.** For example, `Refuse 84/100 · +2/s · 1 place blocked`.
  Use the normal brood-place warning when a queued order has to wait. The meter
  and the job rates should come from the same calculation the tick uses.

Validate three pacing cases with the engine runner and on a phone: a small
colony raising occasional batches, a colony expanding at full brood throughput,
and an unattended colony returning with a full buffer. The second should see a
reason to choose between a chamber and more carriers. The third should recover
through reassignment without abandoning brood or rebuilding the nest. Thresholds,
costs and throughput are tuning proposals.
