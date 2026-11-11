"""Handcrafted hex exploration: phone/desktop controls, deterministic reveals,
finite knowledge/resources, save reload and stable selection. Run after fastOptJS.
Use ANTIDLE_PRODUCTION=1 after fullOptJS for the production bundle.
"""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
from pathlib import Path
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game
from ci_browser_smoke import ProductionHandler


def check_map(page, width, url):
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    now = datetime(2026, 11, 10, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    initial = save_game(page)
    assert initial['version'] == 7
    game = progressed_game(initial)
    game['ants'].update(workers=40 * UNIT, scouts=3 * UNIT, smallWorkers=0,
        tasks=[[{'SugarCollector': {}}, 40 * UNIT]], defaultTask={'SugarCollector': {}},
        eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[])
    game['basicResources']['knowledge']['amount'] = 0
    game['nestAttributes']['chambers']['nurseryChamber']['level'] = 1
    game['exploration'].update(revealedSites=0, surveys={}, depleted={}, explorationParties=[])
    for upgrade in game['upgrades']['upgrades']:
        upgrade.update(show=False, unlocked=upgrade['name'] == 'DefaultWorkers')
    load_game(page, game)
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.locator('.hex-map .map-hex')).to_have_count(7)
    expect(page.locator('.hex-map .map-terrain-icon svg')).to_have_count(7)
    expect(page.locator('.hex-map .map-hex:disabled')).to_have_count(6)
    for tile in page.locator('.hex-map .map-hex').all():
        box = tile.bounding_box()
        assert box['width'] >= 44 and box['height'] >= 44
    expect(page.locator('.exploration-progress')).to_have_count(0)
    page.get_by_role('button', name='3 scouts', exact=True).click()
    page.get_by_role('button', name='Explore Surroundings', exact=True).click()
    sent = save_game(page)
    party = sent['exploration']['explorationParties'][0]
    assert party['scouts'] == 3 * UNIT and party['amountWorkers'] == 0 and party['mission'] == 'reveal'
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.get_by_role('button', name='Explore Surroundings', exact=True)).to_be_disabled()
    page.clock.run_for(21000)
    expect(page.locator('.hex-map .map-hex:disabled')).to_have_count(5)
    tile = page.locator('.map-hex[data-site="woodland-edge"]')
    tile.click()
    expect(tile).to_have_attribute('aria-pressed', 'true')
    expect(page.locator('.site-knowledge')).to_have_text('800 knowledge remaining')
    expect(page.locator('#map-worker-count')).to_have_count(0)
    assert page.locator('#scout-crew').bounding_box()['y'] >= page.locator('.hex-map').bounding_box()['y'] + page.locator('.hex-map').bounding_box()['height']
    page.get_by_role('button', name='Study Site', exact=True).click()
    page.clock.run_for(61000)
    expect(page.locator('.site-knowledge')).to_have_text('400 knowledge remaining')
    expect(tile).to_have_attribute('aria-pressed', 'true')
    expect(page.get_by_role('button', name='Collect Insects', exact=True)).to_be_visible()
    # No scrolling through unrelated destinations: only selected-site actions appear.
    expect(page.get_by_role('button', name='Collect Resin', exact=True)).to_have_count(0)
    assert_fits(page, width)
    studied = save_game(page)
    assert studied['basicResources']['knowledge']['amount'] == 400 * UNIT
    assert studied['exploration']['surveys']['woodland-edge'] == 1
    assert 'experience' not in studied['exploration']
    load_game(page, studied)
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="woodland-edge"]').click()
    expect(page.locator('.site-knowledge')).to_have_text('400 knowledge remaining')
    # At capacity collection is blocked; no finite deposit is wasted.
    full = copy.deepcopy(studied)
    full['basicResources']['protein']['amount'] = full['basicResources']['protein']['maxAmount']
    load_game(page, full)
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="woodland-edge"]').click()
    expect(page.get_by_role('button', name='Collect Insects', exact=True)).to_be_disabled()
    # Exhausted hex remains visible, with no scout/worker mission or crew control.
    depleted = copy.deepcopy(studied)
    depleted['exploration'].update(surveys={'woodland-edge': 3}, depleted={'woodland-edge': 800 * UNIT})
    load_game(page, depleted)
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="woodland-edge"]').click()
    expect(page.locator('.map-site-card')).to_contain_text('Site exhausted')
    expect(page.locator('.map-hex[data-site="woodland-edge"]')).to_have_accessible_name('Woodland Edge: exhausted')
    expect(page.locator('.map-site-card button')).to_have_count(0)
    expect(page.locator('#map-worker-count')).to_have_count(0)
    assert_fits(page, width)
    artifacts = Path(os.environ.get('ANTIDLE_ARTIFACTS', '/tmp/antidle-map-browser'))
    artifacts.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(artifacts / f'map-{width}.png'), full_page=True)
    ring = copy.deepcopy(studied)
    ring['exploration'].update(revealedSites=6, surveys={key: 1 for key in ['woodland-edge', 'nectar-meadow', 'rotten-log', 'dry-clearing', 'fallen-pine', 'rival-food-cache']}, depleted={})
    # A studied site can still lack a collection prerequisite (resin needs seep study 2).
    load_game(page, ring)
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="fallen-pine"]').click()
    expect(page.locator('#map-worker-count')).to_have_count(0)
    expect(page.get_by_role('button', name='Collect Resin', exact=True)).to_have_count(0)
    page.locator('.map-hex[data-site="nectar-meadow"]').click()
    page.locator('.hex-map').screenshot(path=str(artifacts / f'ring-{width}.png'))
    expect(page.locator('.expedition-supplies')).not_to_be_visible()
    page.get_by_role('tab', name='Upgrades', exact=True).click()
    supplies = page.get_by_role('button', name='Improve Expedition Supplies', exact=True)
    expect(supplies).to_be_visible()
    supplies.click()
    expect(page.locator('.expedition-supplies')).to_contain_text('Rank 1 /')
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.locator('.expedition-supplies')).not_to_be_visible()
    assert not errors, errors
    print(f'PASS map {width}px: hex geometry, scout reveal/study, finite knowledge, selected-site controls, capacity block, depleted site and save reload', flush=True)


def main():
    handler = ProductionHandler if os.environ.get('ANTIDLE_PRODUCTION') else QuietHandler
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(handler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as playwright:
            launch = {'headless': True}
            chromium = os.environ.get('CHROMIUM_PATH') or shutil.which('chromium')
            if chromium:
                launch['executable_path'] = chromium
            proxy = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
            if proxy:
                launch['proxy'] = {'server': proxy, 'bypass': '127.0.0.1,localhost'}
            browser = playwright.chromium.launch(**launch)
            try:
                for width in (320, 390, 1280):
                    page = browser.new_page(viewport={'width': width, 'height': 844}, has_touch=width <= 767, is_mobile=width <= 767)
                    try:
                        check_map(page, width, f'http://127.0.0.1:{server.server_port}')
                    except Exception:
                        artifacts = Path(os.environ.get('ANTIDLE_ARTIFACTS', '/tmp/antidle-map-browser'))
                        artifacts.mkdir(parents=True, exist_ok=True)
                        page.screenshot(path=str(artifacts / f'map-{width}-failure.png'), full_page=True)
                        raise
                    finally:
                        page.close()
            finally:
                browser.close()
    finally:
        server.shutdown()


if __name__ == '__main__':
    main()
