# Consistency audit — 2026-11-14

Focused on engine/UI agreement after the Shared Paths rework, with adjacent
checks of royal care, construction queues, resource visibility, upgrades and
save reconciliation. This is a targeted pass, not an exhaustive game review.

## Confirmed and fixed

- Trail requirements previously described the current crew while +5 eligibility
  checked the next crew. A 10-worker trail could show green 1/1 scouts while its
  add button needed two. Counters now show the next crew's scout quota and the
  five workers needed, using the same availability calculation as the engine.
- Growing from five to ten workers could bypass a missing guide, because the
  incremental guide requirement was zero. Expansion now validates the full
  resulting quota after other trails' reservations. Removal remains possible.
  Follow-up: guide losses now shrink crews and return excess workers instead of
  retaining a paused crew; see COLLECTION-TRAILS.md.
- Royal-brood forecasts omitted Dew Gatherer water that the tick engine produces
  before royal care. An empty water store could therefore predict blocked care
  while care progressed. Forecasts now include that same-tick income, also
  correcting the water rate estimate that subtracts forecast care costs.

## Scope and verification

No resource prices, production rates, unlocks or save formats changed. Territorial
contest crews remain a separate mechanic; their limits were not retuned here.
No additional confirmed player-path bug was found in the adjacent checks above.

Regression coverage includes guide thresholds, guide loss, reservations across
routes, ten successive royal-care predictions at an empty water store, and
mobile/desktop red requirement counters matching disabled buttons. See HANDOFF
for the completed validation and branch status.
