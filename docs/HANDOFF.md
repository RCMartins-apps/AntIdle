# Handoff

## 2026-11-16 — mobile navigation and compact resource summary

- Owner approved two sequential UI passes with separate PRs and native auto-merge;
  see D51. Navigation branch `feat/mobile-bottom-navigation`, commit 3d212e4, is
  merged as PR #84 (`acf4c6f`). Main protection requires test and changes /
  classify, strict freshness and no bypass; squash is the only enabled method.
- First pass validated development/production builds (zero Closure warnings),
  four packaging tests, focused phone navigation at 320/390px and desktop resize,
  production UI at 390/1280px, map/trails and prestige at 320/390/1280px, and
  offline/update behavior at 390/1280px. No balance or engine changes.
- `feat/mobile-resource-summary` follows merged #84. It adds a compact live mobile header
  for introduced resource totals, preserving blue/bold full capacity and visible
  depletion warnings. Tap/keyboard activation expands the original complete panel
  with rates, capacities and estimates. Expansion preference is device-local;
  desktop keeps the complete panel. Optional resources follow source row visibility.
- Both enhancements live in assets/ui/ and are automatically packaged/offline cached.
  Resource-detail regressions use expand_resources; focused mobile checks cover
  default collapsed state, live values, reveal rules, warnings, memory and resize.
- Resource summary validation: focused resource/navigation checks passed 320/390px;
  production UI passed 390/1280px; map/trails and prestige passed 320/390/1280px;
  offline/update checks passed 390/1280px. Development/production builds passed
  with zero Closure warnings/errors; four packaging tests, JS/Python syntax and
  git diff --check passed. Final edits were documentation only. Owner authorized
  a separate PR and auto-merge. Preserve unrelated work/ and Python caches.
