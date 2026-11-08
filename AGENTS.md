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
- `game/Prestige.scala`: founding resets, DNA purchases and inherited production.
- `game/PheromoneTrails.scala`: finite nectar sources, guide reservations and trail workers.
- `game/TerritoryProgression.scala`: held crews, pending hauls, recurring contests and withdrawal.
  Task upkeep and `agesAtHome` are separate: holders consume sugar with cohorts reserved at the site.
- `game/UpgradeVisibility.scala`, `ResourceVisibility.scala`: visibility reconciliation.
- `game/saves/`: serialization, restoration and load-time reconciliation.
- `main/MainForm.scala`: game UI; responsive styling is in root `index.html`.

Resources use fixed-point integers: `Constants.u = 10000`. Simulation runs at
`Constants.TicksPerSecond = 5`. Check units when changing counts, costs or rates.
Keep engine rules, displayed costs, previews and income estimates consistent.
Validate gameplay rules in the engine, not only by disabling UI buttons.

## UI copy and information

Keep default screens compact. Show counts, rates, costs, progress and relevant
blocking/risk warnings directly. Remove explanations that repeat those values.
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
The common detail layout orders explanation, Requires, then Result; omit empty
sections and keep costs visible beside the button.

Preserve disclosure/control nodes across live ticks. Project minimal visibility
and structural flags, then apply distinct before creating their DOM. Bind live
costs, rates, levels and labels inside that stable structure; do not recreate an
entire section for unrelated model updates and reset its open/focus state.

## Verification

- `sbt fastOptJS`: development build; serve the repo with `python3 -m http.server 8000`.
- `sbt fullOptJS test`: production build and engine tests, matching PR CI.
- After `fastOptJS`, run the relevant `tests/*_smoke.py` for UI changes;
  setup and coverage are documented in README. Check phone and desktop layouts.
- Choose checks for the change. Documentation-only work needs link/path checks
  and `git diff --check`, not a game rebuild. Report checks actually run.

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

## Branch and push by default

The owner has given standing authorization to create branches, commit and push
project changes to `origin`, including documentation-only design decisions.

- Start each distinct development task or lasting design decision on a new,
  descriptive branch from up-to-date `origin/main`. Continue its existing branch
  for related follow-ups; do not create a branch per message, file or tool call.
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
  force-pushes, direct pushes to `main`, branch deletion, PR creation, merging or
  deployment; those still need user authorization. Avoid `debug*` names unless
  deployment is intended: such branches deploy when in a same-repository PR.
- A pushed branch is remotely saved, but new checkouts of `main` receive its
  context only after merge. Keep pending work discoverable in the handoff and
  inspect relevant remote branches when resuming unfinished work. Once the owner
  requests a PR, open it; push-only work does not need one automatically.
