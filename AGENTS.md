# Working on Ant Idle

## Start here

Before planning or editing, read these small context files:
1. [Game direction](docs/VISION.md): agreed identity and long-term goals.
2. [Design decisions](docs/DECISIONS.md): constraints and the reasons behind them.
3. [Handoff](docs/HANDOFF.md): last verified state and unresolved work.

Then inspect `git status --short`, the current branch, and recent commits. Verify
handoff claims against the checkout and relevant PRs before relying on them.
Read relevant README sections and source files as needed; do not load the whole
history or all tests at startup. Chat history is supplementary context.

The user's current instructions take precedence. If they change a recorded
decision, update its entry. Distinguish approved direction from implemented
behavior and tentative suggestions. Do not implement a roadmap item merely
because it appears in these files.

## Code map

Scala.js 2.13 with Laminar UI, sbt, zio-json saves and MUnit tests.
Source paths below are relative to `src/main/scala/pt/rcmartins/antidle/`:

- `model/`: immutable game state, costs, chambers, upgrades and statistics.
- `game/TickUpdater.scala`: simulated game ticks and progression.
- `game/TickCalculator.scala`, `ResourceTiming.scala`: net resource rates and visible full/empty estimates.
- `game/Actions.scala`, `game/Utils.scala`: UI action dispatch and reactive state.
- `game/Constants.scala`, `game/ChamberBonuses.scala`: shared balance values and chamber effects.
- `game/*Progression.scala`, `Foraging.scala`, `AphidHusbandry.scala`,
  `Construction.scala`, `PopulationAging.scala`, `AutomaticBrood.scala`: mechanics.
- `game/BroodProgression.scala`: founding/standard brood, ordered laying, feeding, nurses and small-worker jobs.
  Fresh `AntsData.initial` starts small; missing legacy caste fields retain standard workers.
  Scout orders store worker-order counts ahead of them for compact shared FIFO; `ScoutProgression` handles acceptance versus actual laying.
- `game/AgingJobReplacement.scala`: saved old-age job vacancies, pool refill and manual cancellation.
- `game/EmergencyRecovery.scala`: one-worker recovery after collapse and protected queen provisions.
- `game/AphidHusbandry.scala`, `AphidRelocation.scala`: housed aphids, ten per tender, starter group and reserved relocation missions; see docs/APHIDS.md.
- `game/ColonyMilestones.scala`: permanent achievement tiers, exact sugar multiplier and saved resource peaks.
- `game/MilestoneResearch.scala`: current-colony goal previews and shared research effect values; see docs/MILESTONES.md.
- `game/Prestige.scala`: founding resets, DNA purchases and inherited production.
- `game/PheromoneTrails.scala`: safe finite collection automation, per-route staffing/guide reservations and supply costs; see docs/EXPLORATION.md#shared-paths-collection-automation.
- `game/TerritoryProgression.scala`: held crews, pending hauls, recurring contests and withdrawal.
  Task upkeep and `agesAtHome` are separate: holders consume sugar with cohorts reserved at the site.
- `game/balance/UpgradeBalance.scala`: upgrade prices, help and structured reveal prerequisites; see docs/BALANCING.md.
- `game/UpgradeResearch.scala`: shared visibility/purchase validation for UI, actions and simulator.
- `game/ExplorationMap.scala`: handcrafted first ring, scout-only reveals/studies, finite Knowledge/deposits; see docs/EXPLORATION.md.
- `game/ExplorationDestination.scala`, `ExplorationHelper.scala`, `ExplorationProgression.scala`: finite collection, cargo reservations/overflow, mission slots and supplies. Save version 7 removes XP; old saves intentionally rejected.
- `game/UpgradeVisibility.scala`, `ResourceVisibility.scala`: visibility reconciliation.
- `game/saves/`: serialization, restoration and load-time reconciliation.
- `tools/balance/` (repository root): seeded real-engine pacing runner, policy config, portable reports and checkpoint saves; see its README.
- `game/UnlockUtils.scala`, `GameTransition.scala`: pure unlock and post-action rules shared by browser and balance runner.
- `game/ActivityStatus.scala`, `MapFeedback.scala`: compact presentation states and map mission progress.
- `game/EventLog.scala`: bounded compatible string history and routine report grouping.
- `main/UiPreferences.scala`: browser-only disclosure/map choices, separate from game saves.
- `main/MainForm.scala`: game UI; responsive styling is in root `index.html`.
- `assets/ui/`: progressive mobile navigation and compact resource projection.
  Keep existing game controls/rows as the source of truth; do not duplicate engine
  calculations here. Browser checks cover grouped views and resource disclosure.

