"""Task panel disclosure, matching controls, idle semantics and stable staffing rows."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
from threading import Thread
import os
import shutil
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, assert_fits, load_game, progressed_game, save_game, select_tab
from ci_browser_smoke import ProductionHandler


def geometry(page):
    return page.locator('#tasksContent .task-row[id]').evaluate_all("""rows => rows.map(row => {
        const r = row.getBoundingClientRect(), parent = row.closest('#tasksContent').getBoundingClientRect();
        return [row.id, r.y-parent.y, r.height];
    })""")


def check(page, width, url):
    now=datetime(2026,11,17,tzinfo=timezone.utc)
    page.clock.install(time=now); page.clock.pause_at(now+timedelta(seconds=1))
    errors=[]; page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto(url,wait_until='networkidle')
    game=progressed_game(save_game(page))
    game['ants'].update(workers=2*UNIT,smallWorkers=12*UNIT,smallTunnelers=0,smallScavengers=0,smallNurses=0,
        tasks=[[{'SugarCollector':{}},0],[{'Tunneler':{}},0],[{'Scavenger':{}},0]],defaultTask=None,
        scouts=0,eggsAndLarvae=[],eggOrders=0,scoutEggOrders=[])
    game['ants']['queenRoutine']['enabled']=False
    game['nestAttributes']['chambers']['nurseryChamber']['level']=1
    for upgrade in game['upgrades']['upgrades']:
        upgrade['unlocked']=upgrade['name']=='DefaultWorkers'
    load_game(page,game); select_tab(page,'Tasks')
    small=page.locator('.small-worker-tasks')
    heading=small.locator(':scope > summary')
    expect(page.locator('#tasks-tab')).to_have_text('Jobs (2)')
    expect(heading).to_have_text('Small workers · 12')
    expect(small.locator('.small-collector-summary .job-rate')).to_have_text('+0.19/s')
    expect(heading.locator('.resource-icon-small-workers svg path')).to_have_count(2)
    assert heading.locator('.small-worker-mark').evaluate('el => getComputedStyle(el).stroke') != heading.locator('.resource-icon-small-workers path').first.evaluate('el => getComputedStyle(el).stroke')
    standard=page.locator('.standard-worker-tasks')
    standard_heading=standard.locator(':scope > summary')
    idle=standard_heading.locator('.idle-worker-summary')
    expect(standard_heading.locator('.worker-summary-title')).to_have_text('Workers · 2')
    expect(standard_heading.locator('.resource-icon-workers')).to_have_count(1)
    expect(idle).to_have_text('Idle 2')
    assert idle.evaluate('el => Math.abs(el.getBoundingClientRect().right - el.parentElement.getBoundingClientRect().right) <= 1')
    standard_heading.click()
    expect(page.get_by_role('button',name='Assign Tunneler',exact=True)).not_to_be_visible()
    expect(idle).to_have_text('Idle 2')
    page.clock.run_for(600)
    expect(standard).not_to_have_attribute('open','')
    page.reload(wait_until='networkidle');select_tab(page,'Tasks')
    expect(standard).not_to_have_attribute('open','')
    if os.environ.get('ANTIDLE_ARTIFACTS') and width==390:
        site = page.locator('.jobs-panel')
        site.evaluate("el => el.scrollIntoView({block:'center'})")
        site.screenshot(path=os.path.join(os.environ['ANTIDLE_ARTIFACTS'],'worker-cards-collapsed.png'))
    standard_heading.click()
    ants_icon=page.locator('.resource-row').filter(has=page.get_by_text('Ants',exact=True)).locator('.resource-icon-colony path')
    assert page.locator('.mobile-bottom-nav [data-destination=nest] svg path').get_attribute('d') == ants_icon.get_attribute('d')
    expect(idle).to_have_text('Idle 2')
    assert idle.evaluate("el => !!(el.compareDocumentPosition(document.querySelector('.small-worker-tasks')) & Node.DOCUMENT_POSITION_PRECEDING)")
    heading.click()
    expect(page.get_by_role('button',name='Assign small Tunneler',exact=True)).not_to_be_visible()
    page.clock.run_for(600)
    expect(small).not_to_have_attribute('open','')
    page.reload(wait_until='networkidle');select_tab(page,'Tasks')
    expect(small).not_to_have_attribute('open','')
    heading.click()
    expect(page.locator('.jobs-panel .info-details')).to_have_count(0)
    page.get_by_role('button',name='Assign small Brood Nurse',exact=True).click()
    expect(small.locator('.brood-care-badge')).to_contain_text('+1 care')
    page.get_by_role('button',name='Remove small Brood Nurse',exact=True).click()
    expect(page.locator('#task-sugarcollector .job-rate .resource-icon-sugar')).to_have_count(1)
    expect(page.locator('#task-sugarcollector .collector-total')).to_be_hidden()
    expect(page.locator('#task-tunneler .job-rate .resource-icon-tunneling')).to_have_count(1)
    small_plus=page.get_by_role('button',name='Assign small Tunneler',exact=True)
    normal_plus=page.get_by_role('button',name='Assign Tunneler',exact=True)
    for prop in ['backgroundColor','borderRadius','fontSize']:
        assert small_plus.evaluate(f'el => getComputedStyle(el).{prop}')==normal_plus.evaluate(f'el => getComputedStyle(el).{prop}')
    for row in small.locator('.small-worker-job:visible').all():
        assert row.evaluate("el => Math.abs(el.getBoundingClientRect().right - el.querySelector('.task-controls').getBoundingClientRect().right) <= 1")
    if width<=767:
        page.locator('#tasks-action-amount').select_option('10')
        small_plus.click()
        expect(small.locator('.small-worker-job').first).to_contain_text('Tunneler: 10')
        page.locator('#tasks-action-amount').select_option('1')
    else:
        small_plus.click()
        expect(small.locator('.small-worker-job').first).to_contain_text('Tunneler: 1')
    expect(heading).to_have_text('Small workers · 12')
    before=geometry(page)
    normal_plus.click();normal_plus.click()
    expect(page.locator('#tasks-tab')).to_have_text('Jobs')
    expect(idle).to_have_count(0)
    expect(page.locator('#task-scavenger .task-controls button').last).to_have_attribute('title','Needs idle workers')
    assert geometry(page)==before,(width,before,geometry(page))
    page.get_by_role('button',name='Remove Tunneler',exact=True).click()
    expect(page.locator('#task-scavenger .task-controls button').last).to_be_enabled()
    assert geometry(page)==before,(width,before,geometry(page))
    page.locator('#default-worker-task').select_option('SugarCollector')
    expect(idle).to_have_count(0)
    expect(page.locator('#task-sugarcollector .task-controls button').last).to_have_attribute('title','Assigned automatically')
    # The default pool remains assignable, despite having zero actually idle ants.
    expect(normal_plus).to_be_enabled()
    pooled=geometry(page)
    normal_plus.click()
    expect(page.locator('#task-scavenger .task-controls button').last).to_have_attribute('title','Needs Sugar Collector workers')
    assert geometry(page)==pooled,(width,pooled,geometry(page))
    assert_fits(page,width)
    gone=copy.deepcopy(game);gone['ants']['smallWorkers']=0
    load_game(page,gone);select_tab(page,'Tasks')
    expect(small).to_have_count(0)
    expect(idle).to_have_text('Idle 2')
    if os.environ.get('ANTIDLE_ARTIFACTS') and width==390:
        standard.evaluate("el => el.scrollIntoView({block:'center'})")
        standard.screenshot(path=os.path.join(os.environ['ANTIDLE_ARTIFACTS'],'worker-card-expanded.png'))
    nurse = page.locator('#task-broodnurse')
    expect(nurse.locator('.brood-care-badge .job-rate')).to_have_count(0)
    assign = nurse.get_by_role('button',name='Assign Brood Nurse',exact=True)
    assign.click()
    assign.click()
    expect(nurse.locator('.brood-care-badge')).to_contain_text('+4 care')
    expect(nurse.locator('.brood-care-badge .resource-icon-eggs')).to_have_count(1)
    if os.environ.get('ANTIDLE_ARTIFACTS') and width==390:
        nurse.evaluate("el => el.scrollIntoView({block:'center'})")
        nurse.screenshot(path=os.path.join(os.environ['ANTIDLE_ARTIFACTS'],'brood-nurse-care.png'))
    assert not errors,errors
    print(f'PASS task controls {width}px: persistent collapse/count, matching aligned buttons, batch assignment, idle/default distinction and stable blocker geometry',flush=True)


if __name__=='__main__':
    server=ThreadingHTTPServer(('127.0.0.1',0),partial(ProductionHandler,directory=str(ROOT)))
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
                    check(context.new_page(),width,f'http://127.0.0.1:{server.server_port}');context.close()
            finally:
                browser.close()
    finally:
        server.shutdown();server.server_close()
