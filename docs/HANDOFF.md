# Handoff

## Last verified: 2026-11-07 — preserve staffing through aging

- Branch `fix/aging-job-replacement` starts from merged main `6a23158` (PR #41).
- Fixed reverse task-list depletion after Queen's Routine is unlocked. Home
  aging uses idle/default workers first, preserving other staffed jobs whenever
  the pool can cover losses. Cohort expiry and actual population losses remain.
- If the pool is exhausted, save aging-specific job vacancies and refill them
  as workers mature, return or become free. This maintenance is part of the
  Queen's Routine unlock, independent of its automatic-egg toggle. Idle workers
  are used when no default is selected. Standard workers can fill aging small
  worker jobs; spare small collectors can fill supported jobs. Shared caps apply.
- Never borrow busy expedition/carrier/territory crews or reset expiry ticks.
  Starvation/combat do not create aging replacement requests. Job edits cancel
  that role's pending request; changing the default drops its quota. Trail
  recall/depletion cancels trail requests. A compact conditional list and Cancel
  replacements button appear under Queen's Routine only while jobs are waiting.
- Verified: 221 engine tests (10 new staffing tests), development and production
  builds; new browser regression at 320/390/1920px covers default pool, saved
  vacancies, actual nurse replacement through automatic brood, cancellation,
  small-to-standard job replacement and layout. Existing lifecycle browser
  regression also passes at 320/390/1920px. Final builds and all 221 tests pass.
- Existing saved assignments with jobs already emptied cannot be reconstructed;
  this preserves staffing against future aging losses. Old saves default to no
  pending requests. README and D18 document the behavior.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
