"""Visible blocked-action reasons stay consistent with real engine eligibility."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
from threading import Thread
import shutil
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, assert_fits, load_game, progressed_game, save_game, select_tab
from ci_browser_smoke import ProductionHandler


def card(page, name):
    return page.locator('.action-cell').filter(has=page.get_by_role('button', name=name, exact=True))


def check(page, width, url):
    now = datetime(2026, 11, 16, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto(url, wait_until='networkidle')
    game = progressed_game(save_game(page))
    game['ants'].update(workers=20*UNIT, smallWorkers=0, scouts=0, tasks=[[{'SugarCollector':{}},0]],
        defaultTask=None, eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[], royalBrood=None, daughterQueens=0)
    game['nestAttributes']['chambers']['nurseryChamber']['level']=1
    game['basicResources']['sugar']['amount']=0
    game['basicResources']['protein']['amount']=0
    game['exploration'].update(revealedSites=0, surveys={}, explorationParties=[])
    load_game(page, game)
    for name in ['Raise Worker Egg', 'Raise Scout Egg']:
        expect(card(page,name).get_by_role('button',name=name,exact=True)).to_be_enabled()
        expect(card(page,name).locator('.action-blocked-reason')).to_have_count(0)
        badge_id=card(page,name).locator('.brood-action-counts').get_attribute('id')
        expect(card(page,name).get_by_role('button',name=name,exact=True)).to_have_attribute('aria-describedby',badge_id)
    full=copy.deepcopy(game)
    full['nestAttributes']['maxAnts']=20*UNIT
    load_game(page, full)
    for name in ['Raise Worker Egg', 'Raise Scout Egg']:
        action=card(page,name)
        expect(action.get_by_role('button',name=name,exact=True)).to_be_disabled()
        expect(action.locator('.action-blocked-reason')).to_have_text('No free colony slots')
        reason_id=action.locator('.action-blocked-reason').get_attribute('id')
        badge=action.locator('.brood-action-counts')
        expect(badge).to_have_count(1)
        badge_id=badge.get_attribute('id')
        described_by=action.get_by_role('button',name=name,exact=True).get_attribute('aria-describedby').split()
        assert len(described_by)==2 and set(described_by)=={reason_id,badge_id}, described_by
        expect(badge).to_contain_text('Ordered: 0')
        expect(badge).to_contain_text('Brood: 0')
        expect(badge).to_contain_text('Adults: 20' if name=='Raise Worker Egg' else 'Adults: 0')
    load_game(page,game)
    select_tab(page,'Chambers')
    build=card(page,'Expand Nest')
    expect(build.get_by_role('button',name='Expand Nest',exact=True)).to_be_enabled()
    expect(build.locator('.action-blocked-reason')).to_have_count(0)
    build.get_by_role('button',name='Expand Nest',exact=True).click()
    expect(build.locator('.action-blocked-reason')).to_have_text('Build queue full')
    select_tab(page,'Explore')
    scout=card(page,'Explore Surroundings')
    expect(scout.locator('.action-blocked-reason')).to_have_text('Needs scouts')
    ready=copy.deepcopy(game)
    ready['ants']['scouts']=UNIT
    ready['basicResources']['sugar']['amount']=1000*UNIT
    load_game(page,ready)
    select_tab(page,'Explore')
    expect(scout.locator('.action-blocked-reason')).to_have_count(0)
    scout.get_by_role('button',name='Explore Surroundings',exact=True).click()
    expect(scout.locator('.action-blocked-reason')).to_have_text('Scouting in progress')
    poor=copy.deepcopy(game)
    poor['basicResources']['sugar']['maxAmount']=100*UNIT
    load_game(page,poor)
    select_tab(page,'Upgrades')
    research=card(page,'Foraging Techniques 1')
    expect(research.locator('.action-blocked-reason')).to_have_text('Sugar capacity too low')
    poor['basicResources']['sugar']['maxAmount']=1000*UNIT
    load_game(page,poor)
    select_tab(page,'Upgrades')
    expect(research.locator('.action-blocked-reason')).to_have_text('Needs sugar')
    poor['basicResources']['sugar']['amount']=150*UNIT
    load_game(page,poor)
    select_tab(page,'Upgrades')
    expect(research.get_by_role('button',name='Foraging Techniques 1',exact=True)).to_be_enabled()
    expect(research.locator('.action-blocked-reason')).to_have_count(0)
    jobs=copy.deepcopy(game)
    jobs['ants']['tasks']=[[{'SugarCollector':{}},0],[{'Tunneler':{}},20*UNIT],[{'Scavenger':{}},0]]
    load_game(page,jobs)
    select_tab(page,'Tasks')
    collector=page.locator('#task-sugarcollector')
    expect(collector.locator('.task-controls button').last).to_have_attribute('title','Needs idle workers')
    page.get_by_role('button',name='Remove Tunneler',exact=True).click()
    expect(collector.get_by_role('button',name='Assign Sugar Collector',exact=True)).to_be_enabled()
    expect(collector.locator('.task-controls button').last).to_be_enabled()
    page.get_by_role('button',name='Remove Tunneler',exact=True).click()
    for _ in range(2):
        page.get_by_role('button',name='Assign Scraps Gatherer',exact=True).click()
    expect(page.locator('#task-scavenger .task-controls button').last).to_have_attribute('title','No free task places')
    assert_fits(page,width)
    assert not errors,errors
    print(f'PASS blocked actions {width}px: queued eggs/builds, housing, busy scouts, capacity, affordability and accessible reasons',flush=True)


if __name__=='__main__':
    server=ThreadingHTTPServer(('127.0.0.1',0),partial(ProductionHandler,directory=str(ROOT)))
    Thread(target=server.serve_forever,daemon=True).start()
    try:
        with sync_playwright() as p:
            browser=p.chromium.launch(headless=True,executable_path=shutil.which('chromium'))
            try:
                for width in [320,390,1280]:
                    context=browser.new_context(viewport={'width':width,'height':844},is_mobile=width<=767,has_touch=width<=767)
                    check(context.new_page(),width,f'http://127.0.0.1:{server.server_port}')
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
