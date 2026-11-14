"""Real packaged-release checks: installability, offline reopen, saves and atomic updates."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
import os
from pathlib import Path
import shutil
import tempfile
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, save_game

spec = importlib.util.spec_from_file_location('package_site', ROOT / 'scripts/package_site.py')
site = importlib.util.module_from_spec(spec)
spec.loader.exec_module(site)


class ReleaseHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def translate_path(self, path):
        # Exercise the same subpath hosting as GitHub Pages, not only origin root.
        relative = path.split('?', 1)[0].removeprefix('/AntIdle/').lstrip('/')
        return str(self.server.release / relative)

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()


def make_release(root, output, label):
    fixture = root / label
    fixture.mkdir()
    for name in ['index.html', 'pwa.js', 'manifest.webmanifest']:
        shutil.copy2(ROOT / name, fixture / name)
    shutil.copytree(ROOT / 'assets', fixture / 'assets')
    shutil.copytree(ROOT / 'web', fixture / 'web')
    bundle = fixture / site.BUNDLE
    bundle.parent.mkdir(parents=True)
    bundle.write_bytes((ROOT / site.BUNDLE).read_bytes() + f'\n// {label}\n'.encode())
    html = (fixture / 'index.html').read_text().replace('<title>Ant Idle</title>', f'<title>Ant Idle {label}</title>')
    (fixture / 'index.html').write_text(html)
    return site.package(output, fixture)


def check(context, width, server, root):
    a, b, broken = [root / f'{name}-{width}' for name in ('release-a', 'release-b', 'release-broken')]
    first = make_release(root, a, f'first-{width}')
    second = make_release(root, b, f'second-{width}')
    failed = make_release(root, broken, f'failed-{width}')
    (broken / failed['bundle']).unlink()  # Simulate interrupted deployment/network delivery.
    server.release = a
    for blank in context.pages:
        blank.close()
    workers = []
    context.on('serviceworker', lambda worker: workers.append(worker))
    page = context.new_page()
    errors, external = [], []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.on('request', lambda req: external.append(req.url) if req.url.startswith('http') and not req.url.startswith(url) else None)
    url = f'http://127.0.0.1:{server.server_port}/AntIdle/'
    try:
        page.goto(url, wait_until='networkidle')
        expect(page.locator('#offline-status')).to_have_text('Available offline', timeout=30000)
        page.wait_for_function('navigator.serviceWorker.controller !== null')
        running_build = page.locator('.game-build-footer .build-version').inner_text()
        assert running_build.startswith('Build ')
        # Disable HTTP cache: this must work through the service worker's release cache.
        cdp = context.new_cdp_session(page)
        cdp.send('Network.enable')
        cdp.send('Network.setCacheDisabled', {'cacheDisabled': True})
        manifest = cdp.send('Page.getAppManifest')
        assert not manifest['errors'], manifest
        parsed = json.loads(manifest['data'])
        assert parsed['display'] == 'standalone' and parsed['start_url'] == './'
        for size in (192, 512):
            assert page.evaluate('''async size => {
              const img = new Image(); img.src = `assets/icons/icon-${size}.png`;
              await img.decode(); return img.naturalWidth === size && img.naturalHeight === size;
            }''', size)
        installability = cdp.send('Page.getInstallabilityErrors')
        assert not installability['installabilityErrors'], installability
        for _ in range(17):
            page.get_by_role('button', name='Gather Sugar', exact=True).click()
        saved = save_game(page)
        assert saved['basicResources']['sugar']['amount'] == 170000
        saved_raw = page.evaluate("localStorage.getItem('save')")
        context.set_offline(True)
        page.close()
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(url, wait_until='networkidle')
        expect(page.get_by_role('button', name='Gather Sugar', exact=True)).to_be_visible()
        expect(page.locator('#offline-status')).to_have_text('Available offline')
        expect(page.locator('.game-build-footer .build-version')).to_have_text(running_build)
        assert page.evaluate("localStorage.getItem('save')") == saved_raw
        assert page.get_by_role('button', name='Gather Sugar', exact=True).evaluate("el => getComputedStyle(el).backgroundColor") == 'rgb(13, 110, 253)'
        page.get_by_role('button', name='Gather Sugar', exact=True).click()
        assert save_game(page)['basicResources']['sugar']['amount'] == 180000
        # Updating must not reload an open game or mix its HTML and JS releases.
        context.set_offline(False)
        server.release = b
        page.evaluate("window.liveGameMarker = 'do-not-reload'")
        page.evaluate('async () => (await navigator.serviceWorker.getRegistration()).update()')
        expect(page.locator('#offline-status')).to_contain_text('Update ready', timeout=30000)
        assert page.evaluate('window.liveGameMarker') == 'do-not-reload'
        expect(page.locator('.game-build-footer .build-version')).to_have_text(running_build)
        assert page.title() == f'Ant Idle first-{width}'
        page.reload(wait_until='networkidle')
        assert page.title() == f'Ant Idle first-{width}'
        assert page.locator(f'script[src="{first["bundle"]}"]').count() == 1
        new_worker = workers[-1]
        other_window = context.new_page()
        other_window.goto(url, wait_until='networkidle')
        context.set_offline(True)
        page.close()
        assert other_window.title() == f'Ant Idle first-{width}'
        assert other_window.evaluate('async () => !!(await navigator.serviceWorker.getRegistration()).waiting')
        other_window.close()
        assert new_worker.evaluate('''async () => {
          for (let i=0; i<100; i++) {
            if (self.registration.active?.state === 'activated' && !self.registration.waiting) return true;
            await new Promise(resolve => setTimeout(resolve,100));
          }
          throw new Error('New release never activated after all windows closed');
        }''')
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto(url, wait_until='networkidle')
        assert page.title() == f'Ant Idle second-{width}'
        assert page.locator(f'script[src="{second["bundle"]}"]').count() == 1
        assert save_game(page)['basicResources']['sugar']['amount'] == 180000
        # An incomplete update must fail installation and preserve the working release offline.
        context.set_offline(False)
        server.release = broken
        page.evaluate('''async () => {
          const reg = await navigator.serviceWorker.getRegistration();
          await new Promise(async (resolve,reject) => {
            setTimeout(() => reject(new Error('Broken-update test timed out')), 15000);
            reg.addEventListener('updatefound', () => {
              const worker = reg.installing;
              worker.addEventListener('statechange', () => {
                if (worker.state === 'redundant') resolve();
                if (worker.state === 'installed') reject(new Error('Broken release installed'));
              });
            }, {once:true});
            await reg.update();
          });
        }''')
        context.set_offline(True)
        page.reload(wait_until='networkidle')
        assert page.title() == f'Ant Idle second-{width}'
        assert save_game(page)['basicResources']['sugar']['amount'] == 180000
        assert not errors, errors
        assert not external, external
        print(f'PASS offline {width}px: installable manifest/icons, no CDNs, offline reopen/save/play, deferred coherent update, failed-update recovery', flush=True)
    finally:
        context.close()


def main():
    with tempfile.TemporaryDirectory(prefix='antidle-offline-') as directory:
        root = Path(directory)
        server = ThreadingHTTPServer(('127.0.0.1', 0), ReleaseHandler)
        server.release = root
        Thread(target=server.serve_forever, daemon=True).start()
        try:
            with sync_playwright() as playwright:
                launch = {'headless': True}
                chromium = os.environ.get('CHROMIUM_PATH') or shutil.which('chromium')
                if chromium:
                    launch['executable_path'] = chromium
                for width in (390, 1280):
                    context = playwright.chromium.launch_persistent_context(str(root / f'profile-{width}'),
                        viewport={'width': width, 'height': 844}, service_workers='allow', **launch)
                    check(context, width, server, root)
        finally:
            server.shutdown()


if __name__ == '__main__':
    main()
