"""Exercise stale tabs and concurrent save attempts against shared browser storage."""
import base64
import json
from datetime import datetime, timedelta, timezone
import os
import shutil
from functools import partial
from http.server import ThreadingHTTPServer
from threading import Thread

from playwright.sync_api import expect, sync_playwright

from browser_smoke import ROOT, QuietHandler, save_game, select_tab
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


def check_autosave(context, url):
    page = context.new_page()
    now = datetime(2026, 11, 27, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.add_init_script("""
        window.autosaveWrites = [];
        window.rejectAutosave = false;
        const write = Storage.prototype.setItem;
        Storage.prototype.setItem = function (key, raw) {
            if (this === localStorage && key === 'save') {
                window.autosaveWrites.push(JSON.parse(atob(raw)).world.currentTick);
                if (window.rejectAutosave) throw new DOMException('Test write failure', 'QuotaExceededError');
            }
            return write.call(this, key, raw);
        };
    """)
    def flush_writes():
        page.evaluate("navigator.locks.request('antidle:save', () => {})")

    page.goto(url, wait_until="networkidle")
    page.clock.run_for(29400)
    flush_writes()
    assert page.evaluate("window.autosaveWrites") == []
    page.clock.run_for(1000)
    flush_writes()
    assert page.evaluate("window.autosaveWrites") == [150], page.evaluate("window.autosaveWrites")
    original = storage(page)

    page.evaluate("window.rejectAutosave = true")
    page.clock.run_for(30000)
    expect(page.get_by_text("The colony could not be written to local storage.", exact=True)).to_be_visible()
    assert page.evaluate("window.autosaveWrites") == [150, 300]
    assert storage(page) == original

    page.evaluate("window.rejectAutosave = false")
    page.clock.run_for(30000)
    flush_writes()
    assert page.evaluate("window.autosaveWrites") == [150, 300, 450]
    assert storage(page) != original

    saved = json.loads(base64.b64decode(storage(page)))
    saved["world"]["targetTick"] = saved["world"]["currentTick"] + 500
    page.evaluate("(save) => localStorage.setItem('save', save)",
                  base64.b64encode(json.dumps(saved).encode()).decode())
    page.reload(wait_until="networkidle")
    page.clock.run_for(29800)
    assert page.evaluate("window.autosaveWrites") == []
    page.get_by_role("button", name="Use stored catch-up time").click()
    page.clock.run_for(200)
    flush_writes()
    assert page.evaluate("window.autosaveWrites") == [600]
    assert page.evaluate("JSON.parse(atob(localStorage.getItem('save'))).world.currentTick") == 600
    select_tab(page, "Settings")
    page.get_by_role("button", name="Manual Save", exact=True).click()
    flush_writes()
    assert page.evaluate("window.autosaveWrites") == [600, 601]
    print("PASS autosave deadline, failed write and retry, and catch-up snapshot")


def check_clock_changes(context, url):
    page = context.new_page()
    now = datetime(2026, 11, 27, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until='networkidle')
    page.clock.run_for(5000)
    before = save_game(page)['world']
    for offset in (-3, 12):
        page.clock.set_system_time(now + timedelta(hours=offset))
        page.clock.run_for(200)
        after = save_game(page)['world']
        assert after['currentTick'] == before['currentTick'] + 1, (offset, before, after)
        assert after['targetTick'] == before['targetTick'] + 1
        before = after

    colony = save_game(page)
    for offset, expected_credit in ((60_000, 599), (-7_200_000, 18_000)):
        wall_now = page.evaluate('Date.now()')
        world = colony['world']
        world.update(targetTick=world['currentTick'] + 600, lastUpdateTime=wall_now + offset)
        page.evaluate("(raw) => localStorage.setItem('save', raw)", json.dumps(colony))
        page.reload(wait_until='networkidle')
        page.clock.run_for(200)
        after = save_game(page)['world']
        assert after['currentTick'] == world['currentTick'] + 1, (offset, world, after)
        assert after['targetTick'] - after['currentTick'] == expected_credit
        page.clock.run_for(200)
        continued = save_game(page)['world']
        assert continued['currentTick'] == after['currentTick'] + 1
        assert continued['targetTick'] - continued['currentTick'] == expected_credit
    print('PASS live clock jumps, backwards reload, and capped forwards reload')


if __name__ == "__main__":
    handler = ProductionHandler if os.environ.get("ANTIDLE_PRODUCTION") else QuietHandler
    server = ThreadingHTTPServer(("127.0.0.1", 0), partial(handler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(executable_path=os.environ.get("CHROMIUM_PATH") or shutil.which("chromium"))
            with browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True) as context:
                check(context, f"http://127.0.0.1:{server.server_port}")
            with browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True) as context:
                check_autosave(context, f"http://127.0.0.1:{server.server_port}")
            with browser.new_context(viewport={"width": 390, "height": 844}, is_mobile=True) as context:
                check_clock_changes(context, f"http://127.0.0.1:{server.server_port}")
    finally:
        server.shutdown()
        server.server_close()
