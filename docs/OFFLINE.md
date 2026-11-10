# Offline play and browser installation

The published release can reopen without a connection after one complete online
visit. Wait for **Available offline** at the bottom of the page before disconnecting.
This works in a normal browser tab as well as an installed app; installation is
optional for caching. An initial visit without internet cannot download the game.

In normal Chrome (not private browsing), use the browser menu's Install option.
The manifest supplies the Ant Idle name, app icon and standalone window. Installation
needs browser/OS support and HTTPS; localhost is supported for development testing.
No app-store build, native wrapper, notifications or account is required.

Saves remain in this browser's local storage. The offline cache doesn't change
the save format, add cloud synchronization or simulate gameplay while closed.
Stored offline time and its multiplier work as before. Clearing site data can
remove saves/cache, and browsers can evict cached files; keep using save exports
for backups. Installing isn't a substitute for backing up a colony.

## Updates

An online visit checks for a new release in the background. A complete new release
waits while any existing game window/tab is open. **Update ready · close all game
windows to apply** appears when ready. Use normal saving and close all instances;
the next launch uses the new version, even offline. No automatic reload interrupts
an in-progress colony. A failed download/install leaves the previous working
release in place. This intentionally favors a coherent running game over switching
versions underneath it.

## How the release is built

```sh
sbt fullOptJS
python3 scripts/package_site.py --out /tmp/antidle-site
python3 -m http.server 8001 --directory /tmp/antidle-site
```

Open localhost:8001. Use a new output directory each time. GitHub Pages deploys
that same package automatically on authorized main/debug deployments.

- `index.html` uses pinned local Bootstrap CSS/bundle and jQuery, with original
  SRI values. The bundle already includes Popper; unused Font Awesome is removed.
  No CDN/font connection is needed at runtime. See assets/vendor/README.md/licenses.
- `manifest.webmanifest` uses relative URLs/id/scope so Pages subpaths work.
  Icons include 192/512px PNGs with maskable-safe padding, authored in ant.svg.
- `package_site.py` copies only the production game bundle, fingerprints its URL,
  computes a release hash from all shell inputs, and generates `sw.js` with an
  integrity-protected precache list. Missing/external HTML assets fail packaging.
  A non-precached main.js alias supports pre-PWA HTML during the first rollout.
- The worker atomically precaches the release and uses its own cache for navigation
  and assets. Integrity checks reject stale/incomplete deploy responses. It doesn't
  force activation or reload clients. Old release caches are removed only on new
  activation, limited to this app's exact scope prefix. Local saves are untouched.
- `pwa.js` handles registration/readiness/update status. Ordinary source-tree dev
  pages don't register a worker; packaging enables it explicitly. This avoids
  stale fastOptJS bundles during normal development. Use different ports for
  source development (8000) and packaged previews (8001). If reusing the same
  origin/port, unregister its preview worker in DevTools before serving source.

Browser storage is not permanent. Offline operation requires a successful cache
and that it remains stored; a cache failure doesn't stop ordinary online play.

## Verification

```sh
python3 -m unittest discover -s tests -p 'package_site_test.py'
python3 tests/offline_browser_smoke.py
```

The browser test uses the real packaged production build under /AntIdle/, regular
isolated temporary Chrome profiles (private contexts cannot install PWAs), no HTTP
cache, and real service workers. At 390/1280px it checks valid/installable manifest
and icon sizes, no CDN requests, offline cold reopen/play/save, deferred updates
with two windows, coherent offline launch of the new release, and recovery from a
new release missing its game bundle. PR CI runs it alongside the production UI/map
gates. Generated packages and browser profiles stay outside Git.
