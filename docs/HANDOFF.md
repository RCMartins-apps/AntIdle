# Handoff

## 2026-11-17 — egg-order caste icons

- `polish/egg-order-icons` starts at main 16e8444 (merged #91). Owner requested
  compact worker/scout counts in Egg orders. PR #92 is merged on main; this branch still starts before its map changes.
  Screenshot workflow docs are separate in PR #93. Integrate their context when
  preparing a PR; no PR requested for this branch yet.
- Egg orders now groups worker icon + number and scout icon + number with space
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

- Icon-first follow-up: owner requested [icon] count to match the rest of the
  UI. Reordered both caste entries; development build and mobile capture checks
  passed, with worker/scout countdown states captured and updated view shown in
  chat. Diff check passed. No gameplay changes.

- Compact cancellation follow-up: red × in the summary row on mobile replaces
  the separate cancel row; desktop retains its text label in that row. Accessible
  name/title and 44px minimum target remain, disabled when queue is empty.
  Development build and brood layout checks passed at 320/390/1280px, including
  target geometry, cancel behavior, stable actions and desktop label. Mobile
  screenshot shown in chat; syntax/diff checks passed.

- Raise-button follow-up adds live queue / brood / adult badges for each caste,
  with icons, tooltip labels and accessible descriptions. Worker badges follow
  the small/standard phase and count matching brood; scout badges are separate.
  Button accessible names remain stable. Development build and brood checks
  passed at 320/390/1280px, including immediate worker/scout click updates,
  cancellation and founding adult counts. Mobile screenshot shown in chat.
  Python syntax and diff checks passed; no gameplay changes.

- Raise-button info alignment now uses a two-column grid: help is centered
  against the full button (including badge), costs/details span both columns.
  Scoped to badge-bearing raise actions; native disclosure state remains intact.
  CSS-only follow-up passed brood checks at 320/390/1280px, including center
  alignment, separation, open/close and stable control positions. Updated mobile
  screenshot shown in chat; Python syntax and diff checks passed.
