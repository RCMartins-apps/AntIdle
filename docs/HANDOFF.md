# Handoff

## 2026-11-18 — repair brood UI PR #94

- `polish/egg-order-icons` integrates main 9b0f3e5 (#96), including isolated
  previews and the preview-aware required test gate. Documentation conflicts
  combined; source merged cleanly. PR #94 remains authorized for auto-merge.
- The failed check was blocked_actions_smoke.py expecting aria-describedby to
  contain only the blocker ID. Live count badges correctly add their own ID.
  Updated regression asserts both IDs, both existing descriptions and caste
  counts, rather than removing accessible badge information.
- Brood changes retain compact caste/order counts, next-caste countdown, red
  cancel button, live raise-button badges and centered info controls.
- Development and production builds passed, zero Closure warnings/errors.
  The exact failed production blocked-action check and production brood layout
  suite passed at 320/390/1280px. Python syntax and diff checks passed. No new
  visual changes; earlier screenshots show the intended UI. Auto-merge remains
  enabled with required test and changes / classify enforced; push reruns CI.
- #97 polish/safer-queue-controls is separate manual playtesting work. Leave it
  open without auto-merge. Preserve unrelated work/ and Python caches.
