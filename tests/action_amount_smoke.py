"""Independent Nest and Tasks action amounts."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game, select_tab, open_brood_options


def check_amounts(page, width, url):
    now = datetime(2026, 11, 8, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"].update(eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[])
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    load_game(page, game)
    select_tab(page, "Nest")
    if width <= 767:
        open_brood_options(page)
        page.locator("#brood-action-amount").select_option("25")
        select_tab(page, "Jobs")
        expect(page.locator("#tasks-action-amount")).to_have_value("1")
        page.locator("#tasks-action-amount").select_option("10")
        page.get_by_role("button", name="Assign Sugar Collector", exact=True).click()
        saved = save_game(page)
        assert next(n for task, n in saved["ants"]["tasks"] if "SugarCollector" in task) == 10 * UNIT
        select_tab(page, "Nest")
        expect(page.locator("#brood-action-amount")).to_have_value("25")
        page.get_by_role("button", name="Raise Worker Egg", exact=False).click()
        assert save_game(page)["ants"]["eggOrders"] == 25
        select_tab(page, "Nest")
        page.locator("#brood-action-amount").select_option("10")
        scout = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Raise Scout Egg", exact=False))
        expect(scout.locator(".badge")).to_have_text("x10")
        expect(scout.locator(".action-costs")).to_contain_text("/500 sugar")
        expect(scout.locator(".action-costs")).to_contain_text("/20 protein")
        scout.get_by_role("button").click()
        assert save_game(page)["ants"]["scoutEggOrders"] == [25] * 10
        select_tab(page, "Nest")
        page.locator("#brood-action-amount").select_option("25")
        select_tab(page, "Jobs")
        expect(page.locator("#tasks-action-amount")).to_have_value("10")
        page.locator("#tasks-action-amount").select_option("1")
        select_tab(page, "Nest")
        expect(page.locator("#brood-action-amount")).to_have_value("25")
    else:
        expect(page.locator("#brood-action-amount")).not_to_be_visible()
        page.get_by_role("button", name="Raise Worker Egg", exact=False).click(modifiers=["Control"])
        assert save_game(page)["ants"]["eggOrders"] == 10
        select_tab(page, "Nest")
        page.get_by_role("button", name="Raise Scout Egg", exact=False).click(modifiers=["Control"])
        assert save_game(page)["ants"]["scoutEggOrders"] == [10] * 10
        select_tab(page, "Jobs")
        expect(page.locator("#tasks-action-amount")).not_to_be_visible()
        page.get_by_role("button", name="Assign Sugar Collector", exact=True).click(modifiers=["Shift"])
        saved = save_game(page)
        assert next(n for task, n in saved["ants"]["tasks"] if "SugarCollector" in task) == 25 * UNIT
    assert_fits(page, width)
    print(f"PASS {width}px: separate amounts, actual egg orders/job assignment, tab retention and desktop modifiers", flush=True)


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
                    check_amounts(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
