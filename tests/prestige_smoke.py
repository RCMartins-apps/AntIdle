"""Staged founding, atomic Understanding selection and second-ring research. Run after fastOptJS."""
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


def check_prestige(page, width, url):
    now = datetime(2026, 11, 14, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto(url, wait_until='networkidle')
    initial = save_game(page)
    game = progressed_game(initial)
    game['ants'].update(workers=20 * UNIT, scouts=3 * UNIT, daughterQueens=UNIT,
        lineage={'foundings': 0, 'dna': 0, 'sharedPaths': False},
        tasks=[[{'SugarCollector': {}}, 20 * UNIT], [{'Explorer': {}}, 0]],
        defaultTask={'SugarCollector': {}}, royalBrood=None, eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[])
    game['statistics']['sugarU'] = {'thisRun': 100 * UNIT, 'total': 300 * UNIT}
    for upgrade in game['upgrades']['upgrades']:
        upgrade.update(show=False, unlocked=upgrade['name'] in ['DefaultWorkers', 'AphidTending'])
    load_game(page, game)
    page.get_by_role('tab', name='Lineage', exact=True).click()
    expect(page.locator('.founding-rewards')).to_contain_text('0 + 1 = 1')
    expect(page.locator('.founding-rewards')).to_contain_text('+0% → +10%')
    page.get_by_role('button', name='Choose Understandings', exact=True).click()
    expect(page.get_by_role('checkbox', name='Select Beyond the Woodland', exact=True)).to_have_count(0)
    page.get_by_role('checkbox', name='Select Shared Paths', exact=True).check()
    expect(page.locator('.founding-rewards')).to_contain_text('DNA carried forward0')
    page.get_by_role('button', name='Back to rewards', exact=True).click()
    unchanged = save_game(page)
    assert unchanged['ants']['lineage']['dna'] == 0
    assert not unchanged['ants']['lineage']['sharedPaths']
    assert unchanged['ants']['daughterQueens'] == UNIT
    page.get_by_role('tab', name='Lineage', exact=True).click()
    page.get_by_role('button', name='Choose Understandings', exact=True).click()
    page.get_by_role('button', name='Review founding', exact=True).click()
    found = page.get_by_role('button', name='Found daughter colony', exact=True)
    expect(found).to_be_disabled()
    page.get_by_role('checkbox', name='Confirm colony reset', exact=True).check()
    found.click()
    page.clock.run_for(1)
    first = save_game(page)
    assert first['ants']['lineage'] == {'foundings': 1, 'dna': 0, 'sharedPaths': True, 'beyondWoodland': False}
    assert first['ants']['workers'] == 0 and first['ants']['daughterQueens'] == 0
    assert first['basicResources']['sugar']['amount'] == 0
    assert first['statistics']['sugarU'] == {'thisRun': 0, 'total': 300 * UNIT}
    second = copy.deepcopy(game)
    second['ants']['lineage'] = first['ants']['lineage']
    load_game(page, second)
    page.get_by_role('tab', name='Lineage', exact=True).click()
    page.get_by_role('button', name='Choose Understandings', exact=True).click()
    page.get_by_role('checkbox', name='Select Beyond the Woodland', exact=True).check()
    page.locator('.understanding-research > summary').click()
    expect(page.locator('.understanding-research')).to_contain_text('10 → 20 aphid places')
    expect(page.locator('.understanding-research')).to_contain_text('60% → 75%')
    page.clock.run_for(1000)
    expect(page.locator('.understanding-research')).to_have_attribute('open', '')
    artifacts = Path(os.environ.get('ANTIDLE_ARTIFACTS', '/tmp/antidle-prestige-browser'))
    artifacts.mkdir(parents=True, exist_ok=True)
    assert_fits(page, width)
    page.locator('#lineageContent').screenshot(path=str(artifacts / f'choices-{width}.png'))
    page.get_by_role('button', name='Review founding', exact=True).click()
    expect(page.locator('.founding-rewards')).to_contain_text('+10% → +20%')
    expect(page.locator('.selected-understandings')).to_contain_text('Beyond the Woodland')
    page.locator('.founding-reset > summary').click()
    expect(page.locator('.founding-reset')).to_contain_text('Standard workers20 → 0')
    page.get_by_role('checkbox', name='Confirm colony reset', exact=True).check()
    found.click()
    page.clock.run_for(1)
    founded = save_game(page)
    assert founded['ants']['lineage']['beyondWoodland']
    assert founded['ants']['lineage']['dna'] == 0 and founded['ants']['lineage']['foundings'] == 2
    later = progressed_game(founded)
    later['ants'].update(workers=40 * UNIT, scouts=3 * UNIT, tasks=[[{'SugarCollector': {}}, 40 * UNIT]])
    later['exploration'].update(revealedSites=6, surveys={key: 3 for key in ['woodland-edge','nectar-meadow','rotten-log','dry-clearing','fallen-pine','rival-food-cache']})
    later['nestAttributes'].update(aphids=10, aphidsInitialized=True)
    later['nestAttributes']['chambers']['aphidChamber']['level'] = 1
    for upgrade in later['upgrades']['upgrades']:
        upgrade.update(show=False, unlocked=upgrade['name'] in ['DefaultWorkers','AphidTending'])
    load_game(page, later)
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.locator('.hex-map .map-hex')).to_have_count(10)
    page.get_by_role('button', name='3 scouts', exact=True).click()
    for site, title in [('aphid-thicket','Aphid Shelters'),('fallen-oak','Trail Logistics'),('root-hollow','Coordinated Expeditions')]:
        page.get_by_role('button', name='Nest surroundings', exact=True).click()
        page.get_by_role('button', name='Explore Surroundings', exact=True).click()
        page.clock.run_for(21000)
        tile = page.locator(f'.map-hex[data-site="{site}"]')
        expect(tile).to_be_enabled()
        tile.click()
        expect(page.locator('.site-unlock')).to_contain_text(title)
        for _ in range(3):
            page.get_by_role('button', name='Study Site', exact=True).click()
            page.clock.run_for(61000)
        expect(page.locator('.site-knowledge')).to_have_text('0 knowledge remaining')
        page.get_by_role('tab', name='Upgrades', exact=True).click()
        purchase = page.get_by_role('button', name=title, exact=True)
        expect(purchase).to_be_enabled()
        purchase.click()
        expect(purchase).to_have_count(0)
        page.get_by_role('tab', name='Explore', exact=True).click()
    for tile in page.locator('.hex-map .map-hex').all():
        box = tile.bounding_box()
        assert box['width'] >= 44 and box['height'] >= 44, box
    assert_fits(page, width)
    page.locator('.hex-map').screenshot(path=str(artifacts / f'outer-map-{width}.png'))
    upgraded = save_game(page)
    assert upgraded['nestAttributes']['aphidPlacesPerLevel'] == 20
    assert upgraded['nestAttributes']['aphids'] == 10
    assert upgraded['exploration']['revealedSites'] == 9
    assert not errors, errors
    print(f'PASS prestige {width}px: staged founding, no preview charges, permanent selection, second ring and all three research purchases', flush=True)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 0), partial(ProductionHandler if os.environ.get("ANTIDLE_PRODUCTION") else QuietHandler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as p:
            launch = {"headless": True}
            chromium = os.environ.get("CHROMIUM_PATH") or shutil.which("chromium")
            if chromium:
                launch["executable_path"] = chromium
            proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
            if proxy:
                launch["proxy"] = {"server": proxy, "bypass": "127.0.0.1,localhost"}
            browser = p.chromium.launch(**launch)
            try:
                for width in (320, 390, 1280):
                    context = browser.new_context(viewport={"width": width, "height": 844}, has_touch=width <= 767, is_mobile=width <= 767)
                    check_prestige(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
