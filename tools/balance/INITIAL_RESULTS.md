# First measured pacing baseline

Measured 2026-11-09 with `default.json`: three policies × seeds 42/137,
up to four simulated hours each, targeting two foundings. The final bundle was
rebuilt and tested before the `--skip-build` run. Node v24.19.0 took about
84 wall-clock seconds for all six runs (build time excluded). These numbers are
**policy observations**, not targets or a claim that the game is fun/balanced.
No gameplay balance values were changed.

All times below are total simulated minutes at normal speed. "Second founding"
is the cumulative time; subtract the first founding for the second colony's time.

| Policy | Seed | Exploration | First founding | Second founding |
| --- | ---: | ---: | ---: | ---: |
| Balanced | 42 | 31.2 | 124.2 | 227.8 |
| Balanced | 137 | 31.2 | 107.7 | 209.8 |
| Growth first | 42 | 35.8 | 99.8 | 189.8 |
| Growth first | 137 | 34.8 | 102.8 | 191.2 |
| Exploration first | 42 | 32.3 | 134.1 | Not reached within 240 min |
| Exploration first | 137 | 31.9 | 110.2 | 208.1 |

The current growth-first policy reached exploration later, but first founding
earlier. This suggests population/expedition reward interactions deserve testing;
it does **not** prove larger colonies are always better. These policies avoid
voluntary danger and have different labor/party allocations as well as population
targets, so this comparison cannot isolate a single cause. A controlled follow-up
should change one policy parameter at a time with more seeds.

All six runs had zero starvation deaths. Aging deaths were 72–129 across each
whole observation window, with routine replacements handled by real game rules.
The exploration-first seed-42 run was still progressing (its second Royal Nursery
was built at about 233 min), so its budget exhaustion is not a softlock.

First small workers appeared at 20 seconds; Standard Worker Development at about
3.5 minutes; first standard adults at about 9.7 minutes. A useful first human
spot check is the period between standard research and the first adult, then the
transition into exploration. Are there satisfying decisions during these waits?
The simulator cannot answer that question.

The standalone report contains the underlying decisions, labor/stock curves,
policy wait episodes and eight checkpoint milestone families. Its mobile/desktop
controls and real production-game loading of all eight families were checked at
390/1280px. An independent short CLI run repeated against itself matched exactly,
including events, samples and milestones; the baseline comparison retained it.

To reproduce or compare a subsequent change, follow [README.md](README.md).
Keep generated full reports outside Git; this small measured summary is the
persistent record. Future game or policy changes will legitimately change it.
