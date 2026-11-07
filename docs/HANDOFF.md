# Handoff

## Last verified: 2026-11-07 — action and queue feedback polish

- Branch `fix/action-queue-feedback` starts from merged main `6303814` (PR #44).
- Fixed Royal Egg colony-slot feedback ignoring queued worker/scout reservations.
  Worker/scout/royal acceptance and displayed slots share availableColonySlots;
  actual queued-head laying still uses its reserved place normally.
- Brood now reports Colony slots full when housing blocks actual laying. With
  no pending eggs, cooldown says Queen ready in rather than promising a next egg.
- Chamber actions show Build queue full / Already queued only when relevant.
  Construction.blockedReason supplies both eligibility and visible text, including
  duplicate Royal Nursery plans; cancelling a plan clears the feedback immediately.
  Resource shortages still allow construction plans; costs remain visible.
- Verified development/release builds and 238 engine tests (5 new feedback cases).
  New action_feedback_smoke passes 320/390/1920px: full and duplicate plans,
  cancellation, Royal Egg slot colors, housing blockage and cooldown wording.
  Existing management browser regression also passes at 320/390/1920px, covering
  construction costs/levels, waits, cancellation, completion and save/load.
- README and D21 record behavior. No save, balance or progression changes.
- Preserve unrelated untracked `work/` and `tests/__pycache__/`.
