"""Brood counters, statuses and queue changes do not move the egg action buttons."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
from pathlib import Path
from threading import Thread
import os
import shutil
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game, select_tab
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
    initial = save_game(page)
    select_tab(page, 'Nest')
    expect(page.locator('.nest-chambers')).not_to_be_visible()
    game=progressed_game(initial)
    game['ants'].update(workers=10*UNIT,smallWorkers=0,scouts=0,tasks=[[{'SugarCollector':{}},10*UNIT]],
        eggsAndLarvae=[],eggOrders=0,scoutEggOrders=[],layingCooldown=0,royalBrood=None,daughterQueens=0)
    game['ants']['queenRoutine']['enabled']=False
    game['nestAttributes']['maxEggs']=2*UNIT
    game['nestAttributes']['chambers']['nurseryChamber']['level']=1
    for upgrade in game['upgrades']['upgrades']:
        upgrade.update(unlocked=False,show=True)
    panel=page.locator('.nest-brood')
    options=panel.locator('.brood-options-details')
    summary=options.locator(':scope > summary')
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
        expect(summary).to_have_text('Brood options')
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
    expect(options).not_to_have_attribute('open','')
    expect(page.get_by_label('About Brood',exact=True)).to_have_count(0)
    summary.click()
    expect(options).to_have_attribute('open','')
    expect(page.get_by_label('Brood priority',exact=True)).to_be_visible()
    if width <= 767:
        expect(options.get_by_label('Action amount')).to_be_visible()
        options.get_by_label('Action amount').select_option('10')
        expect(worker.locator('.badge')).to_have_text('x10')
    expect(worker).to_be_visible()
    page.clock.run_for(1200)
    expect(options).to_have_attribute('open','')
    assert abs(worker.evaluate("el => el.getBoundingClientRect().top - el.closest('.nest-brood').getBoundingClientRect().top")-before)<=1
    summary.click()
    expect(worker).to_be_visible()
    expect(page.get_by_label('Brood priority',exact=True)).not_to_be_visible()
    if width <= 767:
        expect(worker.locator('.badge')).to_have_text('x10')
    # Attribute observation persists state even with native toggle events blocked.
    key='antidle.ui.details.nest.brood.options'
    assert page.evaluate("key => localStorage.getItem(key)",key) == 'closed'
    summary.click()
    expect(worker).to_be_visible()
    assert page.evaluate("key => localStorage.getItem(key)",key) == 'open'
    founding=copy.deepcopy(game)
    founding['ants'].update(standardWorkersUnlocked=False,workers=0,smallWorkers=2*UNIT,tasks=[])
    founding['nestAttributes']['chambers']['nurseryChamber']['level']=0
    load_game(page,founding)
    expect(options).to_have_attribute('open','')
    expect(panel.locator('.brood-scout-orders')).to_have_count(0)
    expect(page.get_by_label('Brood priority',exact=True)).to_have_count(0)
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
    mixed=copy.deepcopy(game)
    mixed['ants']['eggsAndLarvae']=[
        {'Developing':{'initialTick':tick,'kind':kind,'care':0}}
        for kind in ('worker','scout','worker','scout')
    ]
    next(upgrade for upgrade in mixed['upgrades']['upgrades'] if upgrade['name']=='QueensRoutine')['unlocked']=True
    mixed['ants'].pop('broodPriority',None)  # Existing saves start balanced.
    load_game(page,mixed)
    priority=page.get_by_label('Brood priority',exact=True)
    expect(priority).to_have_value('Balanced')
    assert priority.bounding_box()['height']>=44
    priority.select_option(label='Scouts first')
    page.clock.run_for(1000)
    saved=save_game(page)
    broods=[entry['Developing'] for entry in saved['ants']['eggsAndLarvae']]
    worker_care=sum(b['care'] for b in broods if b['kind']=='worker')
    scout_care=sum(b['care'] for b in broods if b['kind']=='scout')
    assert scout_care>worker_care>0,(scout_care,worker_care)
    load_game(page,saved);select_tab(page,'Nest')
    expect(priority).to_have_value('ScoutsFirst')
    assert_fits(page,width)
    if width==390 and os.environ.get('ANTIDLE_ARTIFACTS'):
        path=Path(os.environ['ANTIDLE_ARTIFACTS'])
        path.mkdir(parents=True,exist_ok=True)
        panel.locator('.brood-options').screenshot(path=str(path/'brood-options-open.png'))
    summary.click()
    expect(worker).to_be_visible()
    expect(page.get_by_role('button',name='Raise Scout Egg',exact=True)).to_be_visible()
    expect(panel.locator('.queen-routine')).not_to_be_visible()
    if width==390 and os.environ.get('ANTIDLE_ARTIFACTS'):
        panel.screenshot(path=str(path/'brood-options-closed.png'))

    crowded=copy.deepcopy(game)
    crowded['basicResources']['sugar'].update(amount=1500*UNIT,maxAmount=2000*UNIT)
    crowded['basicResources']['tunnelingSpace'].update(amount=1000*UNIT,maxAmount=1500*UNIT)
    crowded['nestAttributes'].update(nurseryRefuse=100*UNIT)
    crowded['nestAttributes']['chambers']['nurseryChamber']['level']=2
    crowded['ants'].update(eggOrders=1,eggsAndLarvae=[
        {'Developing': {'initialTick': tick, 'kind': 'worker'}} for _ in range(6)
    ])
    load_game(page,crowded)
    select_tab(page,'Nest')
    meter=panel.locator('.refuse-meter')
    expect(meter).to_contain_text('Waste 100/100')
    expect(meter).to_contain_text('2 places blocked')
    expect(panel.locator('.brood-place-count b')).to_have_text('6/6')
    expect(panel.locator('.brood-pipeline')).to_contain_text('Needs clean brood places')
    expect(meter).to_have_class('refuse-meter small mb-2 text-danger')
    assert_fits(page,width)
    if width==390 and os.environ.get('ANTIDLE_ARTIFACTS'):
        artifacts=Path(os.environ['ANTIDLE_ARTIFACTS'])
        artifacts.mkdir(parents=True,exist_ok=True)
        panel.screenshot(path=str(artifacts/'refuse-full.png'))
    select_tab(page,'Jobs')
    expect(page.get_by_role('button',name='Assign Waste Carrier',exact=True)).to_be_visible()
    select_tab(page,'Chambers')
    chamber=page.get_by_role('button',name='Waste Chamber',exact=True)
    page.get_by_label('Details for Waste Chamber',exact=True).click()
    expect(chamber.locator('..').locator('.action-details')).to_contain_text('+150 waste capacity')
    chamber.click()
    page.clock.run_for(400)
    select_tab(page,'Nest')
    expect(meter).to_contain_text('Waste 100/250')
    expect(meter).not_to_contain_text('blocked')
    expect(panel.locator('.brood-place-count b')).to_have_text('7/8')
    expect(panel.locator('.brood-worker-orders b')).to_have_text('0')
    if width == 390 and os.environ.get('ANTIDLE_ARTIFACTS'):
        select_tab(page, 'Chambers')
        expect(chamber.locator('.badge')).to_have_text('1')
        chamber.locator('..').screenshot(path=str(artifacts / 'waste-chamber.png'))
    def chamber_order(state, expected):
        load_game(page, state)
        select_tab(page, 'Chambers')
        buttons = page.locator('.nest-chambers .action-cell > button')
        labels = buttons.evaluate_all("buttons => buttons.map(button => button.getAttribute('aria-label'))")
        assert labels == expected, (labels, expected)
        assert_fits(page, width)

    early = copy.deepcopy(initial)
    early['ants']['smallWorkers'] = 5 * UNIT
    early['unlocks']['actions']['canBuildNestUpgrade'] = True
    chamber_order(early, ['Expand Nest'])
    maintenance = copy.deepcopy(crowded)
    maintenance['unlocks']['actions'].update(canBuildQueenChamber=False, canBuildFoodStorageChamber=False)
    chamber_order(maintenance, ['Expand Nest', 'Nursery Chamber', 'Waste Chamber'])

    late = copy.deepcopy(maintenance)
    for upgrade in late['upgrades']['upgrades']:
        if upgrade['name'] in {
            'UnlockQueensChamber', 'UnlockFoodStorageChamber', 'AphidTending',
            'RepleteStorage', 'RoyalNurseryPlanning', 'ConstructionPlanning',
        }:
            upgrade['unlocked'] = True
    late['basicResources']['sugar']['amount'] = 0
    chamber_order(late, [
        'Expand Nest', "Queen's Chamber", 'Food Storage Chamber', 'Nursery Chamber',
        'Waste Chamber', 'Aphid Chamber', 'Replete Chamber', 'Royal Nursery',
    ])
    if width == 390 and os.environ.get('ANTIDLE_ARTIFACTS'):
        page.locator('.nest-chambers').screenshot(path=str(artifacts / 'chamber-order.png'))
    page.get_by_role('button', name='Royal Nursery', exact=True).click()
    page.get_by_role('button', name='Expand Nest', exact=True).click()
    page.get_by_role('button', name='Expand Nest', exact=True).click()
    queued = save_game(page)
    queue = queued['nestAttributes']['buildQueue']
    kind = lambda task: next(iter(task['chamberType']))
    assert [kind(task) for task in queue] == ['RoyalNursery', 'Nest', 'Nest']
    select_tab(page, 'Chambers')
    rows = page.locator('.queued-building')
    first = rows.nth(0)
    expect(first.locator('.build-estimate')).to_have_text('Needs resources')
    expect(rows.nth(1).locator('.build-estimate')).to_have_text('Needs resources')
    expect(first.get_by_role('button', name='Move Royal Nursery up')).to_be_disabled()
    raise_nest = rows.nth(1).get_by_role('button', name='Move Expand Nest up')
    raise_nest.focus()
    page.keyboard.press('Enter')
    expect(first.get_by_role('button', name='Move Expand Nest down')).to_be_focused()
    expect(first.locator('.build-estimate')).to_have_text('')
    rows.nth(2).get_by_role('button', name='Move Expand Nest up').click()
    expect(rows.nth(1).locator('.build-estimate')).to_have_text('')
    if width == 390 and os.environ.get('ANTIDLE_ARTIFACTS'):
        page.locator('.build-queue-panel').screenshot(path=str(artifacts / 'reordered-queue.png'))
    assert_fits(page, width)
    reordered = save_game(page)
    assert reordered['nestAttributes']['buildQueue'] == [queue[1], queue[2], queue[0]]
    load_game(page, reordered)
    select_tab(page, 'Chambers')
    start_level = reordered['nestAttributes']['chambers']['nestChamber']['level']
    expect(rows.nth(0).locator('.build-task')).to_have_text(f'Expand Nest · {start_level} → {start_level + 1}')
    expect(rows.nth(1).locator('.build-task')).to_have_text(f'Expand Nest · {start_level + 1} → {start_level + 2}')
    page.clock.run_for(400)
    completed = save_game(page)
    assert completed['nestAttributes']['chambers']['nestChamber']['level'] == start_level + 2
    assert completed['nestAttributes']['buildQueue'] == [queue[0]]
    aging = copy.deepcopy(game)
    now_tick = aging['world']['currentTick']
    aging['ants']['scouts'] = 2 * UNIT
    aging['ants']['ages'] = {
        'active': True,
        'workers': [{'count': 10, 'expiresAt': now_tick + 3000}],
        'scouts': [{'count': 2, 'expiresAt': now_tick + 100}],
    }
    load_game(page, aging)
    select_tab(page, 'Nest')
    forecast = panel.locator('.colony-aging')
    expect(forecast).to_contain_text('Aging · Next 5m')
    neutral = forecast.get_by_role('img', name='0 workers', exact=True)
    expect(neutral.locator('.resource-icon-workers')).to_have_count(1)
    expect(neutral.locator('.text-danger')).to_have_count(0)
    loss = forecast.get_by_role('img', name='2 scouts', exact=True)
    expect(loss.locator('.resource-icon-scouts')).to_have_count(1)
    expect(loss.locator('.text-danger.fw-bold')).to_have_text('2')
    for prop in ('border-top-width', 'border-bottom-width', 'margin-top', 'margin-bottom', 'padding-top', 'padding-bottom'):
        expect(panel).to_have_css(prop, '0px')
    assert_fits(page, width)
    if width == 390 and os.environ.get('ANTIDLE_ARTIFACTS'):
        panel.screenshot(path=str(artifacts / 'brood-aging.png'))
    assert not errors,errors
    print(f'PASS brood layout {width}px: stable counters/status/cancel, visible raise actions, scoped feeding help, independent disclosures, saved brood priority, waste capacity and chamber order',flush=True)


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
