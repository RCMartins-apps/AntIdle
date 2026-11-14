# Handoff

## Last verified: 2026-11-14 — second prestige and staged founding complete

- Branch `feature/second-prestige` starts at main `7b22c11` (PR #73). Owner approved
  Beyond the Woodland: three handcrafted second-ring sites, plus a clear reward →
  Understanding selection → confirmation flow. Owner now authorized PR/auto-merge.
  Task ordering PR #74 is separate; refresh main before a later PR/integration.
- Beyond costs 1 DNA at the second founding after Shared Paths; existing banked DNA
  can also learn it once eligible. Reveals append three sites after the original
  six. Each has finite 50/30/20 scout Knowledge and a three-study research unlock.
- Colony research: Aphid Shelters (10→20 places/level; no free animals), Trail
  Logistics (60→75% manual haul rate, +25% supplies) and Coordinated Expeditions
  (+1 slot). Current-colony research resets; Understanding access persists. Values,
  costs, Knowledge budget and pacing results are in PRESTIGE.md, D46 and VISION.
- Founding previews live DNA earned/available/spent/remaining and sugar bonuses,
  numeric resource/capacity/population/chamber resets, research names, and what is
  kept. Selection/back costs nothing. An explicit final checkbox is required.
  Engine validates and applies selection/payment/reset atomically. Unknown,
  duplicate, premature, owned and unaffordable selections are rejected. Learning
  from banked DNA clears stale drafts. New ownership defaults false in old saves.
- Final validation: 277 engine tests and 4 balance tests passed; production build
  completed with zero Closure errors/warnings. Production staged-prestige gate
  passed at 320/390/1280px, including banked-DNA drafts, both introductory foundings,
  all three outer-site reveals/studies/purchases and save persistence. Inspected
  phone choice/map screenshots; shortened repeated research text.
- Production CI UI gate passed at 390/1280px; original map/trail/aphid gate at
  320/390/1280px; offline install/update/reopen gate at 390/1280px. Scoped the old
  extinct-worker resource assertion so it does not match the reset preview label.
  Prestige gate is now a separate five-minute CI step under the existing job cap.
  YAML step check and git diff --check passed.
- Two balanced-policy six-hour seeded runs (42/137) reached all three foundings,
  studied all outer sites and bought the new research, with zero starvation deaths.
  Third founding at 313.6/311.3 simulated minutes. This is scoped pacing evidence,
  not a universal balance or enjoyment claim. See PRESTIGE.md for reproduction.
- PR/auto-merge handoff authorized. Main remains 7b22c11; active required test
  and changes / classify checks, strict up-to-date policy and no bypass verified.
  Auto-merge is enabled, squash only. Check GitHub for live PR/deployment state;
  finish after queue confirmation without CI polling.
- Preserve unrelated work/ and Python caches; temporary logs/reports and
  screenshots are under /tmp and are not durable chat attachments.
