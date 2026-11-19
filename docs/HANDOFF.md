# Handoff

## 2026-11-19 — purchased list preview updated from main

- `polish/purchased-upgrades-view` integrates main dc89fb7 (merged #100), including
  unified expanded Resources and collapse on navigation. Documentation conflicts
  combined; CSS merged cleanly. Owner requested branch update then preview PR;
  leave auto-merge off for manual review.
- Mobile Purchased opens its shared list directly, with no repeated disclosure
  title, border or top padding. Upgrade info controls remain usable. Desktop
  retains its disclosure on the combined Upgrades screen (D64).
- Updated navigation checks passed 320/390px. Focused purchased-list checks
  passed 320/390/1280px: direct rows, info controls, zero wrapper spacing and
  desktop disclosure. Existing screenshot still represents this styling. Python
  syntax/diff checks passed. CSS-only; no engine changes or rebuild required.
- Preserve unrelated work/ and Python caches.
