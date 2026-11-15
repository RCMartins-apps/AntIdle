"""Grouped colony log at phone/desktop widths, after fastOptJS."""
from browser_smoke import *


def check(page, width, url):
    now = datetime(2026, 11, 15, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    game = progressed_game(save_game(page))
    game['messages'] = [f'Nectar Meadow: collected {n} sugar.' for n in range(10, 0, -1)] + [
        'Discovery: Wild Aphids!', 'Research completed: Brood Care.', 'An ant died of starvation.']
    load_game(page, game)
    log = page.locator('.event-log')
    expect(log.locator('.log-highlights')).to_contain_text('Discovery: Wild Aphids!')
    expect(log.locator('.log-highlights .log-loss')).to_contain_text('starvation')
    expect(log.locator('.log-routine')).not_to_have_attribute('open', '')
    expect(log.locator('.log-routine summary')).to_have_text('Routine activity (10 reports)')
    log.locator('.log-routine summary').click()
    expect(log.locator('.log-routine-entry')).to_have_count(1)
    expect(log.locator('.log-routine-entry')).to_contain_text('collected 10 sugar.')
    page.clock.run_for(400)
    expect(log.locator('.log-routine')).to_have_attribute('open', '')
    assert_fits(page, width)
    print(f'PASS colony log {width}px: significant events, grouped routine reports and stable disclosure', flush=True)


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
