# Handoff

## 2026-11-17 — egg-order caste icons

- `polish/egg-order-icons` starts at main 16e8444 (merged #91). Owner requested
  compact worker/scout counts in Egg orders. PR #92 is merged on main; this branch still starts before its map changes.
  Screenshot workflow docs are separate in PR #93. Integrate their context when
  preparing a PR; no PR requested for this branch yet.
- Egg orders now groups number + worker icon and number + scout icon with space
  between them. Brood places stays a separate column. Scout counts retain the
  existing unlock visibility. Founding workers have a small-worker accessible
  label; all icon counts have accessible names and hover titles.
- Development build passed. Brood browser checks passed at 320/390/1280px:
  correct counts/icons/accessibility, same-line spacing, stable actions through
  queue/status/cancel changes, founding visibility and disclosure persistence.
  Python syntax and diff checks passed. No engine or save-format changes.
- Follow-up adds the actual next worker/scout caste icon in the laying countdown,
  with a hidden accessible caste name. Related presentation polish is explicitly
  proactive in AGENTS.md; engine queue and timing rules remain unchanged.
  Development build and updated brood checks passed at 320/390/1280px for
  worker-first/scout-first queues and unchanged blocking states. Both mobile
  countdown screenshots were captured and shown in chat. Syntax/diff passed.
- Implementation is ready; no PR requested for this change yet. Preserve
  unrelated work/ and Python caches.
