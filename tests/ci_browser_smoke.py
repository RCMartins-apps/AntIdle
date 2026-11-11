"""Small production-bundle browser gate for mobile and desktop regressions."""
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
from pathlib import Path
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game, select_expedition


class ProductionHandler(QuietHandler):
    def translate_path(self, path):
        return super().translate_path(path.replace('/antidle-fastopt/', '/antidle-opt/'))


def check_game(page, width, url):
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    now = datetime(2026, 11, 9, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    gather = page.get_by_role('button', name='Gather Sugar', exact=True)
    expect(gather).to_have_css('background-color', 'rgb(13, 110, 253)')
    for _ in range(5):
        gather.click()
    expect(page.get_by_role('button', name='Raise Small Worker Egg', exact=True)).to_be_enabled()
    page.get_by_label('Details for Raise Small Worker Egg', exact=True).click()
    expect(page.locator('.action-details[open]')).to_contain_text('Shared laying queue')
    page.clock.run_for(400)
    expect(page.locator('.action-details[open]')).to_have_count(1)
    assert_fits(page, width)
    # Egg orders require housing, not resources; costs are paid at laying.
    unfunded = progressed_game(save_game(page))
    unfunded['ants'].update(workers=0, smallWorkers=0, scouts=0, tasks=[],
        eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[], layingCooldown=10)
    unfunded['basicResources']['sugar']['amount'] = 0
    unfunded['basicResources']['protein']['amount'] = 0
    unfunded['nestAttributes']['maxAnts'] = 2 * UNIT
    unfunded['nestAttributes']['chambers']['nurseryChamber']['level'] = 1
    load_game(page, unfunded)
    worker = page.get_by_role('button', name='Raise Worker Egg', exact=True)
    scout = page.get_by_role('button', name='Raise Scout Egg', exact=True)
    expect(worker).to_be_enabled()
    expect(scout).to_be_enabled()
    worker.click()
    expect(scout).to_be_enabled()
    scout.click()
    expect(worker).to_be_disabled()
    expect(scout).to_be_disabled()
    queued = save_game(page)
    assert queued['ants']['eggOrders'] == 1
    assert queued['ants']['scoutEggOrders'] == [1]
    assert queued['ants']['eggsAndLarvae'] == []
    assert queued['basicResources']['sugar']['amount'] == 0
    assert queued['basicResources']['protein']['amount'] == 0
    # Shared research validation: reveal independently of affordability, then
    # enable at the displayed price and remove the one-time purchase.
    research = progressed_game(save_game(page))
    research['ants'].update(workers=0, smallWorkers=0, tasks=[], eggsAndLarvae=[])
    research['nestAttributes']['chambers']['nestChamber']['level'] = 1
    research['unlocks']['tabs']['exploreTabUnlocked'] = False
    research['basicResources']['sugar']['amount'] = 149 * UNIT
    for upgrade in research['upgrades']['upgrades']:
        upgrade.update(show=False, unlocked=False)
    load_game(page, research)
    page.get_by_role('tab', name='Upgrades', exact=True).click()
    foraging = page.get_by_role('button', name='Foraging Techniques 1', exact=True)
    expect(foraging).to_be_disabled()
    expect(page.get_by_role('button', name='Foraging Techniques 2', exact=True)).to_have_count(0)
    page.get_by_role('tab', name='Nest', exact=True).click()
    page.get_by_role('button', name='Gather Sugar', exact=True).click()
    page.get_by_role('tab', name='Upgrades', exact=True).click()
    expect(foraging).to_be_enabled()
    foraging.click()
    expect(foraging).to_have_count(0)
    researched = save_game(page)
    assert researched['basicResources']['sugar']['amount'] == 0
    assert next(u for u in researched['upgrades']['upgrades'] if u['name'] == 'ImproveSugarCollectorTask1')['unlocked']
    game = progressed_game(save_game(page))
    game['ants'].update(workers=50 * UNIT, scouts=2 * UNIT, daughterQueens=UNIT,
        tasks=[[{'SugarCollector': {}}, 40 * UNIT], [{'Tunneler': {}}, 10 * UNIT], [{'Explorer': {}}, 0]],
        defaultTask={'SugarCollector': {}}, eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[])
    game['nestAttributes']['chambers']['nurseryChamber']['level'] = 1
    game['nestAttributes']['chambers']['royalNurseryChamber']['level'] = 1
    game['exploration'].update(revealedSites=6, proteinDiscovered=True, surveys={'dry-clearing': 3})
    for upgrade in game['upgrades']['upgrades']:
        upgrade.update(show=False, unlocked=upgrade['name'] == 'DefaultWorkers')
    load_game(page, game)
    page.get_by_label('Details for Establish Dry Clearing Outpost', exact=True).click()
    outpost = page.locator('.nest-outposts .action-details')
    page.clock.run_for(600)
    expect(outpost).to_have_attribute('open', '')
    outpost.locator('summary').click()
    page.get_by_role('tab', name='Tasks').click()
    for row in page.locator('.task-row:visible').all():
        bounds, info = row.bounding_box(), row.locator('.info-summary').bounding_box()
        assert info['height'] >= 44
        assert info['y'] >= bounds['y'] and info['y'] + info['height'] <= bounds['y'] + bounds['height'] + 1
    # Replacing native select options must retain the actual default task.
    unlocking = save_game(page)
    unlocking['nestAttributes']['chambers']['nurseryChamber']['level'] = 0
    unlocking['ants']['tasks'] = [[{'SugarCollector': {}}, 40 * UNIT], [{'Tunneler': {}}, 10 * UNIT]]
    load_game(page, unlocking)
    page.get_by_role('tab', name='Tasks').click()
    expect(page.locator('#default-worker-task')).to_have_value('SugarCollector')
    page.get_by_role('tab', name='Nest', exact=True).click()
    page.get_by_role('button', name='Nursery Chamber', exact=True).click()
    page.get_by_role('tab', name='Tasks').click()
    page.clock.run_for(600)
    expect(page.locator('#default-worker-task option[value="BroodNurse"]')).to_have_count(1)
    expect(page.locator('#default-worker-task')).to_have_value('SugarCollector')
    assert save_game(page)['ants']['defaultTask'] == {'SugarCollector': {}}
    load_game(page, game)
    page.get_by_role('tab', name='Explore', exact=True).click()
    # A fresh ring reveals through scout-only missions, with identical mobile/desktop rules.
    game['exploration'].update(revealedSites=0, surveys={}, depleted={}, explorationParties=[])
    load_game(page, game)
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.locator('.hex-map .map-hex')).to_have_count(7)
    expect(page.locator('.hex-map .map-hex:disabled')).to_have_count(6)
    expect(page.locator('.exploration-progress')).to_have_count(0)
    page.locator('#scout-crew').select_option('2')
    expect(page.locator('.map-site-card .action-costs').first).to_be_visible()
    assert_fits(page, width)
    page.get_by_role('button', name='Explore Surroundings', exact=True).click()
    sent = save_game(page)
    party = sent['exploration']['explorationParties'][0]
    assert party['mission'] == 'reveal' and party['scouts'] == 2 * UNIT and party['amountWorkers'] == 0
    assert 'experience' not in sent['exploration']
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.clock.run_for(31000)
    expect(page.locator('.map-hex[data-site="woodland-edge"]')).to_be_enabled()
    page.locator('.map-hex[data-site="woodland-edge"]').click()
    expect(page.get_by_role('button', name='Study Site', exact=True)).to_be_enabled()
    expect(page.get_by_role('button', name='Collect Insects', exact=True)).to_have_count(0)
    assert_fits(page, width)
    page.get_by_role('tab', name='Lineage', exact=True).click()
    page.get_by_text('Review founding', exact=True).click()
    expect(page.locator('.founding-preview')).to_contain_text('Reset:')
    page.once('dialog', lambda dialog: dialog.accept())
    page.get_by_role('button', name='Found daughter colony', exact=True).click()
    founded = save_game(page)
    assert founded['ants']['lineage']['foundings'] == 1
    assert not founded['ants']['standardWorkersUnlocked']
    assert founded['exploration']['revealedSites'] == 0 and not founded['exploration']['surveys']
    page.get_by_role('tab', name='Nest', exact=True).click()
    expect(page.get_by_role('button', name='Nursery Chamber', exact=False)).to_have_count(0)
    protein = page.locator('.resource-row').filter(has=page.get_by_text('Protein', exact=True))
    expect(protein).to_be_visible()
    page.reload(wait_until='networkidle')
    expect(page.get_by_role('button', name='Nursery Chamber', exact=False)).to_have_count(0)
    expect(protein).to_be_visible()
    assert_fits(page, width)
    assert not errors, errors
    print(f'PASS production {width}px: founding labels, disclosures, task geometry, research prices/purchase, hex map/scout launch/reveal, prestige/reset and resource visibility', flush=True)


