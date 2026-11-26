"""Small production-bundle browser gate for mobile and desktop regressions."""
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
from pathlib import Path
import shutil
import subprocess
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import expand_resources, select_tab, ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game, select_expedition


class ProductionHandler(QuietHandler):
    def translate_path(self, path):
        return super().translate_path(path.replace('/antidle-fastopt/', '/antidle-opt/'))




def check_build_version(page, width):
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    footer = page.locator('.game-build-footer .build-version')
    expect(footer).to_contain_text('Build ' + revision[:8])
    expect(footer.locator('a')).to_have_attribute('href', 'https://github.com/RCMartins-apps/AntIdle/commit/' + revision)
    displayed = footer.inner_text()
    select_tab(page, 'Settings')
    expect(page.locator('.settings-build .build-version')).to_have_text(displayed)
    page.clock.run_for(1000)
    expect(footer).to_have_text(displayed)
    page.reload(wait_until='networkidle')
    expect(footer).to_have_text(displayed)
    assert_fits(page, width)
    print(f'PASS build version {width}px: compiled commit, Settings/footer agreement and reload', flush=True)



def check_milestones(page, width, base):
    game = progressed_game(base)
    game['ants'].update(workers=20 * UNIT, smallWorkers=0, scouts=3 * UNIT, daughterQueens=0,
        tasks=[[{'SugarCollector': {}}, 20 * UNIT]], defaultTask={'SugarCollector': {}},
        eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[], royalBrood=None,
        lineage={'foundings': 0, 'dna': 0, 'sharedPaths': False, 'beyondWoodland': False})
    game['ants']['queenRoutine']['enabled'] = False
    game['nestAttributes']['chambers']['nurseryChamber']['level'] = 1
    game['exploration'].update(revealedSites=0, surveys={}, explorationParties=[], collectionTrails=[])
    game['statistics']['explorationsCompleted'] = {'thisRun': 19, 'total': 200}
    game['statistics']['workersMatured'] = {'thisRun': 29, 'total': 200}
    game['statistics']['smallWorkersMatured'] = {'thisRun': 5, 'total': 50}
    for upgrade in game['upgrades']['upgrades']:
        upgrade.update(show=False, unlocked=upgrade['name'] in ['UnlockExplorerTask', 'DefaultWorkers', 'BroodCare', 'QueensRoutine'])
    load_game(page, game)
    select_tab(page, 'Upgrades')
    expect(page.locator('.milestone-progress')).to_have_text('19/20 expeditions completed')
    expect(page.get_by_role('button', name='Scout Wayfinding', exact=True)).to_have_count(0)
    assert_fits(page, width)
    select_tab(page, 'Explore')
    page.get_by_role('button', name='3 scouts', exact=True).click()
    page.get_by_role('button', name='Explore Surroundings', exact=True).click()
    page.clock.run_for(21000)
    select_tab(page, 'Upgrades')
    expect(page.locator('.milestone-progress')).to_have_text('24/25 standard workers raised')
    page.get_by_role('button', name='Scout Wayfinding', exact=True).click()
    select_tab(page, 'Explore')
    expect(page.locator('.map-site-card .explore-duration')).to_have_text('16.0s')
    saved = save_game(page)
    saved['ants']['eggsAndLarvae'] = [{'Developing': {'initialTick': saved['world']['currentTick'], 'kind': 'worker', 'care': 600 * UNIT - 1}}]
    load_game(page, saved)
    page.clock.run_for(400)
    select_tab(page, 'Upgrades')
    expect(page.locator('.milestone-progress')).to_have_text('25/50 standard workers raised')
    page.get_by_role('button', name='Nurse Training', exact=True).click()
    trained = save_game(page)
    trained['statistics']['workersMatured']['thisRun'] = 54
    trained['ants']['eggsAndLarvae'] = [{'Developing': {'initialTick': trained['world']['currentTick'], 'kind': 'worker', 'care': 600 * UNIT - 1}}]
    load_game(page, trained)
    page.clock.run_for(400)
    select_tab(page, 'Upgrades')
    expect(page.locator('.milestone-goal')).to_have_count(0)
    page.get_by_role('button', name="Queen's Rhythm", exact=True).click()
    result = save_game(page)
    for name in ['ScoutWayfinding', 'NurseTraining', 'QueensRhythm']:
        assert next(u for u in result['upgrades']['upgrades'] if u['name'] == name)['unlocked']
    print(f'PASS milestones {width}px: compact goals, real completions/births, gated research and live duration preview', flush=True)


