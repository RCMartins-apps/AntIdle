# Handoff

## Last verified: 2026-11-14 — brood-stage icons

- Branch `ui/brood-stage-icons` starts at fetched main `99ee3f2` (PR #68).
  PR #69 (consistency/guide-loss fixes) and #70 (aphid relocation) were authorized
  for native auto-merge in earlier turns; main had not advanced when this task
  began. This branch does not include them. Check live GitHub state before a PR.
- Brood resource disclosure/desktop tooltip now places distinct SVG egg, larva
  and pupa icons beside the existing name/progress. Icons derive from AntBrood's
  actual stage flags, covering small/standard/scout and legacy entries. Existing
  text supplies accessible labels; icons are decorative. No gameplay/save changes.
- Reuses ResourceIcons sizing/strokes with two new silhouettes/colors. Live stage
  transitions update the icon. CI browser gate now covers all three shapes,
  caste/legacy entries, disclosure persistence and a live egg-to-larva transition.
- fastOptJS passed. Focused browser verification using that maintained helper
  passed at 320/390/1280px; visually inspected the 320px list. git diff --check
  passed. No engine tests, full production build or complete CI browser run was
  needed/repeated for this presentation-only change.
- No PR requested for this task. Save on this feature branch under the standing
  push workflow; verify its remote head when resuming. Preserve unrelated work/
  and Python caches. Temporary screenshots/logs are under /tmp, not durable links.
- Aphid rules and measured pacing are in docs/APHIDS.md on feature/aphid-relocation;
  audit findings are in docs/CONSISTENCY-AUDIT.md on fix/consistency-audit. Do not
  infer either pending branch has merged merely from prior chat approval.
