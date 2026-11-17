# Handoff

## 2026-11-17 — bottom mobile sub-tabs and settings toggle

- `polish/mobile-bottom-subtabs` starts at main 5cc539f, merged #90. Owner requested
  this mobile trial (D57) and now authorized its PR with native auto-merge.
- Existing mobile sub-tabs/menu header now sits in a fixed 56px row above the
  68px main navigation, with safe-area-aware content padding. Desktop keeps its
  original tabs. Dense Explore sub-tabs remain visible without horizontal scroll.
- Settings button toggles open/closed with an expanded state and close icon.
  Closing/Escape restores the previous valid sub-tab and its scroll; choosing a
  main destination exits the menu. Hidden/removed prior destinations fall back to
  Nest. Tests' select_tab helper remains idempotent when already in Settings.
- Production build passed with zero Closure warnings/errors. Focused 320/390px
  checks passed for bottom geometry, full Explore group, toggling/Escape, scroll,
  navigation and desktop resize; the same checks passed with a wider Linux font.
  Production UI passed 390/1280px, map/trails and prestige passed 320/390/1280px,
  offline/update checks passed 390/1280px. Four packaging tests and JS/Python
  syntax/diff checks passed. Final edits were test coverage/documentation only.
- Required main checks test and changes / classify are active, strict and have
  no bypass. Squash auto-merge is enabled in repository settings. Preserve
  unrelated work/ and Python caches. No gameplay or balance changes.
