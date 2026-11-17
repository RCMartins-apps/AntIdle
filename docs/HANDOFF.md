# Handoff

## 2026-11-17 — screenshots for visual changes

- `docs/visual-change-screenshots` starts at main 2c8cfd2, merged #92.
  AGENTS.md now requires actual rendered screenshots in chat for every visual
  change, mobile first and other affected states/layouts as needed (D59).
  If capture/delivery is unavailable, report it; local file links do not suffice.
- Documentation-only change; diff/wording checks passed. No game rebuild needed.
  Owner authorized commit/push by standing workflow; no PR requested yet.
- Pending separate work: `polish/egg-order-icons` at a56cc47 is pushed and ready,
  with no PR requested. It groups worker/scout egg orders with caste icons.
  Development build and brood layout checks passed at 320/390/1280px, including
  counts/accessibility, spacing, stable controls and founding visibility. Its
  earlier handoff predates merged #92; combine context when merging that branch.
- Preserve unrelated work/ and Python caches. No gameplay or save changes.
