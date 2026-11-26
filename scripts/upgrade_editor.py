#!/usr/bin/env python3
"""Serve the offline Ant Idle upgrade editor on this computer."""
import argparse
from hashlib import sha256
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import subprocess
from tempfile import NamedTemporaryFile
from threading import Lock

from balance import ROOT, balance_bundle, executable_command

ASSETS = ROOT / 'tools/balance/editor'
TUNING = ROOT / 'src/main/balance/upgrades.json'
CATALOG = '/api/catalog'
CHAMBERS = '/api/chambers'
CHAMBER_TUNING = ROOT / 'src/main/balance/chambers.json'
FILES = {
    '/': ('index.html', 'text/html'),
    '/editor.css': ('editor.css', 'text/css'),
    '/editor.js': ('editor.js', 'text/javascript'),
    '/chambers': ('chambers.html', 'text/html'),
    '/chambers.js': ('chambers.js', 'text/javascript'),
}


def read_catalog(bundle, source, command='--upgrade-catalog'):
    result = subprocess.run(['node', str(bundle), command], input=source,
                            capture_output=True, text=True, encoding='utf-8', timeout=20)
    try:
        data = json.loads(result.stdout)
    except ValueError as error:
        raise RuntimeError('Could not read upgrade definitions. Rebuild balance/fastOptJS.') from error
    if result.returncode:
        raise ValueError(data['error'])
    return data


def revision(contents):
    return sha256(contents).hexdigest()


def atomic_write(path, contents):
    with NamedTemporaryFile(dir=path.parent, prefix=f'.{path.name}-', delete=False) as temporary:
        staged = Path(temporary.name)
        temporary.write(contents)
    try:
        staged.chmod(path.stat().st_mode & 0o777)
        staged.replace(path)
    finally:
        staged.unlink(missing_ok=True)


class EditorServer(ThreadingHTTPServer):
    def __init__(self, port, bundle, tuning=TUNING, chambers=CHAMBER_TUNING):
        super().__init__(('127.0.0.1', port), EditorHandler)
        self.bundle = bundle
        self.tuning = tuning
        self.chambers = chambers
        self.write_lock = Lock()
        self.host = f'127.0.0.1:{self.server_port}'
        self.origin = 'http://' + self.host

    def catalog_path(self, route):
        return self.chambers if route == CHAMBERS else self.tuning

    def catalog(self, route, source):
        if route == CHAMBERS:
            metadata = read_catalog(self.bundle, source.decode('utf-8'), '--validate-chambers')
            data = {**metadata, 'catalog': json.loads(source)}
        else:
            data = read_catalog(self.bundle, source.decode('utf-8'))
        return self.response(data, source)

    @staticmethod
    def response(data, source):
        return {**data, 'revision': revision(source)}


class EditorHandler(BaseHTTPRequestHandler):
    def send(self, status, content, mime):
        self.send_response(status)
        self.send_header('Content-Type', mime + '; charset=utf-8')
        self.send_header('Content-Length', str(len(content)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Security-Policy',
                         "default-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'")
        self.end_headers()
        self.wfile.write(content)

    def send_json(self, status, value):
        self.send(status, json.dumps(value, ensure_ascii=False).encode('utf-8'), 'application/json')

    def do_GET(self):
        if self.headers.get('Host') != self.server.host:
            self.send_json(HTTPStatus.FORBIDDEN, {'error': 'Open the editor at its loopback address.'})
        elif self.path in FILES:
            name, mime = FILES[self.path]
            self.send(HTTPStatus.OK, (ASSETS / name).read_bytes(), mime)
        elif self.path in (CATALOG, CHAMBERS):
            try:
                self.send_json(HTTPStatus.OK, self.server.catalog(
                    self.path, self.server.catalog_path(self.path).read_bytes()))
            except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
                self.send_json(HTTPStatus.UNPROCESSABLE_ENTITY, {'error': str(error)})
        else:
            self.send_json(HTTPStatus.NOT_FOUND, {'error': 'Unknown route.'})

    def do_PUT(self):
        if self.headers.get('Host') != self.server.host or self.headers.get('Origin') != self.server.origin:
            self.send_json(HTTPStatus.FORBIDDEN, {'error': 'Save from the local editor tab.'})
            return
        if self.path not in (CATALOG, CHAMBERS):
            self.send_json(HTTPStatus.NOT_FOUND, {'error': 'Unknown route.'})
            return
        try:
            size = int(self.headers.get('Content-Length', '0'))
            if not 0 < size <= 262144:
                raise ValueError('Expected a JSON save request of at most 256 KiB.')
            request = json.loads(self.rfile.read(size))
            if not isinstance(request, dict) or not isinstance(request.get('catalog'), dict):
                raise ValueError('Expected a complete catalog keyed by ID.')
            encoded = (json.dumps(request['catalog'], indent=2, ensure_ascii=False, allow_nan=False) + '\n').encode('utf-8')
            validated = self.server.catalog(self.path, encoded)
            path = self.server.catalog_path(self.path)
            with self.server.write_lock:
                if request.get('revision') != revision(path.read_bytes()):
                    self.send_json(HTTPStatus.CONFLICT, {'error': 'The balance file changed on disk. Reload before saving.'})
                    return
                atomic_write(path, encoded)
            self.send_json(HTTPStatus.OK, validated)
        except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
            self.send_json(HTTPStatus.UNPROCESSABLE_ENTITY, {'error': str(error)})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8765, help='Port on 127.0.0.1')
    parser.add_argument('--sbt', default='sbt', help='Path to the project’s sbt executable')
    parser.add_argument('--skip-build', action='store_true', help='Use an existing balance runner build')
    args = parser.parse_args()
    if not args.skip_build:
        try:
            command = executable_command(
                args.sbt, 'set ThisBuild / offline := true', 'balance/fastOptJS'
            )
            subprocess.run(command, cwd=ROOT, check=True)
        except FileNotFoundError as error:
            parser.error(str(error))
    try:
        bundle = balance_bundle()
        with EditorServer(args.port, bundle) as server:
            print(f'Open {server.origin} in your browser. Press Ctrl+C here to stop.', flush=True)
            server.serve_forever()
    except KeyboardInterrupt:
        pass
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == '__main__':
    main()
