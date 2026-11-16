"""Compact resource projection: reveal rules, live amounts, warnings and disclosure memory."""
from functools import partial
from datetime import datetime, timedelta, timezone
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


def check_catchup(page, width, url):
    now = datetime(2026, 11, 16, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    game = save_game(page)
    game['world'].update(currentTick=100, targetTick=18100, lastUpdateTime=page.evaluate('Date.now()'))
    load_game(page, game)
    expect(page.locator('.game-topbar')).to_have_count(0)
    expect(page.get_by_text('Ant Idle', exact=True)).to_have_count(0)
    toggle = page.get_by_role('button', name='Use stored catch-up time', exact=True)
    expect(toggle).to_be_visible()
    expect(toggle).to_have_attribute('aria-pressed', 'false')
    if width <= 767:
        page.get_by_role('button', name='Collapse resource details', exact=True).click()
        expect(page.locator('#resource-details')).not_to_be_visible()
        heading = page.locator('.resource-summary-heading').bounding_box()
        catchup = page.locator('.catchup-controls').bounding_box()
        label = page.locator('.resource-summary-heading > span').first.bounding_box()
        assert label['x'] + label['width'] <= catchup['x'], (label, catchup)
        assert catchup['y'] + catchup['height'] <= heading['y'] + heading['height'] + 2
    before = save_game(page)['world']['currentTick']
    close = page.get_by_role('alert').locator('.btn-close')
    if close.is_visible():
        close.click()
    toggle.click()
    expect(toggle).to_have_attribute('aria-pressed', 'true')
    if width <= 767:
        expect(page.locator('#resource-details')).not_to_be_visible()
    page.clock.run_for(2000)
    after = save_game(page)
    assert after['world']['currentTick'] - before > 10, after['world']
    expect(page.locator('.catchup-time')).not_to_have_text('1h 0m')
    close = page.get_by_role('alert').locator('.btn-close')
    if close.is_visible():
        close.click()
    toggle.click()
    expect(toggle).to_have_attribute('aria-pressed', 'false')
    assert_fits(page, width)
    after['world'].update(targetTick=after['world']['currentTick'], lastUpdateTime=page.evaluate('Date.now()'))
    load_game(page, after)
    expect(page.locator('.catchup-controls')).to_have_count(0)
    print(f'PASS catch-up {width}px: header removed, resource placement, independent toggle, accelerated ticks and exhausted state', flush=True)


if __name__ == '__main__':
    server = ThreadingHTTPServer(('127.0.0.1',0),partial(ProductionHandler if os.environ.get('ANTIDLE_PRODUCTION') else QuietHandler,directory=str(ROOT)))
    Thread(target=server.serve_forever,daemon=True).start()
    try:
        with sync_playwright() as p:
            browser=p.chromium.launch(headless=True,executable_path=shutil.which('chromium'))
            try:
                for width in [320,390,1280]:
                    context=browser.new_context(viewport={'width':width,'height':844},is_mobile=width<=767,has_touch=width<=767)
                    if width <= 767:
                        page = context.new_page()
                        check(page,width,f'http://127.0.0.1:{server.server_port}')
                        page.close()
                    check_catchup(context.new_page(),width,f'http://127.0.0.1:{server.server_port}')
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
