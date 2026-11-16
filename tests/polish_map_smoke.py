"""Live map activity feedback at phone/desktop widths; run after fastOptJS."""
import re
from browser_smoke import *


def check(page, width, url):
    now = datetime(2026, 11, 15, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    game = progressed_game(save_game(page))
    game['ants'].update(workers=0, smallWorkers=0, scouts=UNIT, tasks=[], eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[], royalBrood=None)
    game['ants']['queenRoutine']['enabled'] = False
    game['exploration'].update(revealedSites=1, surveys={}, explorationParties=[], collectionTrails=[])
    load_game(page, game)
    page.get_by_role('tab', name='Explore', exact=True).click()
    hexagon = page.locator('.map-hex[data-site="woodland-edge"]')
    expect(hexagon).to_have_attribute('data-activity', 'study')
    expect(page.locator('.map-hex[data-site="nectar-meadow"]')).to_have_attribute('aria-label', 'Unexplored hexagon')
    hexagon.click()
    page.get_by_role('button', name='Study Site', exact=True).click()
    expect(hexagon).to_have_attribute('data-activity', 'studying')
    page.clock.run_for(10000)
    expect(hexagon.locator('.map-state')).to_have_text(re.compile(r'\d+%'))
    expect(hexagon).to_have_attribute('aria-label', re.compile('Studying'))
    assert float(hexagon.locator('[role="progressbar"]').get_attribute('aria-valuenow')) > 0
    page.clock.run_for(180000)
    expect(hexagon).not_to_have_attribute('data-activity', 'studying')
    expect(hexagon.locator('.map-progress')).not_to_be_visible()
    assert_fits(page, width)
    print(f'PASS map feedback {width}px: hidden sites, live study progress and completion', flush=True)


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
