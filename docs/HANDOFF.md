# Handoff

## Last verified: 2026-11-11 — first-prestige playtest improvements complete

- Owner authorized implementing and merging the ordered playtest follow-up.
  Gameplay/UI PRs #58–#63 are merged after their final GitHub CI passed.
  docs/PLAYTEST-FOLLOWUP.md links each focused change; D38–D39 record the rules.
- Egg orders need future housing, not upfront sugar/protein. Actual laying still
  charges full costs and respects brood places/cooldown; FIFO and save data remain.
  The balance runner uses the updated capacity-only order API too.
- Default task selection survives option additions. Collection crew controls appear
  only for unlocked, non-exhausted routes, keeping storage/crew blockers visible.
- No-living starter workers hide their resource/task controls; later small adults
  can reveal them again. Entire ordinary Brood collapses and survives live ticks.
  A single build queue remains accessible across desktop tabs, only Nest on mobile.
- Scout crew uses 1/2/3 buttons below the map. Expedition Supplies purchases live in
  Upgrades with unchanged rank, prices and reveal rules. Royal laying no longer
  requires nurses; explicit Royal Nurse counts and waiting status explain care.
- Hexes use SVG terrain symbols, shorter labels and terrain colors; full names and
  mission information remain in the selected card. Fog hides unknown terrain;
  keyboard labels, selection and exhaustion states are retained. No economy/reset.
- Verified locally: 254 engine + 4 balance tests, fastOptJS, maintained CI/map
  browser functions at 390/1280px and map script at 320/390/1280px. Phone before/after
  screenshots inspected. Final GitHub production CI also passed for every PR,
  including packaging, browser and offline/update checks. No new save version.
- Context-only completion PR records this final state; docs-only checks suffice.
  No remaining implementation from this approved batch. Future canvas, new terrain
  mechanics or further rings would be separate work, not implied by the SVG pass.
- Preserve unrelated work/ and Python caches. Temporary logs/screenshots under
  /tmp/antidle-ui*, /tmp/antidle-map-browser are not durable user attachments.
- Offline caches require one complete online visit; installed/running old versions
  apply an update after all game windows close. Local saves are not cloud backups.
