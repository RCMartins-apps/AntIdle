# Handoff

## Last verified: 2026-11-15 — optional colony milestone research

- PR #75 is confirmed merged. New branch `feature/colony-milestones` starts at
  main `fa72170`; this task adds optional content after that merge. The owner authorized a PR and auto-merge for this feature on November 15.
  Main remains fa72170; required test/classifier checks and squash-only auto-merge
  settings were reverified. Check GitHub for the live PR/merge state.
- Three paid ordinary upgrades: Scout Wayfinding after 20 completed expeditions
  (−20% new scout reveal/study duration), Nurse Training after 25 standard workers
  raised (+50% Brood Nurse care), Queen's Rhythm after 50 (−20% laying interval).
  Normal research prerequisites also apply. Prices/effects are in MILESTONES.md.
- Upgrades shows one compact upcoming goal with progress, effect and full price.
  Reached goals reveal standard research actions; purchased history stays standard.
  These are current-colony upgrades, not inherited Understandings. No new save
  fields, currency, recurring notifications or forced progression purchases.
- Goals use current-run statistics. workersMatured includes small workers, so
  standard births subtract smallWorkersMatured. Departures/cooldowns already in
  progress keep their timing. New timing and previews share engine calculations.
- Final validation: 283 engine tests and 4 balance tests passed; fastOptJS,
  fullOptJS and balance/fastOptJS passed with zero Closure errors/warnings.
  Production CI browser gate passed at 390/1280px, including actual returns and
  births opening the new research. Production prestige gate passed at
  320/390/1280px. Upcoming-goal layout fit checks passed at 320/390/1280px.
- Two balanced-policy four-hour runs (seeds 42/137) bought all three upgrades in
  both colonies and reached two foundings with no starvation deaths or negative
  sampled resources. Optional spending made these scripted runs a few minutes
  slower than earlier runs; this is pacing evidence, not an enjoyment claim.
  Details/reproduction are in MILESTONES.md. No further balance claim is established.
- Preserve unrelated work/ and Python caches. Temporary logs/screenshots/reports
  under /tmp are not durable chat attachments. For an authorized later PR, verify
  required checks, queue native squash auto-merge, then finish without CI polling.
