# Handoff

## 2026-11-15 — final polish pass

- Owner authorized all five passes with separate PRs and native auto-merge. PRs
  #78 statuses, #79 map feedback, #80 UI memory and #81 event log are confirmed
  merged. Main ce07577 is integrated into `polish/visual-consistency`. Its game
  tree matches the tested event-log predecessor; squash-history conflicts were
  resolved by retaining the combined feature tree. No force pushes or deletions.
- Final pass: consistent 44px controls/focus, tabular digits, shared K/M/B/T
  formatting with safe signs/tiny values, exact requirement titles/accessible
  labels, consistent progress tracks, accessible clamped royal progress, and
  explicit empty construction queue. Narrow map activity uses icons/percentages
  with full accessible/tooltips; duplicate fog labels and report grammar fixed.
  Map feedback signals are shared across each hex's bindings.
- Before the last display-only fixes: 297 engine + 4 balance tests passed;
  development/production builds passed with zero Closure errors/warnings. All
  five scoped browser scenarios passed at 390/1280px, with expanded map and royal
  progress additionally at 320px. Production CI flow passed 390/1280; full
  map/trail/aphid and prestige gates passed 320/390/1280. Final copy/shared-signal
  compilation and browser checks are being completed before PR creation.
- CI hit the default ~0.9 GB Java heap on #78. Explicit 3 GB/G1 in .jvmopts fixed
  it and is merged. Timeouts and test/classifier requirements remain enforced.
  Existing map tests now match the new label and persisted purchase-history state.
- UI preferences are separate browser keys, not saves/confirmations. Event history
  stays compatible with string saves and independently bounds significant/routine
  messages. Logs also remember disclosures. No gameplay balance changes.
- Preserve unrelated work/ and Python caches. Temporary screenshots/logs under
  /tmp are not durable chat attachments. Open the final PR and enable squash
  auto-merge after validation; stop after queue confirmation without CI polling.
