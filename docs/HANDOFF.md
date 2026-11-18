# Handoff

## 2026-11-18 — expanded mobile resources without duplication

- `fix/mobile-resource-expansion` starts at main 0c5561a, merged #99. The owner
  requested detailed rows replace compact chips when the resources panel opens
  (D63). Authoritative follow-up is this branch; no new PR requested yet.
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