Resources use fixed-point integers: `Constants.u = 10000`. Simulation runs at
`Constants.TicksPerSecond = 5`. Check units when changing counts, costs or rates.
Keep engine rules, displayed costs, previews and income estimates consistent.
Resource/population visibility is current-colony history: keep unlocked rows
visible through spending or losses, but reset them on founding. Lifetime
statistics, milestone bonuses and lineage do not reveal current-run resources.
Use current research/worker/chamber requirements for action visibility.
Validate gameplay rules in the engine, not only by disabling UI buttons.

## Offline packaging

BuildInfo is generated by build.sbt at compilation from Git HEAD and tracked
dirty state. Keep the visible build ID tied to the running bundle, distinct from
AllData.CurrentVersion (save schema) and the service worker release digest.

`python3 scripts/package_site.py --out NEW_DIRECTORY` packages fullOptJS for
production, including local browser libraries, manifest/icons and a release-scoped
service worker. Use this same script in deployment and offline tests. Source-tree
pages intentionally do not register workers, keeping dev bundles fresh. Never
force a reload/update over a running colony or delete local saves when cleaning
release caches. See docs/OFFLINE.md and tests/offline_browser_smoke.py.

## Balance maintainability

Make tuning straightforward for the owner, especially unlock timing and
prerequisites. Prefer discoverable named balance values and shared predicates;
keep visibility, engine eligibility and displayed requirements consistent.
Distinguish visibility from affordability. Avoid duplicating balance numbers in
UI prose or introducing a generic framework without a concrete need. Automated
pacing results support human playtests; they do not measure the 80% enjoyment
aspiration. See D33–D34 and tools/balance/README.md.

## UI consistency, copy and information

Before editing UI, find the closest existing screen or control and reuse its
markup, shared styles and icons. Match disclosure markers, spacing, type,
alignment, touch targets, labels, icon order and number formatting for the same
kind of control. Keep native disclosure arrows on ordinary collapsible sections,
including when their headings need custom alignment. Reuse `ResourceIcons` and
the existing number formatters. Display at most two decimal places and keep
exact arithmetic in the engine. If a repeated pattern needs to change, update its
shared implementation and check the other places that use it.

Before handing off a visual change, compare it with its closest counterpart in
the rendered game at phone width. Check the relevant states together, such as
open and closed, enabled and disabled, or with and without a count. Fix visible
mismatches in the same change.

Proactively apply small, related UI consistency and clarity improvements within
the requested area, using existing patterns and icons. Do not require repeated
owner feedback for obvious presentation polish. Keep gameplay/balance decisions
out of that scope unless authorized. Show actual screenshots for visual changes
in chat, mobile first; report capture/delivery limitations instead of giving
local file links.

Keep default screens compact. Show counts, rates, costs, progress and relevant
blocking/risk warnings directly. Remove explanations that repeat those values.
Describe base/current behavior concisely. Do not append obvious claims such as
“improved by [upgrade]” or enumerate hypothetical upgraded variants in general
help. Upgrade actions still need their concrete effects and costs.
Put important mechanics behind explicit [i] controls on phone and desktop;
use MainForm's infoHeading/infoDetails helpers for sections/tasks and the shared
action details control for actions. Do not add always-visible tutorial paragraphs
with new features. Keep ongoing material costs and danger disclosures visible.

