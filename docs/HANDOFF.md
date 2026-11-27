# Handoff

## 2026-11-27

The game loads layout, navigation, resources, and gameplay styles from
local stylesheets in the original cascade order. The production package
includes and precaches every stylesheet.

Verified package and classification tests, precache membership, resource
layout at 320px, 390px, and 1280px, and packaged offline browser checks.
Local browser checks reused the game bundle because this change moves CSS.
See [issue #177](https://github.com/RCMartins-apps/AntIdle/issues/177).
