# Handoff

## 2026-11-19 — continuous expanded resource panel

- `fix/mobile-resource-expansion`, PR #100, is open with auto-merge off for
  manual preview. The owner requested the expanded panel use its header color
  throughout, appearing as one block. Mobile CSS applies the existing card-cap
  color to the panel and removes the transparent header's bottom divider.
- Expanded view still replaces compact chips; real tab/sub-tab changes collapse
  and remember the panel. Same-view clicks and desktop behavior are preserved.
  Catch-up controls stay independent. No gameplay/save changes.
- Resource browser checks passed 320/390/1280px, verifying the matching header
  background, transparent inner surfaces, no divider, timing stability, saved
  expansion, navigation collapse, catch-up and resize. Updated screenshot shown
  in chat. Python syntax/diff checks passed; CSS-only, no rebuild needed.
- Previous prestige-test repair explicitly expands details before checking reset
  visibility; prestige/outer-map, offline and preview isolation checks passed.
- Preview: https://rcmartins-apps.github.io/AntIdle/pr-preview/pr-100/ — update
  pending push/CI. Preserve unrelated work/ and Python caches.
