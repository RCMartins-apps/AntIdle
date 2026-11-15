# Handoff

## 2026-11-15 — polish pass 3: UI memory

Owner authorized all five passes, separate PRs and auto-merge; see POLISH.md.
PR #78 status polish and #79 map feedback are queued; verify live GitHub state.
Current branch `polish/ui-memory` adds browser-only disclosure and map-selection
preferences in UiPreferences. Stable keys cover brood, resource/action/info
sections, aging, purchased upgrades, lineage help and milestone tiers. Invalid or
newly locked map choices fall back to the nest; confirmations are not persisted.
Preferences tolerate storage failure and stay separate from colony saves.
fastOptJS and 390/1280 browser validation passed, including open-state restoration
and invalid/new-colony map fallback. PR/auto-merge is authorized.
Remaining work: event log and visual consistency. Preserve unrelated work/ and
Python caches; integrate latest main before each PR and keep required checks.
