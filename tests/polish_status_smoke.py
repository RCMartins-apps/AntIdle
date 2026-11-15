"""Activity status phone/desktop check. Run after sbt fastOptJS."""
from browser_smoke import *


def check(page, width, url):
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    now = datetime(2026, 11, 15, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    game = progressed_game(save_game(page))
    game['ants'].update(workers=0, smallWorkers=0, scouts=0, tasks=[], eggOrders=1, scoutEggOrders=[], eggsAndLarvae=[], royalBrood=None)
    game['ants']['queenRoutine']['enabled'] = False
    game['basicResources']['sugar']['amount'] = 0
    game['basicResources']['tunnelingSpace']['amount'] = 0
    load_game(page, game)
    expect(page.locator('.brood-pipeline .activity-status')).to_have_attribute('data-state', 'blocked')
    expect(page.locator('.brood-pipeline')).to_contain_text('Needs sugar')
    page.get_by_role('button', name='Expand Nest', exact=True).click()
    expect(page.locator('.build-status').first).to_have_text('Needs space')
    expect(page.locator('.build-status').first).to_have_attribute('data-state', 'blocked')
    assert_fits(page, width)
    game = progressed_game(game)
    game['ants']['eggOrders'] = 0
    game['nestAttributes']['chambers']['royalNurseryChamber']['level'] = 1
    load_game(page, game)
    page.get_by_role('tab', name='Nest', exact=True).click()
    page.get_by_role('button', name='Raise Royal Egg', exact=True).click()
    expect(page.locator('.royal-care-status')).to_have_text('Needs Royal Nurses')
    expect(page.locator('.royal-care-status')).to_have_attribute('data-state', 'blocked')
    page.locator('.royal-care-toggle').click()
    expect(page.locator('.royal-care-status')).to_have_attribute('data-state', 'paused')
    assert_fits(page, width)
    assert not errors, errors
    print(f'PASS statuses {width}px: missing resources, queued construction, royal nurses and pause', flush=True)


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
