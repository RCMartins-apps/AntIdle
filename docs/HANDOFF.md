# Handoff

## 2026-11-18 — worker icons and grouped mobile colony navigation

- `polish/worker-caste-icons`, PR #99, starts at main 4a67020. Manual review only:
  auto-merge remains off. Owner approved S-marked small ants, colony Nest icon,
  icon Nest sub-tabs, moving Tasks into Nest, and primary Settings (D61–D62).
- Mobile Nest now groups Brood/Tasks/Chambers with egg/worker/tunneling icons
  above labels. Settings has a main gear button, replacing the hamburger. A lone
  Settings strip hides; Milestones stays under Settings before Lineage unlocks.
  Escape returns to prior view. Desktop tabs and gameplay unchanged.
- Decorative icons preserve live task-label node parents. Captured mobile arrow
  handling prevents Bootstrap selecting hidden desktop tabs. Existing unlocks,
  remembered sub-views/scroll and idle-count updates remain functional.
- S is orange on light surfaces and pale gold in blue badges. It is reused in
  resources, task headings, orders, countdown and caste-dependent adult badges.
- Production build passed, zero Closure warnings/errors. Final navigation checks
  passed 320/390px, including milestone fallback and desktop resizing; task
  checks passed 320/390/1280px; production UI gate passed 390/1280px. JavaScript/
  Python syntax and diff checks passed. Updated mobile screenshot shown in chat.
- Preview: https://rcmartins-apps.github.io/AntIdle/pr-preview/pr-99/ — refresh
  pending push/CI. Preserve unrelated work/ and Python caches.
