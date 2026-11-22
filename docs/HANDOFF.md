# Handoff

## 2026-11-22 — timed egg laying

- `feature/timed-egg-laying` starts from main 0bd0d41 (#108). Eggs reserve
  a brood and colony place and pay their costs when laying starts. Development
  and the eggs-raised counter start when the saved laying timer finishes.
  Queen Chamber and Queen's Rhythm affect newly started eggs.
- The cancel button appears only for waiting orders. Canceling preserves the
  current egg. Worker/scout FIFO, automatic brood, and emergency recovery
  share the same queen. Existing brood keeps its saved development.
- Passed 321 engine tests, the development build, and brood layout at
  320/390/1280px. Timed laying and cancellation passed a focused browser flow
  at 390/1280px and mobile screenshots were captured.
