"""Persistent UI choices and safe fallback, after fastOptJS."""
from browser_smoke import *


def check(page, width, url):
    now = datetime(2026, 11, 15, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    game = progressed_game(save_game(page))
    game['exploration'].update(revealedSites=2, surveys={}, explorationParties=[])
    load_game(page, game)
    brood = page.locator('.brood-options-details')
    expect(brood).not_to_have_attribute('open', '')
    brood.locator(':scope > summary').click()
    expect(brood).to_have_attribute('open', '')
    page.get_by_role('tab', name='Explore', exact=True).click()
    page.locator('.map-hex[data-site="nectar-meadow"]').click()
    page.clock.run_for(400)
    save_game(page)
    page.reload(wait_until='networkidle')
    expect(brood).to_have_attribute('open', '')
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.locator('.map-hex[data-site="nectar-meadow"]')).to_have_attribute('aria-pressed', 'true')
    page.get_by_label('About Local map', exact=True).click()
    page.clock.run_for(400)
    page.reload(wait_until='networkidle')
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.locator('.info-details[open]')).to_have_count(1)
    assert_fits(page, width)
    reset = save_game(page)
    reset['exploration']['revealedSites'] = 0
    load_game(page, reset)
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.locator('.nest-hex')).to_have_attribute('aria-pressed', 'true')
    page.evaluate("localStorage.setItem('antidle.ui.explore.site', 'invalid-site')")
    page.reload(wait_until='networkidle')
    page.get_by_role('tab', name='Explore', exact=True).click()
    expect(page.locator('.nest-hex')).to_have_attribute('aria-pressed', 'true')
    print(f'PASS UI memory {width}px: brood collapse, map selection, info details, new-colony and invalid-key fallback', flush=True)


def run(check):
    server = ThreadingHTTPServer(('127.0.0.1', 0), partial(QuietHandler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as p:
            args = {'headless': True, 'executable_path': shutil.which('chromium')}
            proxy = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
            if proxy: args['proxy'] = {'server': proxy, 'bypass': '127.0.0.1,localhost'}
            browser = p.chromium.launch(**args)
            for width in (390, 1280):
                context = browser.new_context(viewport={'width': width, 'height': 844}, is_mobile=width <= 767, has_touch=width <= 767)
                try: check(context.new_page(), width, f'http://127.0.0.1:{server.server_port}')
                finally: context.close()
            browser.close()
    finally:
        server.shutdown()
        server.server_close()

if __name__ == '__main__': run(check)
