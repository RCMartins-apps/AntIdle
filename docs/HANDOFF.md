# Handoff

## 2026-11-19 — direct purchased-upgrades sub-tab

- `polish/purchased-upgrades-view` starts at main 0c5561a (#99). Independent of
  pending resource PR #100. Owner requested no repeated title/collapsible card
  inside Purchased (D64); no PR requested for this branch yet.
- Mobile Purchased hides the shared history summary, border and extra top space.
  Existing navigation opens the list automatically; info buttons remain usable.
  Desktop retains the history disclosure on its combined Upgrades screen.
- Navigation checks passed 320/390px. Focused purchased-list checks passed at
  320/390/1280px: direct rows, zero extra wrapper spacing, usable info controls,
  desktop disclosure and no overflow. Screenshot shown in chat. Python syntax
  and diff checks passed; CSS-only, no game rebuild needed.
- Added assertions to maintained navigation and purchase-history harnesses.
  Preserve unrelated work/ and Python caches.
