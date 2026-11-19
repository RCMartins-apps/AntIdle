# Handoff

## 2026-11-19 — full-width purchased-upgrade descriptions

- `polish/purchased-upgrades-view`, PR #101: manual preview, auto-merge off.
  Already incorporates main dc89fb7 (#100).
- Purchased upgrade help now expands below its title/button at full row width,
  preserving their position and size. CSS grid includes native details-content
  wrapper handling. Mobile direct list and desktop history disclosure retained.
- Navigation regression passed 320/390px; focused full-width description and
  stable-control geometry checks passed 320/390/1280px. Python syntax and diff
  checks passed. Actual mobile screenshot shown in chat. CSS-only presentation;
  no engine/save changes or rebuild needed.
- Preserve unrelated work/ and Python caches.
