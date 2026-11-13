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
    # A studied site can still lack a collection prerequisite (resin needs seep study 3).
    load_game(page, ring)
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="fallen-pine"]').click()
    expect(page.locator('#map-worker-count')).to_have_count(0)
    expect(page.get_by_role('button', name='Collect Resin', exact=True)).to_have_count(0)
    page.locator('.map-hex[data-site="nectar-meadow"]').click()
    page.locator('.hex-map').screenshot(path=str(artifacts / f'ring-{width}.png'))
    expect(page.locator('.expedition-supplies')).not_to_be_visible()
    page.get_by_role('tab', name='Upgrades', exact=True).click()
    supplies = page.get_by_role('button', name='Expedition Supplies 1', exact=True)
    expect(supplies).to_be_visible()
    supplies.click()
    expect(supplies).to_have_count(0)
    purchased = page.locator('.purchased-upgrades')
    expect(purchased).not_to_have_attribute('open', '')
    purchased.locator('summary').first.click()
    expect(purchased).to_contain_text('Expedition Supplies 1')
    expect(purchased.locator('.purchased-upgrade').filter(has_text='Expedition Supplies 1')).to_have_count(1)
    page.clock.run_for(600)
    expect(purchased).to_have_attribute('open', '')
    bought = save_game(page)
    assert next(u for u in bought['upgrades']['upgrades'] if u['name'] == 'ExpeditionSupplies1')['unlocked']
    load_game(page, bought)
    page.get_by_role('tab', name='Upgrades', exact=True).click()
    expect(page.get_by_role('button', name='Expedition Supplies 1', exact=True)).to_have_count(0)
    expect(page.get_by_role('button', name='Expedition Supplies 2', exact=True)).to_be_visible()
    expect(page.locator('.purchased-upgrades')).not_to_have_attribute('open', '')
    page.locator('.purchased-upgrades > summary').click()
    expect(page.locator('.purchased-upgrades')).to_contain_text('Expedition Supplies 1')
    page.get_by_role('button', name='Expedition Supplies 2', exact=True).click()
    expect(page.locator('.purchased-upgrades')).to_have_attribute('open', '')
    expect(page.locator('.purchased-upgrades')).to_contain_text('Expedition Supplies 2')
    expect(page.get_by_role('button', name='Expedition Supplies 2', exact=True)).to_have_count(0)
    assert_fits(page, width)
    page.locator('.purchased-upgrades').screenshot(path=str(artifacts / f'purchased-{width}.png'))
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.locator('.expedition-supplies')).not_to_be_visible()
    check_trails(page, width, ring)
    assert not errors, errors
    print(f'PASS map {width}px: hex geometry, scout reveal/study, finite knowledge, selected-site controls, capacity block, depleted site and save reload', flush=True)


def check_trails(page, width, base):
    game = copy.deepcopy(base)
    game['ants'].update(workers=100 * UNIT, scouts=5 * UNIT, smallWorkers=0,
        tasks=[[{'SugarCollector': {}}, 100 * UNIT]], defaultTask={'SugarCollector': {}},
        lineage={'foundings': 1, 'dna': 0, 'sharedPaths': True})
    game['basicResources']['sugar'].update(amount=10000 * UNIT, maxAmount=100000 * UNIT)
    for key in ['protein', 'water', 'resin']:
        game['basicResources'][key].update(amount=0, maxAmount=100000 * UNIT)
    game['exploration'].update(collectionTrails=[], depleted={}, explorationParties=[])
    load_game(page, game)
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="nectar-meadow"]').click()
    expect(page.locator('.pheromone-trails')).to_have_count(0)
    crew = page.locator('#map-worker-count')
    expect(crew).to_have_attribute('min', '5')
    expect(crew).to_have_attribute('max', '50')
    expect(crew).to_have_attribute('step', '5')
    crew.fill('7')
    crew.press('Tab')
    expect(crew).to_have_value('5')
    page.get_by_role('button', name='Increase collection workers', exact=True).click()
    expect(crew).to_have_value('10')
    page.get_by_role('button', name='Decrease collection workers', exact=True).click()
    expect(crew).to_have_value('5')
    trail = page.locator('.collection-trail[data-route="nectar-meadow"]')
    add = page.get_by_role('button', name='Assign 5 trail workers to Nectar Meadow', exact=True)
    for _ in range(3):
        add.click()
    expect(trail.locator('.trail-staffing')).to_have_text('15/50 workers')
    expect(page.locator('.scout-crew-control')).to_contain_text('3 / 5 available')
    for _ in range(7):
        add.click()
    expect(trail.locator('.trail-staffing')).to_have_text('50/50 workers')
    expect(add).to_be_disabled()
    expect(page.locator('.scout-crew-control')).to_contain_text('0 / 5 available')
    trail.get_by_label('About Collection trail', exact=True).click()
    page.clock.run_for(1000)
    expect(trail.locator('.info-details')).to_have_attribute('open', '')
    active = save_game(page)
    assert active['exploration']['collectionTrails'] == [{'destination': 'nectar-meadow', 'workers': 50 * UNIT}]
    assert active['exploration']['depleted']['nectar-meadow'] > 0
    assert active['exploration']['explorationParties'] == []
    page.get_by_role('tab', name='Explore', exact=True).click()
    # Manual collection remains available alongside a trail, using the same deposit.
    page.get_by_role('button', name='Collect Nectar', exact=True).click()
    trail.get_by_role('button', name='Recall trail', exact=True).click()
    page.locator('.map-hex[data-site="woodland-edge"]').click()
    page.get_by_role('button', name='Assign 5 trail workers to Woodland Edge', exact=True).click()
    page.clock.run_for(1000)
    saved = save_game(page)
    assert len(saved['exploration']['explorationParties']) == 1
    assert saved['basicResources']['protein']['amount'] > 0
    load_game(page, saved)
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="woodland-edge"]').click()
    wood = page.locator('.collection-trail[data-route="woodland-edge"]')
    expect(wood.locator('.trail-staffing')).to_have_text('5/50 workers')
    full = copy.deepcopy(saved)
    full['basicResources']['protein']['amount'] = full['basicResources']['protein']['maxAmount']
    load_game(page, full)
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="woodland-edge"]').click()
    expect(wood.locator('.trail-status')).to_have_text('Storage full')
    page.clock.run_for(1000)
    paused = save_game(page)
    assert paused['exploration']['depleted'] == full['exploration']['depleted']
    no_scouts = copy.deepcopy(saved)
    no_scouts['ants']['scouts'] = 0
    load_game(page, no_scouts)
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="woodland-edge"]').click()
    expect(wood.locator('.trail-status')).to_have_text('Needs scouts')
    assert_fits(page, width)
    artifacts = Path(os.environ.get('ANTIDLE_ARTIFACTS', '/tmp/antidle-map-browser'))
    artifacts.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(artifacts / f'trails-{width}.png'), full_page=True)
    page.locator('.map-site-card').screenshot(path=str(artifacts / f'trails-panel-{width}.png'))
    print(f'PASS trails {width}px: 5-worker controls, guide reservations, collection, manual coexistence, reload and pauses', flush=True)


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
