"""Manual browser check for a generated balance report and its real-game saves.
Usage: python3 tests/balance_browser_smoke.py /tmp/balance-output
Requires fullOptJS and tests/requirements-browser.txt.
"""
import argparse
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import json
import os
from pathlib import Path
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, QuietHandler, load_game, save_game
from ci_browser_smoke import ProductionHandler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    args = parser.parse_args()
    folder = args.report.resolve()
    report = json.loads((folder / 'report.json').read_text())
    assert report['runs']
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(ProductionHandler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    report_server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(folder)))
    Thread(target=report_server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as playwright:
            launch = dict(headless=True)
            chromium = os.environ.get('CHROMIUM_PATH') or shutil.which('chromium')
            if chromium:
                launch['executable_path'] = chromium
            proxy = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
            if proxy:
                launch['proxy'] = dict(server=proxy, bypass='127.0.0.1,localhost')
            browser = playwright.chromium.launch(**launch)
            try:
                for width in (390, 1280):
                    page = browser.new_page(viewport=dict(width=width, height=844))
                    errors = []
                    page.on('pageerror', lambda error: errors.append(str(error)))
                    page.goto(f'http://127.0.0.1:{report_server.server_port}/index.html')
                    expect(page.locator('#summary')).to_contain_text('strategic decisions')
                    for i, run in enumerate(report['runs']):
                        page.locator('#run').select_option(str(i))
                        expect(page.locator('#summary')).to_contain_text(run['status'])
                        assert page.locator('#milestones tr').count() == len(run['milestones'])
                    page.locator('#metric').select_option('careWorkers')
                    expect(page.locator('#extent')).to_contain_text('careWorkers')
                    assert page.locator('#chart polyline').get_attribute('points')
                    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')
                    page.close()
                    page = browser.new_page(viewport=dict(width=width, height=844))
                    page.on('pageerror', lambda error: errors.append(str(error)))
                    now = datetime.now(timezone.utc)
                    page.clock.install(time=now)
                    page.clock.pause_at(now + timedelta(seconds=1))
                    page.goto(f'http://127.0.0.1:{server.server_port}', wait_until='networkidle')
                    # Load each milestone family from real simulation, including the reset.
                    seen = set()
                    for run in report['runs']:
                        for milestone in run['milestones']:
                            if milestone['name'] in seen:
                                continue
                            seen.add(milestone['name'])
                            data = json.loads((folder / 'checkpoints' / milestone['checkpoint']).read_text())
                            load_game(page, data)
                            saved = save_game(page)
                            assert saved['world']['currentTick'] == data['world']['currentTick']
                            for field in ('workers', 'smallWorkers', 'daughterQueens', 'lineage'):
                                assert saved['ants'][field] == data['ants'][field], (milestone, field)
                    assert not errors, errors
                    print(f'PASS balance report {width}px: all run selections, chart, layout, {len(seen)} checkpoint families loaded in production game', flush=True)
                    page.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        report_server.shutdown()


if __name__ == '__main__':
    main()
