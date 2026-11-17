# Handoff

## 2026-11-17 — egg-order caste icons

- `polish/egg-order-icons` starts at main 16e8444 (merged #91). Owner requested
  compact worker/scout counts in Egg orders. PR #92 for touching map hexagons is
  separate, with auto-merge enabled; this branch does not include its changes.
- Egg orders now groups number + worker icon and number + scout icon with space
  between them. Brood places stays a separate column. Scout counts retain the
  existing unlock visibility. Founding workers have a small-worker accessible
  label; all icon counts have accessible names and hover titles.
- Development build passed. Brood browser checks passed at 320/390/1280px:
  correct counts/icons/accessibility, same-line spacing, stable actions through
  queue/status/cancel changes, founding visibility and disclosure persistence.
  Python syntax and diff checks passed. No engine or save-format changes.
- Implementation is ready; no PR requested for this change yet. Preserve
  unrelated work/ and Python caches.
