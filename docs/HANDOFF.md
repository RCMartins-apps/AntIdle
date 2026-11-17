# Handoff

## 2026-11-17 — replace PR #90's unreliable toggle-event persistence

- Continue `polish/task-controls`, PR #90, head 072d2d0. Second CI attempt failed
  job 133869603733 at the new closed-preference assertion: advancing a simulated
  clock does not guarantee native toggle delivery. The prior timing-only repair
  was insufficient. No required checks are removed or weakened.
- UiPreferences now observes the details element's open attribute and saves at
  the mutation microtask checkpoint. Restore happens before observation starts.
  bindDetails returns a disconnect function; rememberDetails binds/disconnects it
  through Laminar mount/unmount callbacks, preventing detached stale writes.
- Brood regression blocks all native toggle events and asserts saved closed/open
  states immediately, with no waits or timer advances, then checks reload. This
  deterministically failed against the old production bundle and passed against
  the new development and production bundles at 320/390/1280px.
- Wide-font task controls passed 320/390/1280px. Development/production builds
  passed with zero Closure warnings/errors. Existing production UI checks passed
  390/1280px, and Python syntax/diff checks passed. Final edits were docs only.
  No simulation/balance changes.
- Push this repair to the existing branch. Owner authorizes PR #90 auto-merge
  with enforced test and changes / classify checks. Preserve work/ and caches.
