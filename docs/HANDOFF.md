# Handoff

## 2026-11-15 — polish pass 3: UI memory

Owner authorized all five passes, separate PRs and auto-merge; see POLISH.md.
PR #78 status polish and #79 map feedback are merged; main 009a29d is
integrated. PR #80 is the current UI-memory change.
Current branch `polish/ui-memory` adds browser-only disclosure and map-selection
preferences in UiPreferences. Stable keys cover brood, resource/action/info
sections, aging, purchased upgrades, lineage help and milestone tiers. Invalid or
newly locked map choices fall back to the nest; confirmations are not persisted.
Preferences tolerate storage failure and stay separate from colony saves.
fastOptJS and 390/1280 browser validation passed, including open-state restoration
and invalid/new-colony map fallback. Full map/trail/aphid and prestige gates
also passed at 320/390/1280px; purchase-history assertions now verify restored
open state. PR/auto-merge is authorized.
Remaining work: event log and visual consistency. Preserve unrelated work/ and
Python caches; integrate latest main before each PR and keep required checks.
