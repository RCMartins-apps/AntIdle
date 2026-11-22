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
    # Persistence must not depend on native toggle delivery. Suppressing those
    # events deterministically reproduces the original saved-state failure.
    page.add_init_script("document.addEventListener('toggle', event => event.stopImmediatePropagation(), true)")
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
    cases.append((queued,'Next worker egg in'))
    scout_next=copy.deepcopy(queued);scout_next['ants']['scoutEggOrders']=[0,0,0]
    cases.append((scout_next,'Next scout egg in'))
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
        if text.startswith('Next '):
            icon='scouts' if 'scout' in text else 'workers'
            expect(panel.locator(f'.brood-next-egg .resource-icon-{icon}')).to_have_count(1)
        else:
            expect(panel.locator('.brood-next-egg')).to_have_count(0)
        expect(panel.locator('.brood-worker-orders b')).to_have_text(str(state['ants']['eggOrders']))
        expect(panel.locator('.brood-scout-orders b')).to_have_text(str(len(state['ants']['scoutEggOrders'])))
        expect(panel.locator('.brood-worker-orders .resource-icon-workers')).to_have_count(1)
        expect(panel.locator('.brood-scout-orders .resource-icon-scouts')).to_have_count(1)
        expect(panel.get_by_role('img',name=f"{state['ants']['eggOrders']} worker egg orders",exact=True)).to_have_count(1)
        expect(panel.get_by_role('img',name=f"{len(state['ants']['scoutEggOrders'])} scout egg orders",exact=True)).to_have_count(1)
        worker_box=panel.locator('.brood-worker-orders').bounding_box()
        scout_box=panel.locator('.brood-scout-orders').bounding_box()
        assert abs(worker_box['y']-scout_box['y']) <= 1
        assert scout_box['x'] >= worker_box['x']+worker_box['width']+10
        badge=worker.locator('.brood-action-counts')
        expect(badge.locator('.egg-count-ordered')).to_have_text(f"Ordered: {state['ants']['eggOrders']}")
        expect(badge.locator('.egg-count-brood')).to_have_text(f"Brood: {len(state['ants']['eggsAndLarvae'])}")
        expect(badge.locator('.egg-count-adults')).to_have_text('Adults: 10')
        scout_button=page.get_by_role('button',name='Raise Scout Egg',exact=True)
        expect(scout_button.locator('.egg-count-ordered')).to_have_text(f"Ordered: {len(state['ants']['scoutEggOrders'])}")
        expect(scout_button.locator('.egg-count-brood')).to_have_text('Brood: 0')
        expect(scout_button.locator('.egg-count-adults')).to_have_text('Adults: 0')
        tops.append(worker.evaluate("el => el.getBoundingClientRect().top - el.closest('.nest-brood').getBoundingClientRect().top"))
        for action in [worker,scout_button]:
            button_box=action.bounding_box()
            info=action.locator('..').locator('.action-details > summary')
            info_box=info.bounding_box()
            assert abs(button_box['y']+button_box['height']/2-info_box['y']-info_box['height']/2)<=1
            assert info_box['x']>=button_box['x']+button_box['width']+8
        if state['ants']['eggOrders'] or state['ants']['scoutEggOrders']:
            cancel_box=cancel.bounding_box()
            counts_box=panel.locator('.brood-counts').bounding_box()
            assert cancel_box['width']>=44 and cancel_box['height']>=44
            assert cancel_box['y']>=counts_box['y'] and cancel_box['y']+cancel_box['height']<=counts_box['y']+counts_box['height']+1
            if width<=767:
                expect(cancel.locator('.brood-cancel-icon')).to_be_visible()
                expect(cancel.locator('.brood-cancel-label')).not_to_be_visible()
            else:
                expect(cancel.locator('.brood-cancel-label')).to_be_visible()
        else:
            expect(cancel).to_have_count(0)
        assert_fits(page,width)
    assert max(tops)-min(tops) <= 1, tops
    worker_info=worker.locator('..').locator('.action-details > summary')
    worker_info.click()
    expect(worker.locator('..').locator('.action-details')).to_have_attribute('open','')
    expect(worker.locator('..').locator('.action-details > div')).to_be_visible()
    worker_info.click()
    expect(worker.locator('..').locator('.action-details > div')).not_to_be_visible()
    expect(cancel).to_be_enabled()
    before=worker.evaluate("el => el.getBoundingClientRect().top - el.closest('.nest-brood').getBoundingClientRect().top")
    worker.click()
    expect(worker.locator('.egg-count-ordered')).to_have_text('Ordered: 124')
    page.get_by_role('button',name='Raise Scout Egg',exact=True).click()
    expect(page.get_by_role('button',name='Raise Scout Egg',exact=True).locator('.egg-count-ordered')).to_have_text('Ordered: 2')
    cancel.click()
    expect(cancel).to_have_count(0)
    expect(panel.locator('.brood-worker-orders b')).to_have_text('0')
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
    # Attribute observation persists state even with native toggle events blocked,
    # without advancing the paused clock or allowing a timed retry.
    assert page.evaluate("localStorage.getItem('antidle.ui.details.nest.brood')") == 'closed'
    panel.locator(':scope > .brood-summary').click()
    expect(worker).to_be_visible()
    assert page.evaluate("localStorage.getItem('antidle.ui.details.nest.brood')") == 'open'
    founding=copy.deepcopy(game)
    founding['ants'].update(standardWorkersUnlocked=False,workers=0,smallWorkers=2*UNIT,tasks=[])
    founding['nestAttributes']['chambers']['nurseryChamber']['level']=0
    load_game(page,founding)
    expect(panel).to_have_attribute('open','')
    expect(panel.locator('.brood-scout-orders')).to_have_count(0)
    expect(panel.locator('.brood-order-count')).to_contain_text('Egg orders')
    small=page.get_by_role('button',name='Raise Small Worker Egg',exact=True)
    expect(small).to_be_visible()
    expect(small.locator('.egg-count-adults')).to_have_text('Adults: 2')
    expect(small.locator('.egg-count-adults .resource-icon-small-workers')).to_have_count(1)
    assert small.locator('.small-worker-mark').evaluate('el => getComputedStyle(el).stroke') != small.locator('.egg-count-adults .resource-icon-small-workers path').first.evaluate('el => getComputedStyle(el).stroke')
    expect(panel.locator('.brood-worker-orders .resource-icon-small-workers')).to_have_count(1)
    # The same retained order switches caste icon when standard development unlocks.
    founding['ants'].update(eggOrders=1,layingCooldown=100)
    load_game(page,founding)
    expect(panel.locator('.brood-next-egg .resource-icon-small-workers')).to_have_count(1)
    founding['ants']['standardWorkersUnlocked']=True
    load_game(page,founding)
    expect(panel.locator('.brood-worker-orders .resource-icon-workers')).to_have_count(1)
    expect(panel.locator('.brood-next-egg .resource-icon-workers')).to_have_count(1)
    assert not errors,errors
    print(f'PASS brood layout {width}px: single heading, stable counters/status/cancel, scoped feeding help and independent disclosures',flush=True)


if __name__=='__main__':
    server=ThreadingHTTPServer(('127.0.0.1',0),partial(ProductionHandler if os.environ.get('ANTIDLE_PRODUCTION') else QuietHandler,directory=str(ROOT)))
    Thread(target=server.serve_forever,daemon=True).start()
    try:
        with sync_playwright() as p:
            chromium=os.environ.get('CHROMIUM_PATH')
            if not chromium and not os.environ.get('GITHUB_ACTIONS'):
                chromium=shutil.which('chromium')
            browser=p.chromium.launch(headless=True,executable_path=chromium)
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
