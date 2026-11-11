# First-prestige playtest follow-up — 2026-11-11

Owner completed a full opening-to-first-prestige run. This is the proposed delivery
sequence, not a claim that these changes are implemented. Keep each PR independently
reviewable and test phone/desktop behavior. Do not introduce balance changes into
layout-only PRs.

## 1. Ship the existing egg-order fix

`fix/egg-order-capacity` (784669b) already separates ordering from laying. Main
still points to offline PR #57 and does not include this fix. Open its PR, run
production CI and merge when authorized. Verify the deployed build before treating
the live report as a new regression. Test zero sugar/protein, mixed worker/scout
orders and housing-full disabling. Keep costs visible; actual laying waits for
resources. Do not rewrite this fix on a second branch.

## 2. Fix task selection and irrelevant collection controls

Preserve the actual selected default task when new task options appear. Current
select rebuilds option children on choices changes but its value stream is distinct
on selected task alone; investigate option replacement resetting the native select.
Test selection visually and in saved state through an unlock, tick and reload.

Show collection crew controls only where the selected site has a discovered,
collectible deposit. Keep an exhausted-site state distinct from storage/crew/slot
blockers so the UI does not hide why an available mission cannot launch. Test
unrevealed, revealed-but-unstudied, collectible, exhausted and storage-full sites.

## 3. Compact the Nest on mobile

Hide starter-worker controls when no living small workers remain; retain access
if existing small brood matures later. Do not hide normal brood controls or a
pending small-worker brood status accidentally.

Make the entire ordinary Brood section collapsible, including routine, orders,
action amount and egg actions. Preserve open state across ticks; keep a concise
summary when collapsed. Show the build queue only in Nest on phone widths, while
preserving current desktop access. Avoid duplicate queue controls/subscriptions.
Test zero/surviving starter ants, collapsed ticks/reload behavior and both layouts.

## 4. Simplify Explore controls and relocate research

Replace the scout dropdown with a touch-friendly three-button 1 / 2 / 3 selector
below the map. Preserve selection through ticks/site changes and expose crew costs
and blocked requirements clearly. Keep selection and launch eligibility separate.

Move Expedition Supplies research to Upgrades using the same engine rules,
prices, rank and reveal condition. Keep the current haul-capacity effect; do not
silently convert the saved progression to unrelated upgrade IDs. Check rank/cost
updates, maximum rank, save/reload and no duplicated purchase entry in Explore.

## 5. Clarify royal brood and nurse care

Remove the nurse staffing prerequisite from starting royal brood, keeping royal
nursery capacity, housing and actual egg costs. With no nurses, royal brood waits
without losing progress. Explicitly show `0/5 Royal Nurses` and `Needs Royal Nurse`
when relevant instead of the ambiguous ant icon `0/1`. Keep actual feeding costs
and other capacity requirements visible. Test starting without nurses, no-care
waiting, assigning/removing nurses, resumption, persistence and founding eligibility.
This changes gameplay eligibility and belongs in its own PR, not a styling patch.

## 6. Explore visual redesign — proposal to prototype separately

Owner suggests a canvas map with icons. First try SVG terrain/resource icons in
the existing hex map, with clear fog/available/exhausted/selected states and a
compact selected-site panel. SVG supports scalable phone layouts, accessible
labels and ordinary interaction without canvas hit testing and manual focus.
A canvas implementation remains an option if the prototype shows a concrete need;
it is not yet a chosen requirement. Keep the finite map economy, handcrafted ring
and scout/worker roles unchanged. Compare phone screenshots before implementation
is considered final; retain accessible keyboard controls and adequate touch targets.

## Verification across the sequence

Engine regressions for eligibility/cost/care changes; maintained browser checks at
phone and desktop widths for option changes, collapse stability, visibility,
selection and tab placement. End with a focused start-to-prestige playtest against
the deployed build, especially royal brood and resource-starved ordering. Do not
claim automated tests establish that the pacing is fun.
