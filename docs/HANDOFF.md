# Handoff

## 2026-11-18 — expanded mobile resources without duplication

- `fix/mobile-resource-expansion` starts at main 0c5561a, merged #99. The owner
  requested detailed rows replace compact chips when the resources panel opens
  (D63). Authoritative follow-up is this branch. Owner requested a PR for preview;
  leave auto-merge off.
- Expanded mobile view hides compact chips but retains Resources/collapse and
  catch-up controls. Collapsing restores the live compact view and saved state.
  Expanded toggle no longer references hidden summary data in aria-describedby.
- Resource/catch-up/timing browser checks passed 320/390/1280px, including reload,
  resize and stable timing. Expanded/collapsed screenshots shown in chat. Syntax
  and diff checks passed. CSS/JS only; no game rebuild or engine changes needed.
- After discovering #99 had merged, cherry-picked the tested patch to current
  main and verified UI/test files are identical to the tested version. #99's
  description restored to merged scope and preview marked removed. No merges
  performed. Preserve unrelated work/ and Python caches.

- Navigation follow-up: observe actual data-mobile-view changes and collapse
  Resources on mobile, saving false. Ignore same-view clicks, initialization,
  live ticks and desktop navigation. Tests cover primary/sub-tab changes and
  persistence; production UI assertions explicitly open resource details when
  inspecting them. Updated screenshot shown in chat. Resource/navigation checks
  passed 320/390px and resource timing/catch-up at 1280px. Refreshed production
  build passed with zero Closure warnings/errors; production UI gate passed
  390/1280px. JavaScript/Python syntax and diff checks passed. Ready for manual
  preview PR; no auto-merge authorized.
