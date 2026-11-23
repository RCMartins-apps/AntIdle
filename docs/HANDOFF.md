# Handoff

## 2026-11-22 — compact Jobs cards

- `polish/mobile-worker-cards` from main c6bab7f (#109). Small and standard
  workers have independent disclosures with matching native arrows. The standard
  card keeps the worker
  total and a positive idle count visible in its header.
- Jobs show compact resource icon rates and no description panels. Disabled
  assign buttons retain their reasons in titles, without an extra row.
  The Royal Nurses shortcut opens the standard card.
- Development build passed. The Jobs and blocked-action browser scenarios
  passed at 320/390/1280 px. Captured mobile open and closed cards.
- Brood Nurse rows show an egg icon and the assigned caste's total extra care.
  The same calculation supplies the game engine and the badge, including
  Nurse Training and small workers' half contribution.
- Visible game numbers use at most two decimal places, including amounts,
  costs, rates and the milestone multiplier. Tiny values retain a signed bound.
  Engine arithmetic, costs and save precision are unaffected.
