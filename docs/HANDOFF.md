# Handoff

## Last verified: 2026-11-15 — permanent compounding milestones

- Work is on `feature/permanent-milestones`. PR #76 is confirmed merged at
  main 3fa8eee; this branch integrated main with the tested feature tree unchanged.
  The owner authorized PR creation and native squash auto-merge for this branch.
  Main protection requires test and changes / classify with strict up-to-date
  checks and no bypass; squash-only auto-merge settings were reverified.
  Check GitHub for live PR/merge state; finish after queueing without CI polling.
- Owner explicitly chose permanent lifetime milestones and multiplicative rewards:
  each tier gives ×1.01 sugar; N tiers gives 1.01^N. A dedicated Milestones tab
  shows the combined multiplier, introduced goals, expandable tiers and resource
  peaks. Paid colony research still resets and uses current-run prerequisites.
  D48 supersedes D47's general exclusion of permanent non-Understanding bonuses.
- Goals cover all six gathered resources, standard births, peak worker population,
  scout births, mission returns, carcasses, repletes, territorial contests and
  daughter queens. Goals/rewards are centralized in ColonyMilestones. No claim
  buttons or recurring messages. Disclosure DOM stays stable across live ticks.
- Exact rational multiplication applies once to positive sugar gains, including
  collectors, aphids, manual cargo and trails; spending/upkeep are unchanged.
  Fixed-point output floors once. Finite deposits debit raw cargo and refund
  unused raw units on overflow. Previews and feeding forecasts include bonuses.
- Statistics saves now store maximum holdings for every resource, with lifetime
  maxima retained on founding. Older saves default missing peak fields and seed
  them from current holdings on load; historical peaks cannot be reconstructed.
  Existing lifetime totals immediately qualify. Save version remains compatible.
- Final engine validation: 289 tests and 4 balance tests passed. Development,
  production and balance-runner builds passed with zero Closure errors/warnings.
  Production UI gate passed at 320/390/1280px, including real resource-tier completion,
  saved peaks, lifetime reload and stable disclosure. Prestige browser checks passed
  at all three widths. Inspected the 320px tab screenshot; corrected singular copy.
- Two balanced-policy seeded runs (42/137) reached two foundings in 166.9/165.1
  simulated minutes without starvation or negative sampled resources. First
  daughter readiness 90.4/90.3 minutes. These are faster than before, not evidence
  of universal balance/enjoyment. MILESTONES.md records rules and reproduction.
- Preserve unrelated work/ and Python caches. Screenshots/logs under /tmp are not
  durable chat attachments. For a later authorized PR, verify enforced checks,
  enable native auto-merge, then finish without polling CI.
