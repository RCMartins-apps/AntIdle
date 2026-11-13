# Linear collection and Shared Paths pacing check

Measured 2026-11-12 on feature/automated-collection-trails, save version 7, with the
actual engine, default policies, seeds 42/137 and a four-hour budget per run:

```sh
python3 scripts/balance.py --out /tmp/trail-pacing --hours 4
```

The final curve preserves the old five-worker haul, scales linearly to fifty and
uses 60% manual throughput for automatic trails, with proportional supplies and
normal worker/guide upkeep. Growth-first's old 17-worker policy is now a valid
15-worker crew. Other policy goals remain unchanged; post-prestige policies assign
ten workers to the Nectar trail and reserve a third scout for guiding.

All times are cumulative simulated minutes:

| Policy | Seed | First founding | Second founding | Starvation deaths |
| --- | ---: | ---: | ---: | ---: |
| balanced | 42 | 102.3 | 206.4 | 0 |
| balanced | 137 | 104.2 | 208.5 | 0 |
| growth-first | 42 | 95.2 | 175.8 | 0 |
| growth-first | 137 | 97.4 | 177.9 | 0 |
| exploration-first | 42 | 143.2 | Not reached within budget | 0 |
| exploration-first | 137 | 140.0 | Not reached within budget | 0 |

All sampled resources were nonnegative. Every run purchased Shared Paths and
performed two five-worker assignment actions in its second colony. The smaller
exploration-first policy was still progressing when the budget ended; lack of a
second founding is not evidence of a resource softlock.

An earlier candidate preserved ten-worker rather than five-worker hauls and used
80% automatic throughput. It weakened small initial parties and exploration-first
needed roughly 175 simulated minutes for first founding. That evidence motivated
preserving five-worker hauls and reducing automatic efficiency to retain its
tradeoff against manual expeditions. This is a targeted tuning observation, not a
controlled comparison against every other historical version.

These simulations establish reachable progression for these heuristics/seeds,
not that balance is universally fun or optimal. Actual players should compare
manual bursts versus trail assignment, scouts needed for studies, and upkeep when
running several resource trails. Future tuning should preserve finite deposits,
manual cargo reservations and shared storage. See
[collection rates and rules](../../docs/COLLECTION-TRAILS.md).
