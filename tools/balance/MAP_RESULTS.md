# Initial handcrafted-map pacing observations

Measured on 2026-11-10 with save version 7, three policies × seeds 42/137,
a four-hour simulated budget per run, normal-speed ticks and no injected resources
beyond the explicitly recorded opening gathering allowance. Policies are heuristics,
not optimized players. These are observations, not balance targets or proof of fun.

All times are total simulated minutes. Second founding is cumulative time.

| Policy | Seed | First hex | Entire ring studied | First founding | Second founding |
| --- | ---: | ---: | ---: | ---: | ---: |
| balanced | 42 | 34.8 | 66.6 | 121.1 | 229.0 |
| balanced | 137 | 35.1 | 66.2 | 122.0 | 231.2 |
| growth-first | 42 | 38.0 | 69.6 | 113.2 | 212.8 |
| growth-first | 137 | 37.2 | 67.9 | 114.2 | 213.2 |
| exploration-first | 42 | 37.6 | 60.0 | 145.8 | Not reached |
| exploration-first | 137 | 35.2 | 61.4 | 151.8 | Not reached |

All six runs reached first founding without starvation, raids or territorial
combat. Four reached two foundings; the two smaller exploration-first colonies
were still progressing in their second colony when the budget ended. All collected
Knowledge stayed within the finite per-colony budget and resource samples stayed
nonnegative. Required sites and Knowledge do not depend on the random seed;
ordinary age jitter and strategy choices can still affect timing.

Tests independently calculate that the ring's 7,000 Knowledge exceeds all current
research, supplies, outpost establishment and both extension costs combined. All
useful nectar crews have a positive full-load return after supplies. Resin overflow
returns to the finite site; water discovery enables renewable Dew Gatherers.

Interpretation: additional scouts speed map work, but that alone doesn't pay for
infrastructure or sustained royal care. The smaller policy studied the ring sooner
and founded later. This is a useful playtest question, not evidence to force every
player toward the larger population policy. Short human checks should assess the
period after studying the ring and the approach to royal care.

Production browser checks cover 390/1280px reveals/studies, selected-site controls,
capacity blocking, exhaustion and reload. Generated full reports and checkpoint
saves remain outside Git; rerun the documented CLI to reproduce new experiments.
The previous XP-based baseline is retained as historical INITIAL_RESULTS.md.
