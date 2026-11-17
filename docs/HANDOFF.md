# Handoff

## 2026-11-17 — touching exploration hexagons

- `polish/touching-map-hexes` starts at main 5cc539f and integrates merged #91
  (16e8444), preserving bottom sub-tabs/settings behavior. Shared docs combined
  in order; code/styles merged cleanly. Owner requested this map UI fix;
  implementation/push authorized, no new PR requested yet (D58).
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
  checks passed. After integrating #91, both map ranges and navigation were
  rechecked at 320/390/1280px with shared-edge and containment assertions.
- Selection follow-up replaces the rectangular inset shadow with a blue SVG
  polygon border; keyboard focus uses a distinct dashed polygon. Maintained
  production map/trail/aphid checks passed at 320/390/1280px, including pointer
  selection and keyboard-focus assertions. Four packaging tests, Python syntax
  and diff checks passed. No new PR requested.
- Preserve unrelated work/ and Python caches. No engine or save-format changes.
