# Handoff

## 2026-11-17 — touching exploration hexagons

- `polish/touching-map-hexes` starts at main 5cc539f, merged #90. Bottom sub-tab
  trial is separately in PR #91 with auto-merge enabled. Owner requested this
  map UI fix; implementation/push authorized, no new PR requested yet (D58).
- MainForm derives regular flat-top tile dimensions from one width helper and
  sqrt(3)/2 height, sharing dimensions with CSS through map-level variables.
  Positions use axial q/r: 3/4 width horizontally, one height per row with half
  height per column. No integer truncation in expanded-map placement.
- Normal width is 36% and expanded width 27%, keeping both current ranges inside
  their square board with >=44px targets. A non-interactive SVG outline keeps
  shared boundaries visible. Existing fog, status, selection and missions remain.
- Browser geometry helper verifies regular proportions, containment and both
  shared vertices for every axial-neighbor edge. Maintained map/prestige suites
  checked both ranges at 320/390/1280px, including unchanged map/trails and
  prestige flows. Production UI passed 390/1280px. Development/production builds
  passed with zero Closure warnings/errors; four packaging tests and syntax/diff
  checks passed.
- Preserve unrelated work/ and Python caches. No engine or save-format changes.
