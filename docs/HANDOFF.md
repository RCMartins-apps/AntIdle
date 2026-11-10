# Handoff

## Last verified: 2026-11-10 — offline play and optional installation

- Main baseline: hex-map PR #56 merged as 8d73f2c with all GitHub CI passing.
  Completed feature/offline-install branch starts there; no PR/merge requested yet.
- Owner prioritized reopening/playing without internet after Chrome install was
  disabled. Added relative manifest, maskable-safe 192/512px ant icons and install
  metadata; pinned browser libraries are local with licenses/original SRI.
  Unused Font Awesome and redundant external Popper script removed.
- scripts/package_site.py is shared by deployment/tests: fingerprints production
  bundle, validates entry assets, hashes releases and generates integrity-protected
  atomic precaching. main.js alias supports old HTML during first rollout, outside
  precache. Cache keys are scoped to this app; no save data is deleted or changed.
- pwa.js registers only in packaged releases and reports preparation/readiness and
  waiting updates. Updates never reload open colonies; all game windows must close.
  Failed updates preserve the old working cache. Source dev pages don't register;
  use different ports for dev/package previews to avoid a pre-existing controller.
- Verified four packaging tests (determinism, invalidation/SRI/icons, failure guards),
  existing Python report test and six classifier tests. Regular persistent Chromium
  profiles at 390/1280px report installability with valid icons and pass true offline
  cold reopening/play/save, two-window update deferral, coherent new-release offline
  launch, missing-bundle update rejection and old-release recovery. No CDN requests.
- Existing production UI smoke passed at phone/desktop widths with local libraries.
  Used already-verified main fullOptJS output; no Scala/gameplay/save-schema changes.
  PR workflow now runs package/offline checks; new GitHub PR CI not run yet.
- docs/OFFLINE.md, README, tests/README.md and D37 cover setup/limits. First complete
  online cache is required, installation optional for offline tabs, browser storage
  can be cleared/evicted, no cloud sync and no changed offline-time progression.
- Preserve unrelated work/ and Python caches. Generated packages/profiles/logs are
  in /tmp; default publish-folder is ignored. No generated bundles/profiles staged.
