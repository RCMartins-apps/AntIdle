# Handoff

## 2026-11-27

Local saves retain the previous valid save in a backup key. Startup and manual
load can recover that colony if the primary save is missing or invalid. Import
validates and writes the save before changing the running colony. Storage
failures and malformed or incompatible saves produce visible feedback. A hard
reset clears the recovery copy after writing the fresh colony.

Verified three focused recovery tests and 23 existing save and exploration
tests, plus recovery, malformed import, failed writes and reset in Chromium at
390px. See [issue #135](https://github.com/RCMartins-apps/AntIdle/issues/135).