Related action descriptions must use the same fields in the same order. Use a
shared renderer for action families (egg actions use eggDescription), derive
numbers from gameplay data, and distinguish upfront costs, later care, required
capacity and result. Do not hand-write unrelated paragraphs for sibling actions.
Chamber action help contains numeric bonuses only, without prose descriptions.
Use consistent capacity/place/slot names and include derived effects (Nursery
care places and Queen laying interval) without applying them twice to saved
capacities. Expedition help explains finds/chances without repeating the visible
duration/reward summary.
Disabled action feedback uses ActionBlockers with the same live costs and
requirements as the card. Preserve queued egg/chamber affordability semantics;
above-capacity retained resources are still spendable.
The common detail layout orders explanation, Requires, then Result; omit empty
sections and keep costs visible beside the button.

Preserve disclosure/control nodes across live ticks. Project minimal visibility
and structural flags, then apply distinct before creating their DOM. Bind live
costs, rates, levels and labels inside that stable structure; do not recreate an
entire section for unrelated model updates and reset its open/focus state.
Persist native disclosures from open-attribute observation, rather than deferred
native toggle delivery; disconnect observers when their elements unmount.

## Verification

- `sbt fastOptJS`: development build; serve the repo with `python3 -m http.server 8000`.
- `sbt fullOptJS test`: production build and engine tests, matching PR CI.
- `sbt scalafmtCheckAll balance/scalafmtCheckAll scalafmtSbtCheck`: required
  formatting checks for game, balance, test, and sbt sources.
- After `fastOptJS`, run the relevant `tests/*_smoke.py` for UI changes;
  setup and coverage are documented in README and tests/README.md. Use the maintained map/CI gates; older XP/carousel harnesses are historical. Check phone and desktop layouts.
- Choose checks for the change. Documentation-only work needs link/path checks
  and `git diff --check`, not a game rebuild. Report checks actually run.
- Run balance checks when content, progression, or economy changes warrant them.
  For presentation-only changes, prefer the focused build and browser checks.
- Inspect narrow-screen screenshots for icon and text baselines, aligned
  controls, wrapping, and nested padding before handing back UI changes.

For every visual/UI change, capture and show screenshots of the implemented
result in chat before handing it back for review. Use a representative mobile
viewport by default; add desktop or other states when the change affects them.
Show the affected area clearly with realistic game state, rather than only a
written description. Use actual rendered-game screenshots, not generated mockups.
Screenshots supplement the relevant checks. If capture or chat attachment is
unavailable, explicitly report that limitation; never claim a screenshot was
shared or substitute a local filesystem link that the owner cannot open.
Documentation-only and nonvisual changes do not require screenshots.

Use the environment's sbt installation; do not commit machine-specific tool paths.
Do not commit generated builds, browser caches or scratch screenshots. Preserve
unrelated local files. Screenshots in a temporary workspace are not durable chat
attachments; use the interface's supported attachment mechanism when available,
and do not promise a local file link will work on the user's device.

## Preserve context as part of the work

- Update `docs/DECISIONS.md` when the user establishes or changes a lasting rule.
- Update `docs/VISION.md` only when the agreed product direction changes.
- Update relevant README sections when implemented behavior or setup changes.
- Before handing off substantive work, replace the dated snapshot in
  `docs/HANDOFF.md` with verified state, checks, unfinished work and uncertainties.
  Keep it short; use Git/PR history for the full change log. Do not repeat an
  unchanged handoff or append tool transcripts.
- Keep suggestions explicitly tentative. If intent is missing, inspect these
  files and related code first, then ask only if the missing choice matters.

## Quiet progress and CI handoff

