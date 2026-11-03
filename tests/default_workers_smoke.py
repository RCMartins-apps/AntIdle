"""Default worker upgrade, pool borrowing, returning workers and persistence."""

import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game
from progression_smoke import carcass, explore_tab, task_count


def upgrade(game):
    return next(value for value in game["upgrades"]["upgrades"] if value["name"] == "DefaultWorkers")


def check_default_workers(page, width, url):
    now = datetime(2026, 11, 3, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 20 * UNIT
    game["ants"]["scouts"] = 2 * UNIT
    game["exploration"] = {"experience": 1000, "carcassDiscovered": True,
                           "discoveredCarcasses": [carcass()]}
    upgrade(game)["show"] = False
    game["unlocks"]["tabs"]["exploreTabUnlocked"] = False
    load_game(page, game)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Default Workers", exact=False)).to_have_count(0)
    page.get_by_role("tab", name="Tasks", exact=False).click()
    expect(page.get_by_label("Default task", exact=True)).to_have_count(0)

    # Show the upgrade for already unlocked exploration saves as well as new games.
    game["unlocks"]["tabs"]["exploreTabUnlocked"] = True
    load_game(page, game)
    before = save_game(page)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    page.get_by_role("button", name="Default Workers", exact=False).click()
    purchased = save_game(page)
    assert upgrade(purchased)["unlocked"]
    assert purchased["basicResources"]["sugar"]["amount"] == before["basicResources"]["sugar"]["amount"] - 500 * UNIT
    assert purchased["basicResources"]["knowledge"]["amount"] == before["basicResources"]["knowledge"]["amount"] - 50 * UNIT
    page.get_by_role("tab", name="Tasks", exact=False).click()
    selector = page.get_by_label("Default task", exact=True)
    expect(selector).to_have_value("idle")
    assert selector.locator("option").all_text_contents() == ["Keep idle", "Sugar Collector", "Tunneler"]
    selector.select_option("SugarCollector")
    selected = save_game(page)
    assert selected["ants"]["defaultTask"] == {"SugarCollector": {}}
    assert task_count(selected, "SugarCollector") == 20 * UNIT
    page.get_by_role("tab", name="Tasks", exact=False).click()
    expect(page.get_by_role("button", name="Remove Sugar Collector", exact=True)).to_be_disabled()
    page.get_by_role("button", name="Assign Tunneler", exact=True).click()
    assigned = save_game(page)
    assert task_count(assigned, "Tunneler") == UNIT
    assert task_count(assigned, "SugarCollector") == 19 * UNIT
    page.get_by_role("tab", name="Tasks", exact=False).click()
    page.get_by_role("button", name="Remove Tunneler", exact=True).click()

    # There are no physically idle workers, but the available default pool works.
    explore_tab(page)
    page.locator("#explorer-count").fill("5")
    page.get_by_role("button", name="Explore Surroundings", exact=True).click()
    page.get_by_role("button", name="Retrieve Carcass", exact=True).click()
    away = save_game(page)
    assert task_count(away, "Explorer") == 5 * UNIT
    assert task_count(away, "CarcassCarrier") == 5 * UNIT
    assert task_count(away, "SugarCollector") == 10 * UNIT

    # Changing the default never recalls workers from trips or retrievals.
    page.get_by_role("tab", name="Tasks", exact=False).click()
    selector.select_option("Tunneler")
    changed = save_game(page)
    assert task_count(changed, "Tunneler") == 10 * UNIT
    assert task_count(changed, "SugarCollector") == 0
    assert task_count(changed, "Explorer") == 5 * UNIT
    page.reload(wait_until="networkidle")
    page.clock.run_for(10000)
    first_return = save_game(page)
    assert task_count(first_return, "Tunneler") == 15 * UNIT
    assert task_count(first_return, "Explorer") == 5 * UNIT
    page.clock.run_for(5000)
    all_returned = save_game(page)
    assert task_count(all_returned, "Tunneler") == 20 * UNIT
    assert task_count(all_returned, "Explorer") == 0
    page.get_by_role("tab", name="Tasks", exact=False).click()
    expect(selector).to_have_value("Tunneler")

    # Newly raised workers join the selected pool, including after save/reload.
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_role("button", name="Raise Worker Egg", exact=True).click()
    page.clock.run_for(10000)
    grown = save_game(page)
    assert grown["ants"]["workers"] == 21 * UNIT
    assert task_count(grown, "Tunneler") == 21 * UNIT

    # A small pool cannot borrow from another job to satisfy a larger requirement.
    short = copy.deepcopy(purchased)
    short["ants"]["defaultTask"] = {"SugarCollector": {}}
    short["ants"]["tasks"] = [[{"SugarCollector": {}}, 2 * UNIT],
                              [{"Tunneler": {}}, 18 * UNIT], [{"Explorer": {}}, 0]]
    load_game(page, short)
    explore_tab(page)
    expect(page.get_by_role("button", name="Retrieve Carcass", exact=True)).to_be_disabled()
    expect(page.locator(".carcass-workers .text-danger")).to_have_text("2")
    expect(page.locator(".carcass-workers")).to_contain_text("2/5 Sugar Collector workers")
    assert_fits(page, width)
    page.get_by_role("tab", name="Tasks", exact=False).click()
    selector.select_option("idle")
    idle = save_game(page)
    assert idle["ants"].get("defaultTask") is None
    assert task_count(idle, "SugarCollector") == 0
    assert task_count(idle, "Tunneler") == 18 * UNIT
    page.get_by_role("tab", name="Tasks", exact=False).click()
    expect(selector).to_have_value("idle")
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: unlock/purchase, selector, borrowing, concurrent actions, default switching, returns, maturation, save reload, shortages, keep idle")


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
                    check_default_workers(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
