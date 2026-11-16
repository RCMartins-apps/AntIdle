# Handoff

## 2026-11-16 — stable resource timing layout

- `fix/stable-resource-timing` starts at main c20a295 (merged #88). Owner requested
  fixing resource-panel resizing and explicitly authorized a PR with auto-merge.
- Cause: compact resources used content-sized wrapping flex cells; timing warnings
  changed their widths and were display:none when empty. Full rows also lacked
  reserved timing height. Fixed three-column summary tracks and two-line timing
  slots keep existing resource/cell geometry stable. Resource reveals still add rows.
- Compact summary now projects full as well as empty forecasts, abbreviating the
  visible text while retaining the complete estimate in accessible labels/titles.
  Only depletion is red. No stale estimate latching or gameplay/rate changes.
- Added repeated full/empty/zero and varying-number-width browser checks, including
  collapsed/expanded phone panels and desktop rows. Existing mobile resource,
  catch-up and reveal tests remain. Development/production builds passed with
  zero Closure warnings/errors; resource geometry/behavior passed 320/390/1280px,
  production UI passed 390/1280px, offline/update checks passed 390/1280px.
  Four packaging tests, JS/Python syntax and git diff --check passed. Final edits
  were documentation only. No engine changes or additional engine tests needed.
- Preserve unrelated work/ and Python caches. Required test and changes / classify
  must remain enforced before enabling native squash auto-merge.
