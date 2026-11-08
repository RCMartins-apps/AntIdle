"""Unfinished exploration discovery badges without spoilers."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game, select_expedition


def check_badges(page, width, url):
    now = datetime(2026, 11, 8, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["ants"].update(scouts=2 * UNIT, smallWorkers=0)
    game["exploration"].update(experience=6000, surveys={"woodland-edge": 4, "nectar-meadow": 3, "rotten-log": 4, "dry-clearing": 3, "fallen-pine": 3, "rival-food-cache": 1})
    load_game(page, game)
    page.get_by_role("tab", name="Explore", exact=True).click()
    expect(page.locator(".discovery-badge")).to_have_count(1)
    woodland = page.locator('.exploration-destination[data-destination="woodland-edge"]')
    select_expedition(page, "woodland-edge")
    expect(woodland.locator(".discovery-badge")).to_be_visible()
    expect(woodland.locator(".discovery-badge")).to_have_text("Discovery")
    expect(woodland.locator(".discovery-badge")).to_have_attribute("aria-label", "Discovery still to find")
    expect(woodland).not_to_contain_text("Wild Aphids")
    option = page.locator('#expedition-destination option[value="woodland-edge"]')
    expect(option).to_contain_text("Discovery")
    assert_fits(page, width)
    if width == 390:
        woodland.screenshot(path="/tmp/discovery-badge-390.png")
    woodland.get_by_role("button").click()
    page.clock.run_for(120000)
    expect(page.locator(".discovery-badge")).to_have_count(0)
    expect(option).not_to_contain_text("Discovery")
    if width <= 767:
        expect(page.locator("#expedition-destination")).to_have_value("woodland-edge")
    completed = save_game(page)
    assert completed["exploration"]["surveys"]["woodland-edge"] == 5
    load_game(page, completed)
    page.get_by_role("tab", name="Explore", exact=True).click()
    expect(page.locator(".discovery-badge")).to_have_count(0)
    early = progressed_game(initial)
    early["exploration"] = {}
    early["ants"]["scouts"] = 0
    early["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 0
    load_game(page, early)
    page.get_by_role("tab", name="Explore", exact=True).click()
    expect(page.locator(".exploration-destination")).to_have_count(1)
    expect(page.locator(".discovery-badge")).to_have_count(0)
    expect(page.locator('#expedition-destination option[value="woodland-edge"]')).to_have_count(0)
    assert_fits(page, width)
    print(f"PASS {width}px: remaining discovery indicator, mobile picker hint, live completion, selection stability, save/reload and hidden locked routes", flush=True)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 0), partial(QuietHandler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as p:
            launch = {"headless": True}
            chromium = os.environ.get("CHROMIUM_PATH") or shutil.which("chromium")
            if chromium:
                launch["executable_path"] = chromium
            proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
            if proxy:
                launch["proxy"] = {"server": proxy, "bypass": "127.0.0.1,localhost"}
            browser = p.chromium.launch(**launch)
            try:
                for width in (320, 390, 1920):
                    context = browser.new_context(viewport={"width": width, "height": 844})
                    check_badges(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