def check_permanent_milestones(page, width, base):
    game = progressed_game(base)
    game['statistics'] = {'sugarU': {'thisRun': 99 * UNIT, 'total': 99 * UNIT}}
    game['ants'].update(workers=UNIT, smallWorkers=0, scouts=0, repletes=0, daughterQueens=0,
        tasks=[[{'SugarCollector': {}}, UNIT]], defaultTask={'SugarCollector': {}},
        eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[], royalBrood=None,
        lineage={'foundings': 0, 'dna': 0, 'sharedPaths': False, 'beyondWoodland': False})
    game['ants']['queenRoutine']['enabled'] = False
    game['exploration'].update(explorationParties=[], collectionTrails=[], carcasses=[], retrievals=[])
    for key in ['sugar', 'protein', 'water', 'resin', 'knowledge', 'tunnelingSpace']:
        game['basicResources'][key]['amount'] = 100 * UNIT if key == 'sugar' else 0
    game['basicResources']['sugar']['maxAmount'] = 1000 * UNIT
    load_game(page, game)
    select_tab(page, 'Milestones')
    expect(page.locator('.colony-milestones h5')).to_have_text('Sugar production ×1')
    sugar = page.locator('.achievement-goal').filter(has=page.get_by_text('Sugar gathered', exact=True))
    sugar.locator('summary').click()
    page.clock.run_for(4000)
    expect(page.locator('.colony-milestones h5')).to_have_text('Sugar production ×1.01')
    expect(sugar.get_by_text('✓ ×1.01', exact=True)).to_have_count(1)
    assert sugar.evaluate('(el) => el.open'), 'live ticks must preserve disclosure'
    expect(sugar.locator('summary')).to_contain_text('peak')
    saved = save_game(page)
    peak = saved['statistics']['maxSugarU']['total']
    assert peak >= 100 * UNIT
    saved['basicResources']['sugar']['amount'] = UNIT
    saved['statistics']['sugarU']['thisRun'] = 0
    load_game(page, saved)
    select_tab(page, 'Milestones')
    expect(page.locator('.colony-milestones h5')).to_have_text('Sugar production ×1.01')
    assert save_game(page)['statistics']['maxSugarU']['total'] == peak
    select_tab(page, 'Milestones')
    assert_fits(page, width)
    print(f'PASS permanent milestones {width}px: live tier, multiplicative reward, saved peaks, stable disclosure and lifetime progress', flush=True)


