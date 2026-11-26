"""Phone navigation uses visible grouped controls without horizontal scrolling."""
from functools import partial
from http.server import ThreadingHTTPServer
from threading import Thread
import shutil
import os
from playwright.sync_api import expect, sync_playwright
from ci_browser_smoke import ProductionHandler
from browser_smoke import ROOT, QuietHandler, assert_fits, load_game, progressed_game, save_game, select_tab


def check(page, width, url):
    page.add_init_script("""addEventListener('DOMContentLoaded', () => {
        const style = document.createElement('style');
        style.textContent = '.card-tabs .nav-link {font-family:"DejaVu Sans",sans-serif}';
        document.head.append(style);
    });""")
    errors = []
    page.on('pageerror', lambda e: (errors.append(str(e)), print('BROWSER ERROR:', e, flush=True)))
    page.goto(url, wait_until='networkidle')
    nav = page.get_by_role('navigation', name='Main navigation')
    expect(page.locator('.antidle-app[data-mobile-navigation=ready]')).to_be_attached()
    expect(nav).to_be_visible()
    expect(nav.get_by_role('button', name='Explore', exact=True)).not_to_be_visible()
    expect(nav.get_by_role('button', name='Lineage', exact=True)).not_to_be_visible()
    base = save_game(page)
    game = progressed_game(base)
    game['nestAttributes']['chambers']['royalNurseryChamber']['level'] = 1
    for upgrade in game['upgrades']['upgrades']:
        if upgrade['name']=='StandardWorkers':upgrade['unlocked']=True
    game['exploration'].update(revealedSites=6, surveys={'woodland-edge':1,'rival-food-cache':1})
    load_game(page, game)
    for name in ['Jobs', 'Explore', 'Upgrades', 'Lineage', 'Settings', 'Nest']:
        select_tab(page, name)
        expect(nav.get_by_role('button', name='Nest' if name=='Jobs' else name, exact=True)).to_have_attribute('aria-current', 'page')
        main = nav.bounding_box()
        if name=='Settings':
            expect(page.locator('.game-main > .card-header')).not_to_be_visible()
        else:
            toolbar = page.locator('.game-main > .card-header').bounding_box()
            assert abs(toolbar['y'] + toolbar['height'] - main['y']) <= 1, (toolbar, main)
            assert toolbar['y'] >= 844 - 140 and main['y'] + main['height'] <= 844, (toolbar, main)
        assert_fits(page, width)
        assert page.locator('.card-tabs').evaluate('(el) => el.scrollWidth <= el.clientWidth + 1')
    select_tab(page, 'Chambers')
    expect(page.locator('.nest-chambers')).to_be_visible()
    expect(page.locator('.nest-brood')).not_to_be_visible()
    select_tab(page, 'Jobs')
    expect(nav.get_by_role('button',name='Jobs',exact=True)).to_have_count(0)
    expect(page.locator('.antidle-app')).to_have_attribute('data-mobile-group','nest')
    for tab_id in ['nest','tasks','chambers']:
        expect(page.locator('#'+tab_id+'-tab .nest-subtab-icon')).to_be_visible()
    assert page.locator('.card-tabs').evaluate("el => [...el.querySelectorAll('a')].filter(a => a.getClientRects().length).every(a => a.scrollWidth <= a.clientWidth + 1)")
    select_tab(page, 'Explore')
    nav.get_by_role('button', name='Nest', exact=True).click()
    expect(page.locator('#tasksContent')).to_be_visible()
    page.get_by_role('tab',name='Jobs',exact=False).focus()
    page.keyboard.press('ArrowRight')
    expect(page.locator('.nest-chambers')).to_be_visible()
    page.get_by_role('tab', name='Brood', exact=True).click()
    expect(page.locator('.nest-brood')).to_be_visible()
    expect(page.locator('.nest-chambers')).not_to_be_visible()
    # Settings is a primary destination; returning preserves the prior sub-view/scroll.
    expect(page.get_by_role('button', name='Game menu', exact=True)).to_have_count(0)
    settings = nav.get_by_role('button', name='Settings', exact=True)
    select_tab(page, 'Chambers')
    # Navigation collapses Resources, so use the same layout on both visits.
    collapse = page.get_by_role('button', name='Collapse resource details', exact=True)
    if collapse.is_visible():
        collapse.click()
    expect(page.locator('#resource-details')).not_to_be_visible()
    page.evaluate("window.scrollTo({top:120,behavior:'instant'})")
    page.wait_for_timeout(50)
    return_scroll = page.evaluate('scrollY')
    settings.click()
    expect(settings).to_have_attribute('aria-current', 'page')
    expect(page.locator('#settingsContent')).to_be_visible()
    nav.get_by_role('button',name='Nest',exact=True).click()
    expect(page.locator('.antidle-app')).to_have_attribute('data-mobile-view', 'chambers')
    page.wait_for_function('(saved) => Math.abs(window.scrollY - saved) <= 2', arg=return_scroll)
    settings.click()
    page.keyboard.press('Escape')
    expect(page.locator('.antidle-app')).to_have_attribute('data-mobile-view', 'chambers')
    settings.click()
    nav.get_by_role('button', name='Explore', exact=True).click()
    expect(settings).to_have_attribute('aria-current', 'false')
    for label in ['Discoveries', 'Territory', 'Map']:
        page.get_by_role('tab', name=label, exact=True).click()
        assert_fits(page, width)
    assert page.locator('.card-tabs').evaluate("el => [...el.querySelectorAll('a')].filter(a => a.getClientRects().length).every(a => a.scrollWidth <= a.clientWidth + 1)")
    select_tab(page, 'Purchased')
    expect(page.locator('.purchased-upgrades')).to_be_visible()
    expect(page.locator('.purchased-upgrades > summary')).not_to_be_visible()
    expect(page.locator('.purchased-upgrades')).to_have_attribute('open','')
    purchased=page.locator('.purchased-upgrade').filter(has=page.get_by_text('Standard Worker Development',exact=True))
    label=purchased.locator(':scope > span')
    info=purchased.locator('.info-summary')
    before=[label.bounding_box(),info.bounding_box()]
    expect(purchased.locator('.info-content')).not_to_be_visible()
    info.click()
    expect(purchased.locator('.info-content')).to_be_visible()
    after=[label.bounding_box(),info.bounding_box()]
    assert all(abs(a[k]-b[k])<=1 for a,b in zip(before,after) for k in ['x','y','width','height']), (before,after)
    row_box=purchased.bounding_box();description=purchased.locator('.info-content').bounding_box()
    assert abs(description['x']-row_box['x'])<=1 and abs(description['width']-row_box['width'])<=1,(row_box,description)
    assert description['y']>=max(b['y']+b['height'] for b in after)
    info.click()
    expect(purchased.locator('.info-content')).not_to_be_visible()

    expect(page.locator('.milestone-panel')).not_to_be_visible()
    select_tab(page, 'Lineage')
    select_tab(page, 'Milestones')
    expect(page.locator('#milestonesContent')).to_be_visible()
    # Simulate the pre-Lineage navigation structure: Milestones belongs to Settings.
    page.locator('#lineage-tab').locator('..').evaluate('el => el.remove()')
    expect(nav.get_by_role('button',name='Lineage',exact=True)).not_to_be_visible()
    expect(settings).to_have_attribute('aria-current','page')
    expect(page.get_by_role('tab',name='Milestones',exact=True)).to_be_visible()
    select_tab(page,'Milestones')
    expect(page.locator('#milestonesContent')).to_be_visible()
    select_tab(page, 'Settings')
    expect(page.get_by_role('button', name='Manual Save', exact=True)).to_be_visible()
    select_tab(page, 'Nest')
    previous, reselected, restored = page.evaluate("""() => {
        window.scrollTo({top: 160, behavior: 'instant'});
        const before = window.scrollY;
        document.querySelector('.mobile-bottom-nav [data-destination="nest"]').click();
        const sameView = window.scrollY;
        document.querySelector('#tasks-tab').click();
        document.querySelector('#nest-tab').click();
        return [before, sameView, window.scrollY];
    }""")
    assert previous > 0 and restored == reselected == previous, (previous, reselected, restored)
    select_tab(page, 'Jobs')
    select_tab(page, 'Nest')
    page.wait_for_function('expected => Math.abs(window.scrollY - expected) <= 2', arg=previous)
    page.set_viewport_size({'width': 1920, 'height': 844})
    expect(nav).not_to_be_visible()
    expect(page.locator('.game-main > .card-header')).to_have_css('position', 'static')
    expect(page.locator('.nest-chambers')).to_be_visible()
    expect(page.locator('.nest-brood')).to_be_visible()
    select_tab(page, 'Explore')
    expect(page.locator('#exploreContent')).to_be_visible()
    page.set_viewport_size({'width': width, 'height': 844})
    expect(nav.get_by_role('button', name='Explore', exact=True)).to_have_attribute('aria-current', 'page')
    assert_fits(page, width)
    assert not errors, errors
    print(f'PASS mobile navigation {width}px: grouping, hidden unlocks, subviews, memory and resize', flush=True)


if __name__ == '__main__':
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(ProductionHandler if os.environ.get('ANTIDLE_PRODUCTION') else QuietHandler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as p:
            chromium = os.environ.get('CHROMIUM_PATH')
            if not chromium and not os.environ.get('GITHUB_ACTIONS'):
                chromium = shutil.which('chromium')
            browser = p.chromium.launch(headless=True, executable_path=chromium)
            try:
                for width in [320, 390]:
                    context = browser.new_context(viewport={'width': width, 'height': 844}, is_mobile=True, has_touch=True)
                    check(context.new_page(), width, f'http://127.0.0.1:{server.server_port}')
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