if __name__ == '__main__':
    assert (ROOT / 'target/scala-2.13/antidle-opt/main.js').exists(), 'Run sbt fullOptJS first'
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(ProductionHandler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as playwright:
            launch = {'headless': True}
            chromium = os.environ.get('CHROMIUM_PATH')
            if not chromium and not os.environ.get('GITHUB_ACTIONS'):
                chromium = shutil.which('chromium')
            if chromium:
                launch['executable_path'] = chromium
            proxy = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
            if proxy:
                launch['proxy'] = {'server': proxy, 'bypass': '127.0.0.1,localhost'}
            browser = playwright.chromium.launch(**launch)
            try:
                for width in (390, 1280):
                    context = browser.new_context(viewport={'width': width, 'height': 844}, has_touch=width <= 767, is_mobile=width <= 767)
                    page = context.new_page()
                    try:
                        check_game(page, width, f'http://127.0.0.1:{server.server_port}')
                    except Exception:
                        artifacts = Path(os.environ.get('ANTIDLE_ARTIFACTS', 'test-results'))
                        artifacts.mkdir(parents=True, exist_ok=True)
                        try:
                            page.screenshot(path=str(artifacts / f'browser-{width}-failure.png'), full_page=True, timeout=5000)
                        except Exception:
                            pass
                        raise
                    finally:
                        context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