Keep explicit job/step runtime limits in repository workflows; see
[CI limits](docs/CI-WORKFLOW.md#runtime-limits-and-superseded-runs). Reusable callers
use the timeout on the called runner job. Preserve required check identities.

The owner prefers updates only for blockers, failures, decisions requiring input
and completion. Avoid repetitive “still waiting” CI/build messages. If higher-level
platform instructions require periodic updates, keep them brief and factual; this
repository preference cannot override those instructions.

When the owner authorizes PR creation and merging, prefer GitHub native auto-merge
(squash) after local validation and PR creation, provided required CI checks are
actually enforced for main. Verify that protection/rulesets require `test` and
`changes / classify`; allowing auto-merge in repository settings alone is not enough.
See [CI handoff setup](docs/CI-WORKFLOW.md). Do not change protection requirements
or bypass checks merely to queue a merge. If setup is missing, report it and use
normal checked merging for an explicitly authorized merge request.

Check the label on every issue addressed by a PR. If any issue or the PR
itself carries `needs-manual-testing`, leave auto-merge disabled and wait for
the owner's explicit approval of the latest revision before merging. Share
the playable preview when available. If the branch changes after approval,
ask the owner to approve the new revision. For other PRs with no hold, use
the checked auto-merge process above.

Whenever creating or handing off a PR, include both its GitHub link and its
playable preview link: https://rcmartins-apps.github.io/AntIdle/pr-preview/pr-N/.
The first line of every PR description must be its Markdown preview link, using
the actual PR number, followed by its availability status. After creation, update
the description immediately once GitHub assigns the number; never guess it.
For ineligible docs-only/fork PRs, keep the preview link first and explicitly say
no preview is generated.
Label a preview pending until deployment is confirmed; do not poll CI just to
make it live. For docs-only/fork PRs explain that no preview is generated; after
merge/closure explain that the preview is removed rather than presenting it as
live. This link requirement supplements screenshots for visual changes.

When scheduling is available, schedule a one-time follow-up about 15 minutes
after creating each PR. Schedule it even if auto-merge is unavailable or the
PR is on hold. Include the PR link and any merge or hold instructions.
Use the follow-up to check merge and CI status, diagnose failures, and resume
authorized work when possible. While CI runs, start the next independent ready
task or end the turn. Report the PR status and whether the follow-up was
scheduled. If scheduling is unavailable, report that and continue available work.
Report an immediate merge only once GitHub confirms it.

## Ready issues and blockers

- Read repository issue labels. Start with the highest-priority open issue
  labeled `ready` when choosing backlog work. Rank `priority:high` before
  `priority:normal` before `priority:low`; use issue number to break ties.
  Follow any direct instruction from the owner first.
- When CI runs, follow the scheduled handoff above. For a dependency, an
  approval, or another blocker, record
  what is needed to resume and start the highest-priority ready task that can
  proceed independently. Branch it from current main. Return to blocked work
  when it can advance and bring overlapping branches up to date before merge.
- The owner authorizes a PR with checked auto-merge for each finished task.
  Use a separate PR per task, link its issue with `Closes #N` where applicable,
  and follow the validation, preview and follow-up rules above. Respect explicit
  requests to preview or hold a PR.

## Branch and push by default

The owner has given standing authorization to create branches, commit and push
project changes to `origin`, including documentation-only design decisions.

- Start each distinct development task or lasting design decision on a new,
  descriptive branch from up-to-date `origin/main`. Continue its existing branch
  for related follow-ups; do not create a branch per message, file or tool call.
- Keep diffs scoped to the request. Avoid unrelated formatting and refactors.
  Branch independent changes from main so a failed check blocks only its PR
  and changes that depend on it.
- Discussion that establishes or changes a lasting decision is work: record it
  in the appropriate context file, even when no gameplay code changes. Casual
  conversation and unadopted suggestions do not require empty commits or branches.
- Before ending a turn that changes tracked project files, update relevant
  context, run appropriate checks, commit the intended changes and push the
  branch. Push subsequent commits on continuing work too. Do not wait for a
  separate "push" request. Preserve unrelated local work and exclude artifacts.
- Completion is not required for a backup: if work is unfinished, commit and
  push a clearly labeled checkpoint on the task branch before handing back.
  Record what remains, known failures and checks not yet run in HANDOFF; do not
  present a checkpoint as tested or ready to merge. For long tasks, also push
  meaningful intermediate checkpoints when practical. Continue toward completion
  after saving; checkpointing is not a reason to stop an actionable task early.
- Report the branch and push result. If blocked, identify what remains local;
  never describe an unpushed commit as remotely preserved.
- This authorizes ordinary pushes to work branches. It does not authorize
  force-pushes, direct pushes to `main`, branch deletion or manual deployment.
  Production deploys only from main. Same-repository PRs publish isolated
  previews after validation; `debug*` branch names have no special behavior.
- A pushed branch is remotely saved, but new checkouts of `main` receive its
  context only after merge. Keep pending work discoverable in the handoff and
  inspect relevant remote branches when resuming unfinished work. Open the PR
  after the task is finished and validated.
