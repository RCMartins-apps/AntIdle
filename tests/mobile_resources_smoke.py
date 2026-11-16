"""Compact resource projection: reveal rules, live amounts, warnings and disclosure memory."""
from functools import partial
from http.server import ThreadingHTTPServer
from threading import Thread
import os
import shutil
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game, select_tab
from ci_browser_smoke import ProductionHandler


def check(page, width, url):
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto(url, wait_until='networkidle')
    panel = page.locator('[data-mobile-resources=ready]')
    expect(panel).to_be_attached()
    summary = page.locator('.compact-resources')
    sugar = summary.locator('[data-resource="Sugar"]')
    expect(summary.locator('.compact-resource')).to_have_count(1)
    expect(sugar.locator('.compact-resource-value')).to_have_text('0')
    expect(panel.locator('.card-body')).not_to_be_visible()
    page.get_by_role('button', name='Gather Sugar', exact=True).click()
    expect(sugar.locator('.compact-resource-value')).to_have_text('1')
    page.get_by_role('button', name='Expand resource details', exact=True).click()
    expect(panel.locator('.card-body')).to_be_visible()
    page.get_by_role('button', name='Collapse resource details', exact=True).click()
    page.reload(wait_until='networkidle')
    expect(panel.locator('.card-body')).not_to_be_visible()
    base = save_game(page)
    game = progressed_game(base)
    game['ants'].update(workers=100 * UNIT, smallWorkers=0, tasks=[], defaultTask=None)
    game['basicResources']['sugar']['amount'] = 2 * UNIT
    game['basicResources']['protein']['amount'] = game['basicResources']['protein']['maxAmount']
    game['unlocks']['resources'].update(showWater=True, showResin=True)
    load_game(page, game)
    page.get_by_role('button', name='Collapse resource details', exact=True).click()
    expect(summary.locator('[data-resource="Protein"]')).to_have_class('compact-resource at-capacity')
    expect(sugar.locator('.compact-resource-warning')).not_to_be_empty()
    expect(summary.locator('[data-resource="Water"]')).to_have_count(1)
    expect(summary.locator('[data-resource="Resin"]')).to_have_count(1)
    assert summary.bounding_box()['height'] < 180
    select_tab(page, 'Tasks')
    expect(panel.locator('.card-body')).not_to_be_visible()
    page.reload(wait_until='networkidle')
    expect(panel.locator('.card-body')).not_to_be_visible()
    page.get_by_role('button', name='Expand resource details', exact=True).click()
    expect(panel.locator('.resource-timing').first).to_be_visible()
    page.reload(wait_until='networkidle')
    expect(panel.locator('.card-body')).to_be_visible()
    page.get_by_role('button', name='Collapse resource details', exact=True).click()
    page.set_viewport_size({'width':1920, 'height':844})
    expect(panel.locator('.card-body')).to_be_visible()
    expect(page.locator('.mobile-resource-toggle')).not_to_be_visible()
    page.set_viewport_size({'width':width, 'height':844})
    expect(panel.locator('.card-body')).not_to_be_visible()
    load_game(page, base)
    expect(summary.locator('[data-resource="Water"]')).to_have_count(0)
    expect(summary.locator('[data-resource="Resin"]')).to_have_count(0)
    assert_fits(page, width)
    assert not errors, errors
    print(f'PASS compact resources {width}px: live values, reveal rules, capacity, depletion warning, disclosure memory and resize', flush=True)


if __name__ == '__main__':
    server = ThreadingHTTPServer(('127.0.0.1',0),partial(ProductionHandler if os.environ.get('ANTIDLE_PRODUCTION') else QuietHandler,directory=str(ROOT)))
    Thread(target=server.serve_forever,daemon=True).start()
    try:
        with sync_playwright() as p:
            browser=p.chromium.launch(headless=True,executable_path=shutil.which('chromium'))
            try:
                for width in [320,390]:
                    context=browser.new_context(viewport={'width':width,'height':844},is_mobile=True,has_touch=True)
                    check(context.new_page(),width,f'http://127.0.0.1:{server.server_port}')
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
