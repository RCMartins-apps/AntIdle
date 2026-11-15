# UI polish passes

The owner approved five separate PRs, in this order. Auto-merge is authorized for
each after local verification and enforced GitHub checks. Native auto-merge can
run while the following independent pass is developed; do not poll CI repeatedly.
Integrate current main before submitting each PR.

1. **Activity states (implemented):** shared compact status styling for brood, royal care,
   trails and construction. Waiting/paused states differ from resource blockers;
   queued construction names missing materials. Text accompanies color.
2. **Map feedback:** distinguish unexplored, studying, collecting and exhausted
   sites; show active mission progress at its hex without hiding risks or costs.
3. **UI memory:** preserve selected map site and collapsed sections across reloads,
   with safe fallback when an old selection is no longer unlocked.
4. **Event log:** group routine reports and emphasize meaningful discoveries,
   research and losses without adding message spam.
5. **Visual consistency:** align control/touch sizes, spacing, numeric presentation,
   progress and empty/completed states.

Only implemented passes are described as complete in HANDOFF. Browser scenarios
live in tests/polish_*_smoke.py; use the development bundle after fastOptJS.
