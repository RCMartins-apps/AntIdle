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
    load_game(page, game)
    for name in ['Tasks', 'Explore', 'Upgrades', 'Lineage', 'Nest']:
        select_tab(page, name)
        expect(nav.get_by_role('button', name=name, exact=True)).to_have_attribute('aria-current', 'page')
        assert_fits(page, width)
        assert page.locator('.card-tabs').evaluate('(el) => el.scrollWidth <= el.clientWidth + 1')
    select_tab(page, 'Chambers')
    expect(page.locator('.nest-chambers')).to_be_visible()
    expect(page.locator('.nest-brood')).not_to_be_visible()
    select_tab(page, 'Tasks')
    nav.get_by_role('button', name='Nest', exact=True).click()
    expect(page.locator('.nest-chambers')).to_be_visible()
    page.get_by_role('tab', name='Brood', exact=True).click()
    expect(page.locator('.nest-brood')).to_be_visible()
    expect(page.locator('.nest-chambers')).not_to_be_visible()
    select_tab(page, 'Purchased')
    expect(page.locator('.purchased-upgrades')).to_be_visible()
    expect(page.locator('.milestone-panel')).not_to_be_visible()
    select_tab(page, 'Lineage')
    select_tab(page, 'Milestones')
    expect(page.locator('#milestonesContent')).to_be_visible()
    select_tab(page, 'Settings')
    expect(page.get_by_role('button', name='Manual Save', exact=True)).to_be_visible()
    select_tab(page, 'Nest')
    page.evaluate("window.scrollTo({top:160,behavior:'instant'})")
    page.wait_for_timeout(100)
    previous = page.evaluate('window.scrollY')
    select_tab(page, 'Tasks')
    select_tab(page, 'Nest')
    page.wait_for_timeout(100)
    assert abs(page.evaluate('window.scrollY') - previous) <= 2, (previous, page.evaluate('window.scrollY'), page.locator('.antidle-app').get_attribute('data-mobile-view'))
    page.set_viewport_size({'width': 1920, 'height': 844})
    expect(nav).not_to_be_visible()
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
            browser = p.chromium.launch(headless=True, executable_path=shutil.which('chromium'))
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
