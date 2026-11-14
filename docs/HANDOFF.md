# Handoff

## Last verified: 2026-11-14 — aphid population and relocation

- Branch `feature/aphid-relocation` starts from main `99ee3f2` (PR #68). Main was
  checked again before handoff. PR #69 (audit fixes and guide-loss resizing) is a
  separate authorized auto-merge PR, still pending at the start of this task;
  this branch does not include it. Check GitHub/main before a future PR/update.
- Owner approved D43: each chamber level houses ten aphids; each worker tends ten.
  Per-tender output/protein cost unchanged. The first chamber gets ten animals;
  further empty housing requires relocation. Stock persists, resets on founding,
  and legacy version-7 saves populate existing housing once to preserve capacity.
- Woodland Edge now offers Relocate Aphids after Wild Aphids/Aphid Tending. Safe
  fixed mission: five workers, one scout, ten sugar, thirty simulated seconds,
  ten reserved aphid places. Incoming groups, costs and duration are visible.
  Uses normal expedition slots/age returns/statistics. No Knowledge/resource haul,
  new ring or Understanding. Spent insects do not erase the living aphid source.
- Details and reproducible before/after pacing results: docs/APHIDS.md. Balanced
  policy, seeds 42/137, three hours each: first daughter readiness +3.11% / -1.06%,
  one founding per run and zero starvation deaths. Earlier opening milestones
  identical. Small measured impact for these cases, not universal balance proof.
- 267 engine tests and four balance tests passed; fastOptJS and balance/fastOptJS
  succeeded. fullOptJS: zero Closure errors/warnings. Maintained map/trail/aphid
  browser gate passed at 320/390/1280px; production CI browser gate at 390/1280px.
  git diff --check passed. Offline packaging was not rerun (no packaging changes).
- Existing production/rate/default-pool tests now specify populated aphid housing.
  New relocation tests cover housing reservations, concurrent trips, replay,
  resource/scout/worker/research gates, reload and one-time legacy migration.
  Balance policies now relocate animals to meet their existing tender targets.
- No PR requested for this task. Commit/push to this feature branch under the
  standing backup workflow; verify the remote head when resuming. Preserve
  unrelated work/ and Python caches. Baseline worktree/reports are temporary
  under /tmp; no scratch artifacts should be committed.
