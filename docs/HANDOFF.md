# Handoff

## 2026-11-27

Each tab remembers the save revision it loaded. A stale tab can export its
colony or load the newer save. Web Locks serialize simultaneous writes where
available. Older browsers still compare the stored revision before writing.

Verified the production build, concurrent and stale save checks at 390px,
preview-slot isolation, and offline round trips at 390px and 1280px. See
[issue #158](https://github.com/RCMartins-apps/AntIdle/issues/158).
