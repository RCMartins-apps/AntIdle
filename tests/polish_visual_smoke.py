"""Visual consistency at phone/desktop widths; run after fastOptJS."""
from browser_smoke import *


def check(page, width, url):
    now = datetime(2026, 11, 15, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    game = progressed_game(save_game(page))
    game['basicResources']['sugar'].update(amount=1250000 * UNIT, maxAmount=2000000 * UNIT)
    game['nestAttributes']['buildQueue'] = []
    load_game(page, game)
    sugar = page.locator('.resource-row').filter(has=page.get_by_text('Sugar', exact=True))
    expect(sugar.locator('.resource-amount')).to_contain_text('1.25M')
    expect(sugar).to_contain_text('2M')
    expect(page.locator('.queue-empty')).to_have_text('No construction queued.')
    button = page.get_by_role('button', name='Gather Sugar', exact=True)
    assert button.bounding_box()['height'] >= 44
    expect(button).to_have_css('font-size', '16px')
    expect(sugar.locator('.resource-amount')).to_have_css('font-variant-numeric', 'tabular-nums')
    assert_fits(page, width)
    page.get_by_role('tab', name='Settings', exact=True).click()
    assert_fits(page, width)
    print(f'PASS visual polish {width}px: compact values, aligned digits, control sizes, settings and queue empty state', flush=True)


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
