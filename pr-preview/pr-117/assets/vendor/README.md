# Local browser dependencies

These are the unchanged pinned files previously loaded from CDNs. Keeping them
local allows complete same-origin offline precaching. Their original HTML SRI
hashes are retained and verified by tests/package_site_test.py.

- Bootstrap 5.3.2 CSS and bundle: https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/
  MIT license: bootstrap-LICENSE. The bundle includes Popper 2.11.8 (MIT:
  popper-LICENSE.md), so a second Popper script isn't required.
- jQuery 2.2.4: https://code.jquery.com/jquery-2.2.4.min.js
  MIT license: jquery-LICENSE.txt. Existing save/new-game modals use its plugin API.

The unused Font Awesome stylesheet was removed; game resource and info icons
are inline SVG. Don't reintroduce CDN/runtime font dependencies without updating
and testing the complete offline package. Library upgrades should update filenames,
licenses and SRI together. Production release hashes include these files.
