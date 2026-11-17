"""Packaged PR previews: root worker bypass, independent saves, canonical paths."""
import base64
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, select_tab

spec = importlib.util.spec_from_file_location('package_site', ROOT / 'scripts/package_site.py')
site = importlib.util.module_from_spec(spec)
spec.loader.exec_module(site)

class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass

def save(page, key):
    select_tab(page, 'Settings')
    page.get_by_role('button', name='Manual Save', exact=True).click()
    return page.evaluate('(key) => localStorage.getItem(key)', key)

with tempfile.TemporaryDirectory() as temp:
    temp = Path(temp)
    source = temp / 'source'
    source.mkdir()
    for name in ['assets', 'web', 'target']:
        (source / name).symlink_to(ROOT / name, target_is_directory=True)
    for name in ['pwa.js', 'manifest.webmanifest']:
        shutil.copy2(ROOT / name, source / name)
    html = (ROOT / 'index.html').read_text()
    for marker, path in [('production', ''), ('9', 'pr-preview/pr-9'), ('10', 'pr-preview/pr-10')]:
        (source / 'index.html').write_text(html.replace('<head>', f'<head><meta name="preview-test" content="{marker}">'))
        site.package(temp / 'public' / 'AntIdle' / path, source)
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(temp / 'public')))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True, executable_path=shutil.which('chromium'))
            context = browser.new_context(viewport={'width':390,'height':844}, is_mobile=True, has_touch=True)
            page = context.new_page()
            url = f'http://127.0.0.1:{server.server_port}/AntIdle/'
            page.goto(url, wait_until='networkidle')
            expect(page.locator('#offline-status')).to_have_text('Available offline', timeout=30000)
            page.wait_for_function('navigator.serviceWorker.controller !== null')
            production = json.loads(base64.b64decode(save(page, 'save')))
            production['basicResources']['sugar']['amount'] = 7770000
            production = json.dumps(production)
            page.evaluate('(value) => localStorage.setItem("save", value)', production)
            page.close()
            saves = {}
            for number in [9, 10]:
                page = context.new_page()
                page.goto(f'{url}pr-preview/pr-{number}/', wait_until='networkidle')
                # The root worker must not substitute production's cached index.
                expect(page.locator('meta[name="preview-test"]')).to_have_attribute('content', str(number))
                expect(page.locator('#offline-status')).to_have_text('Available offline', timeout=30000)
                key = f'save:pr-preview:{number}'
                data = json.loads(base64.b64decode(save(page, key)))
                assert data['basicResources']['sugar']['amount'] != 7770000
                assert page.evaluate('localStorage.getItem("save")') == production
                data['basicResources']['sugar']['amount'] = number * 10000
                saves[number] = json.dumps(data)
                page.evaluate('([key, value]) => localStorage.setItem(key, value)', [key, saves[number]])
                page.close()
            page = context.new_page()
            page.goto(f'{url}pr-preview/pr-9/index.html', wait_until='networkidle')
            expect(page.locator('meta[name="preview-test"]')).to_have_attribute('content', '9')
            reloaded = json.loads(base64.b64decode(save(page, 'save:pr-preview:9')))
            assert reloaded['basicResources']['sugar']['amount'] == 90000
            assert page.evaluate('localStorage.getItem("save:pr-preview:10")') == saves[10]
            assert page.evaluate('localStorage.getItem("save")') == production
            # Preferences are also isolated from the live colony.
            page.locator('.mobile-resource-toggle').click()
            assert page.evaluate('localStorage.getItem("antidle.ui:pr-preview:9.resources.expanded")') == 'true'
            assert page.evaluate('localStorage.getItem("antidle.ui.resources.expanded")') is None
            keys = page.evaluate('Object.keys(localStorage)')
            assert any(key.startswith('antidle.ui:pr-preview:9.') for key in keys), keys
            browser.close()
            print('PASS packaged preview paths, production-worker bypass, independent saves and preferences')
    finally:
        server.shutdown()
        server.server_close()
