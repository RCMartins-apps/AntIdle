"""Compact resource projection: reveal rules, live amounts, warnings and disclosure memory."""
import copy
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
    panel = page.locator('.resources-column .game-panel')
    expect(panel).to_be_attached()
    summary = page.locator('.compact-resources')
    sugar = summary.locator('[data-resource="Sugar"]')
    expect(summary.locator('.compact-resource')).to_have_count(1)
    expect(sugar.locator('.compact-resource-value')).to_have_text('10.00')
    expect(panel.locator('.card-body')).not_to_be_visible()
    page.get_by_role('button', name='Raise Small Worker Egg', exact=True).click()
    expect(sugar.locator('.compact-resource-value')).to_have_text('5.00')
    header_background=panel.locator(':scope > .card-header').evaluate('el => getComputedStyle(el).backgroundColor')
    page.get_by_role('button', name='Expand resource details', exact=True).click()
    expect(panel.locator('.card-body')).to_be_visible()
    expect(summary).not_to_be_visible()
    expect(panel).to_have_css('background-color',header_background)
    expect(panel.locator(':scope > .card-header')).to_have_css('background-color','rgba(0, 0, 0, 0)')
    expect(panel.locator(':scope > .card-header')).to_have_css('border-bottom-width','0px')
    expect(panel.locator(':scope > .card-body')).to_have_css('background-color','rgba(0, 0, 0, 0)')
    expect(page.locator('.resource-summary-heading')).to_be_visible()
    expect(page.locator('.mobile-resource-toggle')).not_to_have_attribute('aria-describedby','compact-resource-summary')
    page.get_by_role('button', name='Collapse resource details', exact=True).click()
    expect(summary).to_be_visible()
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
    # A sub-tab change collapses resources; reselecting the active sub-tab does not.
    page.get_by_role('tab',name='Jobs',exact=False).click()
    expect(panel.locator('.card-body')).not_to_be_visible()
    expect(summary).to_be_visible()
    page.get_by_role('button',name='Expand resource details',exact=True).click()
    page.get_by_role('tab',name='Jobs',exact=False).click()
    expect(panel.locator('.card-body')).to_be_visible()
    page.locator('.mobile-bottom-nav').get_by_role('button',name='Settings',exact=True).click()
    expect(panel.locator('.card-body')).not_to_be_visible()
    page.get_by_role('button',name='Expand resource details',exact=True).click()
    page.locator('.mobile-bottom-nav').get_by_role('button',name='Nest',exact=True).click()
    expect(panel.locator('.card-body')).not_to_be_visible()
    page.reload(wait_until='networkidle')
    expect(panel.locator('.card-body')).not_to_be_visible()
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


def check_timing_layout(page, width, url):
    # A common wider Linux font reproduces CI's bold-number wrap even on hosts
    # whose default UI font is narrower. Keep all geometry assertions unchanged.
    page.add_init_script("""addEventListener('DOMContentLoaded', () => {
        const style = document.createElement('style');
        style.textContent = '.compact-resource-value { font-family: "DejaVu Sans", sans-serif; }';
        document.head.append(style);
    });""")
    now = datetime(2026, 11, 16, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    game = progressed_game(save_game(page))
    game['ants']['queenRoutine']['enabled'] = False
    game['exploration'].update(explorationParties=[], collectionTrails=[])
    game['ants'].update(smallWorkers=0, scouts=0, repletes=0, daughterQueens=0,
                        defaultTask=None, eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[])
    panel = page.locator('.resources-column .game-panel')
    summary = page.locator('.compact-resources')
    sugar_row = page.locator('.resource-row').filter(has=page.get_by_text('Sugar', exact=True))
    variants = [
        ('', 0, 0, []),
        ('Full', 999_990, 0, []),
        ('Full in ~', 1160, 20, [[{'SugarCollector': {}}, 20 * UNIT]]),
        ('Empty in ~', 604.20, 20, []),
        ('Empty in ~', 22.35, 20, []),
        ('', 0, 0, []),
    ]
    for expanded in ([False, True] if width <= 767 else [True]):
        measurements = []
        for prefix, amount, workers, tasks in variants:
            fixture = copy.deepcopy(game)
            fixture['ants'].update(workers=workers * UNIT, tasks=tasks)
            for resource in ('sugar', 'protein'):
                fixture['basicResources'][resource].update(
                    amount=round(amount * UNIT), maxAmount=999_990 * UNIT)
            load_game(page, fixture)
            if width <= 767 and not expanded:
                page.get_by_role('button', name='Collapse resource details', exact=True).click()
                expect(summary).to_be_visible()
            elif width <= 767:
                expect(summary).not_to_be_visible()
            timing = sugar_row.locator('.resource-timing').text_content().strip()
            assert timing.startswith(prefix) if prefix.endswith('~') else timing == prefix
            if width <= 767 and not expanded:
                sugar = summary.locator('[data-resource="Sugar"]')
                expected = timing.replace('Empty in ~', '~').replace('Full in ~', 'Full ~')
                if timing.startswith('Empty in ~'):
                    expected += ' left'
                expect(sugar.locator('.compact-resource-warning')).to_have_text(expected)
                value = sugar_row.locator('.resource-amount').text_content().strip()
                expect(sugar).to_have_attribute('title', 'Sugar: ' + value + (', ' + timing if timing else ''))
                values = summary.locator('.compact-resource-value').evaluate_all("""nodes => nodes.map(el => ({
                    value: el.textContent, width: el.clientWidth, contentWidth: el.scrollWidth,
                    height: el.getBoundingClientRect().height, lineHeight: parseFloat(getComputedStyle(el).lineHeight)
                }))""")
                assert all(v['contentWidth'] <= v['width'] + 1 and v['height'] <= v['lineHeight'] + 1 for v in values), values
                warning = sugar.locator('.compact-resource-warning')
                if timing.startswith('Empty'):
                    expect(warning).to_have_css('color', 'rgb(220, 53, 69)')
                elif timing:
                    expect(warning).not_to_have_css('color', 'rgb(220, 53, 69)')
            measurements.append(panel.evaluate("""el => ({
                height: el.getBoundingClientRect().height,
                chips: [...el.querySelectorAll('.compact-resource')].map(chip => {
                    const r = chip.getBoundingClientRect(); return [r.x, r.y - el.getBoundingClientRect().y, r.width, r.height];
                })
            })"""))
        heights = [m['height'] for m in measurements]
        assert max(heights) - min(heights) <= 1, (width, expanded, heights)
        if width <= 767 and not expanded:
            assert all(m['chips'] == measurements[0]['chips'] for m in measurements), (width, expanded, measurements)
        assert_fits(page, width)
    print(f'PASS stable resource timing {width}px: repeated full/empty/zero and value-width changes preserve panel/chip geometry', flush=True)


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
        assert toggle.bounding_box()['height'] <= 34
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
            chromium = os.environ.get('CHROMIUM_PATH')
            if not chromium and not os.environ.get('GITHUB_ACTIONS'):
                chromium = shutil.which('chromium')
            browser=p.chromium.launch(headless=True,executable_path=chromium)
            try:
                for width in [320,390,1280]:
                    context=browser.new_context(viewport={'width':width,'height':844},is_mobile=width<=767,has_touch=width<=767)
                    if width <= 767:
                        page = context.new_page()
                        check(page,width,f'http://127.0.0.1:{server.server_port}')
                        page.close()
                    check_catchup(context.new_page(),width,f'http://127.0.0.1:{server.server_port}')
                    timing_page = context.new_page()
                    check_timing_layout(timing_page,width,f'http://127.0.0.1:{server.server_port}')
                    timing_page.close()
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
