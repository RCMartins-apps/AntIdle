#!/usr/bin/env python3
"""Package a coherent, self-contained production release for any hosting subpath."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
from html.parser import HTMLParser

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = Path('target/scala-2.13/antidle-opt/main.js')


def package(output: Path, root: Path = ROOT):
    if output.exists():
        raise ValueError('Output directory already exists; choose a fresh directory')
    bundle = (root / BUNDLE).read_bytes()
    filename = f'game.{hashlib.sha256(bundle).hexdigest()[:16]}.js'
    html = (root / 'index.html').read_text()
    expected = 'target/scala-2.13/antidle-fastopt/main.js'
    if html.count(expected) != 1 or 'src="pwa.js"' not in html:
        raise ValueError('Missing expected game/PWA entry points')
    html = html.replace(expected, filename).replace('src="pwa.js"', 'src="pwa.js" data-service-worker="./sw.js"')
    files = {'index.html': html.encode(), filename: bundle,
             'pwa.js': (root / 'pwa.js').read_bytes(),
             'manifest.webmanifest': (root / 'manifest.webmanifest').read_bytes()}
    files.update({str(path.relative_to(root)): path.read_bytes() for path in sorted((root / 'assets').rglob('*')) if path.is_file()})
    class AssetLinks(HTMLParser):
        def handle_starttag(self, tag, attrs):
            attributes = dict(attrs)
            ref = attributes.get('src') if tag == 'script' else attributes.get('href') if tag == 'link' else None
            if ref and ref not in files:
                raise ValueError(f'External or missing runtime asset: {ref}')
    AssetLinks().feed(html)
    manifest = json.loads(files['manifest.webmanifest'])
    for icon in manifest['icons']:
        if icon['src'] not in files:
            raise ValueError(f'Missing app icon: {icon["src"]}')
    template = (root / 'web/sw.template.js').read_text()
    digest = hashlib.sha256(template.encode())
    for name, body in sorted(files.items()):
        digest.update(name.encode() + b'\0' + body)
    version = digest.hexdigest()[:24]
    files['index.html'] = html.replace('<title>', f'<meta name="antidle-build" content="{version}">\n    <title>', 1).encode()
    assets = [{'path': name, 'integrity': 'sha256-' + base64.b64encode(hashlib.sha256(body).digest()).decode()}
              for name, body in sorted(files.items())]
    worker = template.replace('__VERSION__', json.dumps(version)).replace('__ASSETS__', json.dumps(assets))
    output.mkdir(parents=True)
    for name, body in files.items():
        path = output / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(body)
    # Keep pre-PWA cached HTML bootable during the first Pages rollout.
    # This compatibility alias is not precached; new releases use the hashed URL.
    (output / 'main.js').write_bytes(bundle)
    (output / 'sw.js').write_text(worker)
    return {'version': version, 'bundle': filename, 'assets': assets}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=ROOT / 'publish-folder')
    args = parser.parse_args()
    result = package(args.out.resolve())
    print(f"Packaged {len(result['assets'])} offline files; build {result['version']}")


if __name__ == '__main__':
    main()
