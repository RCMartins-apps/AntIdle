"""Validate coherent offline release generation without compiling the game."""
import base64
import hashlib
from html.parser import HTMLParser
import importlib.util
import json
from pathlib import Path
import re
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('package_site', ROOT / 'scripts/package_site.py')
site = importlib.util.module_from_spec(spec)
spec.loader.exec_module(site)


class PackagingTests(unittest.TestCase):
    def fixture(self, directory):
        root = Path(directory) / 'source'
        root.mkdir()
        for name in ['index.html', 'manifest.webmanifest', 'pwa.js']:
            shutil.copy2(ROOT / name, root / name)
        for name in ['web', 'assets']:
            shutil.copytree(ROOT / name, root / name)
        bundle = root / site.BUNDLE
        bundle.parent.mkdir(parents=True)
        bundle.write_text('window.productionGame = true;')
        return root

    def test_deterministic_complete_cache_and_integrity_for_every_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            a, b = Path(directory) / 'a', Path(directory) / 'b'
            first, second = site.package(a, root), site.package(b, root)
            self.assertEqual(first, second)
            html = (a / 'index.html').read_text()
            self.assertIn(first['bundle'], html)
            self.assertEqual((a / 'main.js').read_bytes(), (a / first['bundle']).read_bytes())
            self.assertIn('data-service-worker="./sw.js"', html)
            self.assertNotIn('antidle-fastopt', html)
            self.assertNotRegex(html, r'(?:href|src)="https?://')
            self.assertIn(first['version'], html)
            for asset in first['assets']:
                raw = (a / asset['path']).read_bytes()
                self.assertEqual(asset['integrity'], 'sha256-' + base64.b64encode(hashlib.sha256(raw).digest()).decode())
            worker = (a / 'sw.js').read_text()
            self.assertNotIn('__ASSETS__', worker)
            self.assertNotIn('self.skipWaiting(', worker)
            self.assertNotIn('localStorage', worker)
            self.assertEqual((a / 'sw.js').read_bytes(), (b / 'sw.js').read_bytes())

    def test_changes_to_bundle_html_or_styles_invalidate_release(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            first = site.package(Path(directory) / 'a', root)
            (root / site.BUNDLE).write_text('window.productionGame = 2;')
            changed = site.package(Path(directory) / 'b', root)
            self.assertNotEqual(first['bundle'], changed['bundle'])
            self.assertNotEqual(first['version'], changed['version'])
            css = root / 'assets/vendor/bootstrap-5.3.2.min.css'
            css.write_bytes(css.read_bytes() + b'\n/* test update */')
            style = site.package(Path(directory) / 'c', root)
            self.assertEqual(style['bundle'], changed['bundle'])
            self.assertNotEqual(style['version'], changed['version'])
            html = root / 'index.html'
            html.write_text(html.read_text().replace('<title>', '<title>Updated '))
            self.assertNotEqual(site.package(Path(directory) / 'd', root)['version'], style['version'])

    def test_missing_assets_external_dependencies_and_overwrites_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory)
            icon = root / 'assets/icons/icon-512.png'
            original = icon.read_bytes()
            icon.unlink()
            with self.assertRaisesRegex(ValueError, 'Missing app icon'):
                site.package(Path(directory) / 'a', root)
            icon.write_bytes(original)
            html = root / 'index.html'
            original = html.read_text()
            html.write_text(original.replace('</head>', '<script src="https://example.com/external.js"></script></head>'))
            with self.assertRaisesRegex(ValueError, 'External or missing'):
                site.package(Path(directory) / 'b', root)
            html.write_text(original)
            out = Path(directory) / 'c'
            site.package(out, root)
            with self.assertRaisesRegex(ValueError, 'already exists'):
                site.package(out, root)

    def test_vendored_libraries_match_the_original_html_integrity_and_icons_have_required_sizes(self):
        class Links(HTMLParser):
            def handle_starttag(self, tag, attrs):
                attributes = dict(attrs)
                integrity = attributes.get('integrity')
                if integrity:
                    algorithm, digest = integrity.split('-', 1)
                    path = attributes.get('src') or attributes['href']
                    actual = base64.b64encode(hashlib.new(algorithm, (ROOT / path).read_bytes()).digest()).decode()
                    if actual != digest:
                        raise AssertionError(path)
        Links().feed((ROOT / 'index.html').read_text())
        manifest = json.loads((ROOT / 'manifest.webmanifest').read_text())
        for icon in manifest['icons']:
            png = (ROOT / icon['src']).read_bytes()
            self.assertEqual(png[:8], b'\x89PNG\r\n\x1a\n')
            width, height = int.from_bytes(png[16:20], 'big'), int.from_bytes(png[20:24], 'big')
            self.assertEqual(icon['sizes'], f'{width}x{height}')


if __name__ == '__main__':
    unittest.main()
