"""Exercise stale tabs and concurrent save attempts against shared browser storage."""
from datetime import datetime, timedelta, timezone
import os
import shutil
from functools import partial
from http.server import ThreadingHTTPServer
from threading import Thread

from playwright.sync_api import expect, sync_playwright

from browser_smoke import ROOT, QuietHandler, select_tab
from ci_browser_smoke import ProductionHandler


def storage(page):
    return page.evaluate("localStorage.getItem('save')")


def check(context, url):
    first, stale = context.new_page(), context.new_page()
    for page in (first, stale):
        now = datetime(2026, 11, 27, tzinfo=timezone.utc)
        page.clock.install(time=now)
        page.clock.pause_at(now + timedelta(seconds=1))
        page.goto(url, wait_until="networkidle")
        select_tab(page, "Settings")

    first.evaluate("""() => new Promise(acquired => {
        navigator.locks.request('antidle:save', () => new Promise(release => {
            window.releaseTestSaveLock = release;
            acquired();
        }));
    })""")
    first.get_by_role("button", name="Manual Save", exact=True).click()
    stale.get_by_role("button", name="Manual Save", exact=True).click()
    first.evaluate("window.releaseTestSaveLock()")
    expect(first.get_by_text("Game Saved!", exact=True)).to_be_visible()
    original = storage(first)
    assert original

    expect(stale.get_by_text("A newer colony save exists", exact=True)).to_be_visible()
    if screenshot := os.environ.get("ANTIDLE_SAVE_CONFLICT_SCREENSHOT"):
        stale.screenshot(path=screenshot)
    assert storage(first) == original
    stale.get_by_role("button", name="Load newer save", exact=True).click()
    expect(stale.get_by_text("Game Loaded!", exact=True)).to_be_visible()

    first.get_by_role("button", name="Off", exact=True).click()
    first.clock.run_for(1000)
    first.get_by_role("button", name="Hard Reset", exact=True).click()
    first.get_by_role("button", name="Confirm", exact=True).click()
    expect(first.get_by_text("New Game Started!", exact=True)).to_be_visible()
    reset = storage(first)
    assert reset != original

    stale.clock.run_for(31000)
    expect(stale.get_by_text("A newer colony save exists", exact=True)).to_be_visible()
    assert storage(first) == reset
    stale.get_by_role("button", name="Keep playing", exact=True).click()
    stale.get_by_role("button", name="Hard Reset", exact=True).click()
    stale.get_by_role("button", name="Confirm", exact=True).click()
    expect(stale.get_by_text("A newer colony save exists", exact=True)).to_be_visible()
    assert storage(first) == reset
    playtest = context.new_page()
    playtest.goto(url + "/?playtest=1", wait_until="networkidle")
    select_tab(playtest, "Settings")
    playtest.get_by_role("button", name="Manual Save", exact=True).click()
    expect(playtest.get_by_text("Game Saved!", exact=True)).to_be_visible()
    assert playtest.evaluate("localStorage.getItem('save:playtest')")
    assert storage(first) == reset
    print("PASS concurrent and stale saves, reset, and playtest isolation")


if __name__ == "__main__":
    handler = ProductionHandler if os.environ.get("ANTIDLE_PRODUCTION") else QuietHandler
    server = ThreadingHTTPServer(("127.0.0.1", 0), partial(handler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(executable_path=os.environ.get("CHROMIUM_PATH") or shutil.which("chromium"))
            with browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True) as context:
                check(context, f"http://127.0.0.1:{server.server_port}")
    finally:
        server.shutdown()
        server.server_close()
