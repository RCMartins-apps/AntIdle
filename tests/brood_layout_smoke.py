"""Brood counters, statuses and queue changes do not move the egg action buttons."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
from threading import Thread
import os
import shutil
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game
from ci_browser_smoke import ProductionHandler


def check(page, width, url):
    now=datetime(2026,11,16,tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now+timedelta(seconds=1))
    errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto(url,wait_until='networkidle')
    game=progressed_game(save_game(page))
    game['ants'].update(workers=10*UNIT,smallWorkers=0,scouts=0,tasks=[[{'SugarCollector':{}},10*UNIT]],
        eggsAndLarvae=[],eggOrders=0,scoutEggOrders=[],layingCooldown=0,royalBrood=None,daughterQueens=0)
    game['ants']['queenRoutine']['enabled']=False
    game['nestAttributes']['maxEggs']=2*UNIT
    game['nestAttributes']['chambers']['nurseryChamber']['level']=1
    for upgrade in game['upgrades']['upgrades']:
        upgrade.update(unlocked=False,show=True)
    panel=page.locator('.nest-brood')
    cancel=page.get_by_role('button',name='Cancel egg orders',exact=True)
    worker=page.get_by_role('button',name='Raise Worker Egg',exact=True)
    cases=[]
    cases.append((copy.deepcopy(game),'Queen ready'))
    queued=copy.deepcopy(game);queued['ants'].update(eggOrders=123,scoutEggOrders=[123],layingCooldown=100)
    cases.append((queued,'Next egg in'))
    full=copy.deepcopy(queued)
    tick=full['world']['currentTick']
    full['ants']['eggsAndLarvae']=[{'Developing':{'initialTick':tick,'kind':'worker','care':0}} for _ in range(5)]
    cases.append((full,'Brood places full'))
    hungry=copy.deepcopy(queued);hungry['basicResources']['protein']['amount']=0
    hungry['ants'].update(scoutEggOrders=[0],eggsAndLarvae=[{'Developing':{'initialTick':tick,'kind':'worker','care':100*UNIT}}])
    cases.append((hungry,'Needs protein for scout egg'))
    tops=[]
    for state,text in cases:
        load_game(page,state)
        expect(panel.get_by_text('Brood',exact=True)).to_have_count(1)
        expect(panel.locator('.brood-care-cost')).to_have_count(0)
        expect(panel.locator('.brood-feeding')).not_to_be_visible()
        expect(panel.locator('.brood-pipeline')).to_contain_text(text)
        tops.append(worker.evaluate("el => el.getBoundingClientRect().top - el.closest('.nest-brood').getBoundingClientRect().top"))
        assert_fits(page,width)
    assert max(tops)-min(tops) <= 1, tops
    expect(cancel).to_be_enabled()
    before=worker.evaluate("el => el.getBoundingClientRect().top - el.closest('.nest-brood').getBoundingClientRect().top")
    cancel.click()
    expect(cancel).to_be_disabled()
    expect(panel.locator('.brood-order-count b')).to_have_text('0')
    expect(panel.locator('.brood-scout-orders b')).to_have_text('0')
    after=worker.evaluate("el => el.getBoundingClientRect().top - el.closest('.nest-brood').getBoundingClientRect().top")
    assert abs(before-after)<=1,(before,after)
    page.get_by_label('About Brood',exact=True).click()
    expect(panel).to_have_attribute('open','')
    expect(panel.locator('.brood-feeding')).to_contain_text('Worker and scout larvae')
    page.get_by_label('About Brood',exact=True).click()
    page.clock.run_for(1200)
    expect(panel).to_have_attribute('open','')
    assert abs(worker.evaluate("el => el.getBoundingClientRect().top - el.closest('.nest-brood').getBoundingClientRect().top")-before)<=1
    panel.locator(':scope > .brood-summary').click()
    expect(worker).not_to_be_visible()
    panel.locator(':scope > .brood-summary').click()
    expect(worker).to_be_visible()
    founding=copy.deepcopy(game)
    founding['ants'].update(standardWorkersUnlocked=False,workers=0,smallWorkers=2*UNIT,tasks=[])
    founding['nestAttributes']['chambers']['nurseryChamber']['level']=0
    load_game(page,founding)
    expect(panel.locator('.brood-scout-orders')).to_have_count(0)
    expect(panel.locator('.brood-order-count')).to_contain_text('Egg orders')
    expect(page.get_by_role('button',name='Raise Small Worker Egg',exact=True)).to_be_visible()
    assert not errors,errors
    print(f'PASS brood layout {width}px: single heading, stable counters/status/cancel, scoped feeding help and independent disclosures',flush=True)


if __name__=='__main__':
    server=ThreadingHTTPServer(('127.0.0.1',0),partial(ProductionHandler if os.environ.get('ANTIDLE_PRODUCTION') else QuietHandler,directory=str(ROOT)))
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