def check_brood_icons(page, width, base):
    game = progressed_game(base)
    tick = game['world']['currentTick']
    game['ants'].update(workers=20 * UNIT, smallWorkers=0, scouts=0,
        tasks=[[{'SugarCollector': {}}, 20 * UNIT]],
        eggsAndLarvae=[
            {'Developing': {'initialTick': tick, 'kind': 'worker', 'care': 99 * UNIT}},
            {'Developing': {'initialTick': tick, 'kind': 'worker', 'care': 100 * UNIT}},
            {'Developing': {'initialTick': tick, 'kind': 'worker', 'care': 500 * UNIT}},
            {'Developing': {'initialTick': tick, 'kind': 'small', 'care': 0}},
            {'Developing': {'initialTick': tick, 'kind': 'scout', 'care': 80 * UNIT}},
            {'Larvae': {'initialTick': tick}},
            {'Pupae': {'initialTick': tick}},
            {'ScoutEgg': {'initialTick': tick}}], eggOrders=0, scoutEggOrders=[])
    game['ants']['queenRoutine']['enabled'] = False
    load_game(page, game)
    row = page.locator('.resource-row').filter(has=page.get_by_text('Brood', exact=True))
    if width <= 767:
        row.locator('.resource-name details > summary').click()
        listing = row
    else:
        row.locator('.resource-name').hover()
        listing = page.locator('.game-tooltip')
    entries = listing.locator('.brood-entry')
    expect(entries).to_have_count(8)
    expect(entries.locator('.resource-icon-eggs')).to_have_count(3)
    expect(entries.locator('.resource-icon-larva')).to_have_count(3)
    expect(entries.locator('.resource-icon-pupa')).to_have_count(2)
    expect(entries.first).to_contain_text('Worker egg')
    expect(entries.nth(3).locator('.brood-entry-progress')).to_have_text('00%')
    expect(entries.first.locator(':scope > :first-child')).to_have_class('brood-entry-progress')
    expect(entries.first.locator('.resource-icon')).to_have_attribute('aria-hidden', 'true')
    paths = entries.locator('.resource-icon svg path').evaluate_all('(nodes) => nodes.map(n => n.getAttribute("d"))')
    assert len(set(paths)) == 3, paths
    assert_fits(page, width)
    page.clock.run_for(1000)
    expect(entries.first).to_contain_text('Worker larva')
    expect(entries.first.locator('.resource-icon-larva')).to_have_count(1)
    if width <= 767:
        expect(row.locator('.resource-name details')).to_have_attribute('open', '')
    print(f'PASS brood icons {width}px: distinct stages, caste/legacy entries and live stage transition', flush=True)


