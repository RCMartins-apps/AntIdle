# Handoff

## 2026-11-16 — mobile navigation, first of two UI passes

- `feat/mobile-bottom-navigation` starts at merged #83 (`05a25b3`). Owner approved
  both UI proposals in order, with separate PRs and native auto-merge; see D51.
- `assets/ui/mobile-navigation.js` enhances the existing Bootstrap tabs with five
  fixed SVG/icon-label destinations and grouped mobile secondary screens. Desktop
  uses the original panels/tabs. No duplicated game controls or gameplay changes.
  Nest separates brood/chambers; Explore groups map/discoveries/territory; Upgrades
  separates available/purchased; Lineage groups founding/milestones. Understandings
  stay in the founding wizard. Menu opens Settings and the mobile colony log.
- Existing reveal rules, stable DOM/disclosures and per-view scroll positions are
  preserved. Asset is automatically included by production packaging/offline cache.
- Development/production builds passed with zero Closure warnings/errors; four
  packaging tests passed. Mobile-specific and maintained browser checks updated
  to navigate through visible controls. Final validation results recorded in PR.
- Next authorized pass: compact expandable mobile resource summary, separate PR.
  Preserve unrelated work/ and Python caches. No engine tests needed for this
  HTML/CSS/browser enhancement; browser/offline checks cover the affected paths.
