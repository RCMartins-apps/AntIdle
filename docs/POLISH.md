# UI polish passes

The owner approved five separate PRs, in this order. Auto-merge is authorized for
each after local verification and enforced GitHub checks. Native auto-merge can
run while the following independent pass is developed; do not poll CI repeatedly.
Integrate current main before submitting each PR.

1. **Activity states (implemented):** shared compact status styling for brood, royal care,
   trails and construction. Waiting/paused states differ from resource blockers;
   queued construction names missing materials. Text accompanies color.
2. **Map feedback (PR #79):** distinguish unexplored, studying, collecting and exhausted
   sites; show active mission progress at its hex without hiding risks or costs.
3. **UI memory (PR #80):** preserve selected map site and collapsed sections across reloads,
   with safe fallback when an old selection is no longer unlocked.
4. **Event log (PR #81):** group routine reports and emphasize meaningful discoveries,
   research and losses without adding message spam.
5. **Visual consistency (implemented and verified):** align control/touch sizes, spacing, numeric presentation,
   progress and empty/completed states.

Only implemented passes are described as complete in HANDOFF. Browser scenarios
live in tests/polish_*_smoke.py; use the development bundle after fastOptJS.

UI memory is stored in `antidle.ui.*` browser keys, separate from game saves.
It covers brood, aging, resource/details, purchased research and milestone tiers.
Storage failures fall back to normal defaults. Saved map selection is validated
against current reveals/range, including during founding/import. Confirmation
checkboxes and irreversible-action drafts are never restored.

Event retention remains compatible with string-based saves: up to 20 significant
events, 30 routine reports and 10 notices. Collection/Knowledge/carcass reports
group by source/resource and show the latest text plus report count. Counts refer
to reports, not summed yields. Successful paid research records one completion.

The visual pass standardizes 44px controls, tabular resource digits, focus rings,
progress tracks and the empty construction queue. Resource numbers share one
K/M/B/T formatter; exact requirement values remain in titles/accessibility labels.
Game numbers display at most two decimal places. Values closer to zero than
one hundredth use a signed bound such as <0.01 or >−0.01. Royal brood
progress has an accessible label/value and is clamped to 0–100%.

Expanded map hexes show compact percentages with study/cargo/scouting symbols
during missions. Full action labels remain in accessible names and tooltips.
Trail/exhausted labels also stay compact to fit narrow hexes. The 320px expanded
map and royal progress accessibility are covered in polish_visual_smoke.py.

All five passes are implemented. PRs #78–81 are merged; the final visual branch
is `polish/visual-consistency`, authorized for a separate auto-merge PR. See
HANDOFF and GitHub for the latest verification/merge state.