def check_game(page, width, url):
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    now = datetime(2026, 11, 9, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    expand_resources(page)
    expect(page.locator('.resource-row').first).to_be_visible()
    fresh_resources = page.locator('.resource-row:visible .resource-label').all_text_contents()
    small = page.get_by_role('button', name='Raise Small Worker Egg', exact=True)
    expect(small).to_have_css('background-color', 'rgb(13, 110, 253)')
    expect(small).to_be_enabled()
    expect(page.get_by_role('button', name='Gather Sugar', exact=True)).to_have_count(0)
    page.get_by_label('Details for Raise Small Worker Egg', exact=True).click()
    expect(page.locator('.action-details[open]')).to_contain_text('Shared laying queue')
    page.clock.run_for(400)
    expect(page.locator('.action-details[open]')).to_have_count(1)
    assert_fits(page, width)
    small.click()
    page.clock.run_for(22000)
    first = save_game(page)
    assert first['ants']['smallWorkers'] == UNIT
    assert first['basicResources']['sugar']['amount'] > 5 * UNIT
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
    # Brood actions stay visible when secondary settings are collapsed.
    select_tab(page, 'Nest')
    options = page.locator('.brood-options-details')
    summary = options.locator(':scope > summary')
    expect(options).not_to_have_attribute('open', '')
    expect(worker).to_be_visible()
    expect(scout).to_be_visible()
    summary.click()
    expect(options).to_have_attribute('open', '')
    expect(page.get_by_label('Brood priority', exact=True)).to_be_visible()
    summary.click()
    page.clock.run_for(600)
    expect(options).not_to_have_attribute('open', '')
    expect(page.get_by_label('Brood priority', exact=True)).not_to_be_visible()
    expect(worker).to_be_visible()
    expect(scout).to_be_visible()
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
    select_tab(page, 'Upgrades')
    foraging = page.get_by_role('button', name='Foraging Techniques 1', exact=True)
    expect(foraging).to_be_disabled()
    expect(page.get_by_role('button', name='Foraging Techniques 2', exact=True)).to_have_count(0)
    research['basicResources']['sugar']['amount'] = 150 * UNIT
    load_game(page, research)
    select_tab(page, 'Upgrades')
    expect(foraging).to_be_enabled()
    foraging.click()
    expect(foraging).to_have_count(0)
    researched = save_game(page)
    assert researched['basicResources']['sugar']['amount'] == 0
    assert next(u for u in researched['upgrades']['upgrades'] if u['name'] == 'ImproveSugarCollectorTask1')['unlocked']
    game = progressed_game(save_game(page))
    game['ants'].update(workers=50 * UNIT, scouts=2 * UNIT, daughterQueens=UNIT,
        tasks=[[{'CarcassCarrier': {}}, 0], [{'Explorer': {}}, 0], [{'BroodNurse': {}}, 0], [{'Tunneler': {}}, 10 * UNIT], [{'SugarCollector': {}}, 40 * UNIT], [{'TerritoryForager': {}}, 0]],
        defaultTask={'SugarCollector': {}}, eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[])
    game['nestAttributes']['chambers']['nurseryChamber']['level'] = 1
    game['nestAttributes']['chambers']['royalNurseryChamber']['level'] = 1
    game['exploration'].update(revealedSites=6, proteinDiscovered=True, surveys={'dry-clearing': 3})
    for upgrade in game['upgrades']['upgrades']:
        upgrade.update(show=False, unlocked=upgrade['name'] == 'DefaultWorkers')
    load_game(page, game)
    select_tab(page, 'Chambers')
    page.get_by_label('Details for Establish Dry Clearing Outpost', exact=True).click()
    outpost = page.locator('.nest-outposts .action-details')
    page.clock.run_for(600)
    expect(outpost).to_have_attribute('open', '')
    outpost.locator('summary').click()
    select_tab(page, 'Tasks')
    manual = page.locator('.task-row:visible').evaluate_all(
        '(rows) => rows.map(row => !!row.querySelector(".task-controls button"))')
    assert True in manual and False in manual and manual == sorted(manual, reverse=True), manual
    expect(page.locator('.jobs-panel .info-details')).to_have_count(0)
    for control in page.locator('.jobs-panel .task-controls button:visible').all():
        bounds = control.bounding_box()
        assert bounds['height'] >= 44 and bounds['width'] >= 44, bounds
    expect(page.locator('.small-worker-tasks')).to_have_count(0)
    expect(page.locator('.resource-row').filter(has=page.get_by_text('Small workers', exact=True))).to_have_count(0)
    if width <= 767:
        expect(page.locator('.build-queue-panel')).not_to_be_visible()
        select_tab(page, 'Nest')
        expect(page.locator('.build-queue-panel')).not_to_be_visible()
        select_tab(page, 'Chambers')
        expect(page.locator('.build-queue-panel')).to_be_visible()
        select_tab(page, 'Tasks')
    else:
        expect(page.locator('.build-queue-panel')).to_be_visible()
    # Replacing native select options must retain the actual default task.
    unlocking = save_game(page)
    unlocking['nestAttributes']['chambers']['nurseryChamber']['level'] = 0
    unlocking['ants']['tasks'] = [[{'SugarCollector': {}}, 40 * UNIT], [{'Tunneler': {}}, 10 * UNIT]]
    load_game(page, unlocking)
    select_tab(page, 'Tasks')
    expect(page.locator('#default-worker-task')).to_have_value('SugarCollector')
    select_tab(page, 'Chambers')
    page.get_by_role('button', name='Nursery Chamber', exact=True).click()
    select_tab(page, 'Tasks')
    page.clock.run_for(600)
    expect(page.locator('#default-worker-task option[value="BroodNurse"]')).to_have_count(1)
    expect(page.locator('#default-worker-task')).to_have_value('SugarCollector')
    assert save_game(page)['ants']['defaultTask'] == {'SugarCollector': {}}
    royal = save_game(page)
    royal['ants'].update(daughterQueens=0, royalBrood=None)
    load_game(page, royal)
    expect(page.locator('.royal-staffing')).to_have_text('0/5 Royal Nurses')
    royal_button = page.get_by_role('button', name='Raise Royal Egg', exact=True)
    expect(royal_button).to_be_enabled()
    royal_button.click()
    page.clock.run_for(600)
    expect(page.locator('.royal-nursery-ready')).to_contain_text('Needs Royal Nurses')
    waiting_royal = save_game(page)
    assert waiting_royal['ants']['royalBrood']['careUnits'] == 0
    load_game(page, game)
    select_tab(page, 'Explore')
    # A fresh ring reveals through scout-only missions, with identical mobile/desktop rules.
    game['exploration'].update(revealedSites=0, surveys={}, depleted={}, explorationParties=[])
    load_game(page, game)
    select_tab(page, 'Explore')
    expect(page.locator('.hex-map .map-hex')).to_have_count(7)
    expect(page.locator('.hex-map .map-hex:disabled')).to_have_count(6)
    expect(page.locator('.exploration-progress')).to_have_count(0)
    page.get_by_role('button', name='2 scouts', exact=True).click()
    expect(page.locator('.map-site-card .action-costs').first).to_be_visible()
    assert_fits(page, width)
    page.get_by_role('button', name='Explore Surroundings', exact=True).click()
    sent = save_game(page)
    party = sent['exploration']['explorationParties'][0]
    assert party['mission'] == 'reveal' and party['scouts'] == 2 * UNIT and party['amountWorkers'] == 0
    assert 'experience' not in sent['exploration']
    select_tab(page, 'Explore')
    page.clock.run_for(31000)
    expect(page.locator('.map-hex[data-site="woodland-edge"]')).to_be_enabled()
    page.locator('.map-hex[data-site="woodland-edge"]').click()
    expect(page.get_by_role('button', name='Study Site', exact=True)).to_be_enabled()
    expect(page.get_by_role('button', name='Collect Insects', exact=True)).to_have_count(0)
    assert_fits(page, width)
    select_tab(page, 'Lineage')
    page.get_by_role('button', name='Choose Understandings', exact=True).click()
    page.get_by_role('button', name='Review founding', exact=True).click()
    expect(page.get_by_role('button', name='Found daughter colony', exact=True)).to_be_disabled()
    page.get_by_role('checkbox', name='Confirm colony reset', exact=True).check()
    page.get_by_role('button', name='Found daughter colony', exact=True).click()
    founded = save_game(page)
    assert founded['ants']['lineage']['foundings'] == 1
    assert not founded['ants']['standardWorkersUnlocked']
    assert founded['exploration']['revealedSites'] == 0 and not founded['exploration']['surveys']
    select_tab(page, 'Nest')
    expect(page.get_by_role('button', name='Nursery Chamber', exact=False)).to_have_count(0)
    protein = page.locator('.resource-row').filter(has=page.get_by_text('Protein', exact=True))
    expect(protein).to_have_count(0)
    expand_resources(page)
    assert page.locator('.resource-row:visible .resource-label').all_text_contents() == fresh_resources, (fresh_resources, page.locator('.resource-row:visible .resource-label').all_text_contents())
    page.reload(wait_until='networkidle')
    expect(page.get_by_role('button', name='Nursery Chamber', exact=False)).to_have_count(0)
    expect(protein).to_have_count(0)
    assert page.locator('.resource-row:visible .resource-label').all_text_contents() == fresh_resources, (fresh_resources, page.locator('.resource-row:visible .resource-label').all_text_contents())
    # Repair old untouched daughter colonies that saved inherited visibility.
    legacy_founding = save_game(page)
    lifetime_sugar = legacy_founding['statistics']['sugarU']['total']
    legacy_founding['basicResources']['sugar']['amount'] = 0
    legacy_founding['statistics']['maxSugarU']['thisRun'] = 0
    legacy_founding['unlocks']['actions']['canLayEggs'] = False
    legacy_founding['unlocks']['resources'] = {key: True for key in legacy_founding['unlocks']['resources']}
    legacy_founding['exploration']['proteinDiscovered'] = True
    load_game(page, legacy_founding)
    assert page.locator('.resource-row:visible .resource-label').all_text_contents() == fresh_resources, (fresh_resources, page.locator('.resource-row:visible .resource-label').all_text_contents())
    repaired = save_game(page)
    assert repaired['statistics']['sugarU']['total'] == lifetime_sugar
    assert not repaired['exploration']['proteinDiscovered']
    assert not any(repaired['unlocks']['resources'].values())
    assert_fits(page, width)
    check_brood_icons(page, width, save_game(page))
    check_build_version(page, width)
    check_milestones(page, width, save_game(page))
    check_permanent_milestones(page, width, save_game(page))
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
