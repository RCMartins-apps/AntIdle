# Design decisions

This log preserves the owner's current decisions. IDs remain stable for existing
references. Use the [README](../README.md) and linked references for current
mechanics and the code and tests for exact values. See [Vision](VISION.md) for
the game's direction and [Ideas](IDEAS.md) for proposals.

## Colony and exploration

- **D1 · Ant biology.** Ground the game's jobs and trade-offs in ant behavior. Uplift can extend what the colony can learn and pass down.
- **D3 · Resource pressure.** Resources should create meaningful choices. Early sugar production has diminishing returns to make new food sources valuable as the colony grows.
- **D5 · Default task.** Requirements for idle standard workers draw from the selected default task. Workers released from commitments return to that pool.
- **D6 · Lifespans.** Save finite, randomized lifespans in cohorts. Expedition crews age while away and resolve overdue natural deaths on return.
- **D10 · Dry Clearing.** An outpost extends the current colony with its own water-related trade-offs. It does not start a separate run.
- **D14 · Danger.** Optional dangerous expeditions may incur substantial, clearly disclosed losses. Older ants have priority for selection, with random casualties from the actual party.
- **D15 · Territory.** Contests are optional. Holding and withdrawing crews should give the player choices about risk and labor.
- **D31 · Waste.** Tie nest maintenance to worker and space choices. See D71 for the implemented pressure and [Ideas](IDEAS.md#waste-and-colony-maintenance) for extensions.
- **D36 · Finite map.** Replace exploration XP with handcrafted hexes and finite Knowledge and resources. Early progression should work without lucky finds. Scouts reveal and study; workers collect. Keep enough accessible Knowledge for progression.
- **D40 · Supplies.** Expedition supplies are standard research upgrades. Purchased upgrades remain inspectable.
- **D42 · Shared Paths.** Automate finite safe collection with linear haul scaling, five-worker steps, and one scout per ten workers rounded up. Missing guides reduce the crew. Trails and manual expeditions share deposits. See [Exploration](EXPLORATION.md#shared-paths-collection-automation).
- **D43 · Aphids.** House ten aphids per Aphid Chamber level. One worker tends ten aphids. Relocation fills empty housing and the first chamber receives a starter group. See [Aphids](APHIDS.md).
- **D47 · Colony milestones.** Current-run goals reveal optional paid research. These goals and purchases reset on founding.
- **D48 · Permanent milestones.** Keep lifetime progress and resource peaks across foundings. Each earned achievement multiplies sugar production by 1.01. See [Milestones](MILESTONES.md).
- **D65 · Satellite Nests.** Give Dry Clearing specializations with distinct benefits, using water and existing castes. See [Prestige](PRESTIGE.md#satellite-nests).
- **D71 · Waste.** Larval feeding creates waste. Nursery level 2 introduces carriers, base storage, and repeatable Waste Chambers. Excess waste temporarily closes brood places. Existing brood develops and clearing the backlog reopens places.
- **D73 · Guard rotations.** Exchange some territorial harvest for protection. Keep the cost and benefit visible and give the player control over staffing.

## Brood, founding, and saves

- **D2 · Understandings.** Queens pass permanent Understandings to their daughters. Guide the first few foundings, then let players choose longer runs, purchases, or challenges.
- **D7 · Load reconciliation.** Recompute unlock visibility from the current save's prerequisites on load so old visibility flags cannot strand valid upgrades.
- **D8 · Prototype saves.** The owner accepts save breaks for substantial prototype redesigns. Keep compatible saves when feasible and handle incompatible versions explicitly.
- **D11 · Royal Nursery.** Use woodland resin as a construction material for the nursery.
- **D12 · Daughter queens.** Make rearing a daughter queen a distinct step toward founding, with royal brood and dedicated nurses. Laying can proceed before nurses are assigned; care needs staffing.
- **D13 · DNA.** Award DNA on founding and spend it on inherited Understandings. Introductory foundings also accelerate the restart through an inherited sugar boost.
- **D16 · Founding workers.** Begin with small workers at half output and upkeep and shorter lifespans. Standard Worker Development changes future brood; existing workers and brood keep their caste.
- **D17 · Recovery.** A collapsed colony must have a route back to one worker. Preserve protected queen provisions and avoid self-reinforcing shortages.
- **D18 · Job replacement.** After Queen's Routine, replace vacancies from old age using workers from the default pool. Save unfilled vacancies and respect manual staffing changes.
- **D20 · Shared egg queue.** Scouts can be ordered while the queen is busy. Respect order across castes and apply the selected brood batch amount to both.
- **D29 · Founding gates.** Restored visibility must never unlock chambers or actions before their current-run requirements.
- **D38 · Egg orders.** Orders reserve colony space without requiring current supplies. Charge supplies when laying begins. A full brood chamber can delay laying without blocking valid new orders.
- **D46 · Beyond the Woodland.** Open handcrafted outer hexes through a permanent Understanding. Founding presents the reward, Understanding choices, and final confirmation in order. Previewing spends nothing. See [Prestige](PRESTIGE.md).
- **D50 · Rediscovery.** Reset current-colony resource and caste visibility on founding. During a run, keep unlocked rows visible through spending or losses. Lifetime stats and inherited effects do not reveal rows early.
- **D66 · Dry Season.** Offer an optional challenge founding with its own completion reward. See [entry and exit rules](PRESTIGE.md#dry-season-challenge-founding).
- **D67 · Laying time.** The first egg takes time to lay, as later eggs do. The Queen Chamber improves the laying interval.
- **D70 · Brood Priority.** Let players shift nursery care between castes while keeping the laying queue separate.
- **D72 · Founding sugar.** Use founding provisions and worker production. Recovery must work without a repeatable manual Gather Sugar action.

## Player interface

- **D4, D21, D52 · Requirements.** Reveal actions when unlocked. Show costs and relevant blockers directly, using the same requirements as the engine. Use compact available/required counts and color the available amount when short. Flag requirements above maximum capacity.
- **D19, D55 · Resource estimates.** Show time to full or empty where current production supports an estimate. Smooth short-term changes in displayed rates, reserve room for status, and keep layout stable as digits change.
- **D22, D24, D25, D28 · Action details.** Put important facts up front and longer mechanics behind consistent info controls. Use the same fields for sibling actions. Chamber help lists numeric effects. Use caste-aware action names and keep Lineage separate. Keep action amount local to its screen and preserve touch target alignment.
- **D23, D26, D27 · Explore controls.** Keep selection, crew, duration, outcome, and launch close together. Mark remaining discoveries. The hex map replaced the earlier mobile carousel.
- **D39 · First-prestige polish.** Preserve the default job selection when jobs unlock, hide unavailable collection controls, and make scout count quick to select near the map.
- **D45, D56, D68 · Jobs.** List manual jobs before implicit roles. Use collapsible worker cards with caste totals and idle counts, aligned controls, and compact rate feedback. Hide the small-worker group once no small workers remain.
- **D49, D69 · Consistency.** Reuse established disclosure, activity, focus, progress, and numeric patterns. Keep text alongside status color, group routine events, and restore only safe UI preferences. Use at most two decimal places and stable mobile touch targets.
- **D51, D57, D61, D62 · Mobile navigation.** Use fixed bottom navigation and sub-tabs with consistent icons. Nest groups Brood, Jobs, and Chambers. Settings is a main destination. Distinguish small workers visually.
- **D53, D63 · Resource panel.** Remove the decorative calendar. Place Catch up with Resources. The expanded phone panel replaces the compact summary and closes when changing tabs.
- **D54, D75 · Brood layout.** Keep status and Raise actions visible. Put optional controls in a remembered, initially closed Brood options section below Raise. Stable status rows keep action positions steady.
- **D58 · Hex styling.** Keep adjacent tiles edge to edge and follow the hex outline for selection.
- **D64 · Purchased upgrades.** A purchased sub-tab shows its list directly. Expanded details span the row without moving the label or button.
- **Egg-order counts.** Use caste icon then count, including on the next-egg timer. Raise actions show queued, developing, and adult counts. Show the accessible cancel control only when there are outstanding orders.
- **Build queue.** Keep cancel separate from entry names. Show level transitions and sequential affordability estimates where feasible. Affordable entries build on the next tick. On phones the queue belongs on Chambers, and automation controls need usable touch targets.
- **Chamber order.** List founding infrastructure first, then Nursery and Waste, then Aphid, Replete, and Royal Nursery construction. Use the same relative order as choices unlock. Queue entries follow the player's build choices.

## Development workflow

- **D9 · Continuity.** Branch, commit, and push substantive work. Switch to the next independent ready issue when blocked, and open a checked auto-merge PR for each finished task. Schedule one follow-up about 15 minutes after opening each PR and move on while CI runs. See [AGENTS.md](../AGENTS.md#ready-issues-and-blockers).
- **D30, D32, D41, D44 · CI.** Changes limited to `docs/` or Markdown at any depth skip game validation and deployment. Keep production browser coverage focused, required check names stable, workflow runs bounded, and superseded runs canceled. Prefer checked auto-merge for authorized PRs and avoid repetitive waiting updates. See [CI workflow](CI-WORKFLOW.md).
- **D33, D34, D35 · Balance.** Use real-engine scenarios to measure pacing and human playtests to assess enjoyment. Make tuning easy to find, share unlock predicates, and keep visibility distinct from affordability.
- **D37 · Offline.** A complete online visit enables offline reopening. Browser installation is optional. Preserve saves through release updates and show the running build identity. See [Offline play](OFFLINE.md).
- **D59 · Screenshots.** Provide actual mobile-first screenshots for visual changes. Report capture or delivery limits.
- **D60 · PR previews.** Publish isolated previews for eligible PRs, replacing debug deployments. Put the actual preview URL first in PR descriptions and include its status and link in handoffs. See [CI workflow](CI-WORKFLOW.md#playable-pull-request-previews).
- **D74 · Colony Workshop.** The editor, game, and simulator share canonical catalogs. Model editable bonuses with typed effects and derive them from stable upgrade IDs or chamber levels. Upgrade prerequisites are ordinary research links. Validate edits before saving. See [Workshop](BALANCING.md#colony-workshop).

- **Parallel PR handoffs.** Keep task status, checks, and open questions in the PR description or linked issue. Update `docs/HANDOFF.md` in a dedicated PR when shared project context changes.
