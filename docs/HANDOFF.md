# Handoff

## 2026-11-19 — repair resource preview PR #100

- `fix/mobile-resource-expansion`, PR #100, stays open for manual preview with
  auto-merge off. Base is main 0c5561a (#99). Feature: expanded mobile Resources
  replaces compact chips; actual tab/sub-tab changes collapse and remember it.
  Same-view clicks, initialization, live ticks and desktop navigation do not.
- CI passed earlier game/browser checks, but prestige_smoke.py expected visible
  resource rows after founding had changed views and collapsed Resources.
  Both post-founding assertions now explicitly expand the panel first, keeping
  the reset-visibility checks intact. No gameplay or UI code changed in repair.
- Production prestige suite passed 320/390/1280px; offline play/update checks
  passed 390/1280px and packaged preview save/worker isolation passed. Python
  syntax/diff checks passed. Push reruns CI; auto-merge remains off.
- Preview: https://rcmartins-apps.github.io/AntIdle/pr-preview/pr-100/ — pending
  successful CI/deployment. No new screenshots needed for test-only repair.
  Preserve unrelated work/ and Python caches.
