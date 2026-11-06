"""Readable resource amounts, research layout/empty states and visible expedition blockers."""

import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game
from progression_smoke import explore_tab


def check_polish(page, width, url):
    now = datetime(2026, 11, 6, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"].update(workers=30 * UNIT, scouts=2 * UNIT, eggsAndLarvae=[],
        tasks=[[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]])
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["exploration"] = {"experience": 300}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=False)
    load_game(page, game)
    sugar = page.locator(".resource-row").filter(has=page.get_by_text("Sugar", exact=True))
    expect(sugar.locator(".resource-amount")).to_have_text("100K")
    expect(sugar.locator(".resource-amount.text-primary.fw-bold")).to_have_count(1)
    expect(sugar.locator(".text-danger")).to_contain_text("-")
    assert sugar.locator('.resource-amount [style*="opacity"]').count() == 0
    assert_fits(page, width)

    page.get_by_role("tab", name="Upgrades", exact=True).click()
    cards = page.locator(".upgrades-grid > .action-cell")
    assert cards.count() >= 2
    first, second = cards.nth(0).bounding_box(), cards.nth(1).bounding_box()
    if width > 767:
        assert abs(first["y"] - second["y"]) <= 1
        assert second["x"] >= first["x"] + first["width"] - 1
    else:
        assert second["y"] >= first["y"] + first["height"] - 1
    assert_fits(page, width)
    before = save_game(page)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    page.get_by_role("button", name="Foraging Techniques 1", exact=True).click()
    expect(page.get_by_role("button", name="Foraging Techniques 1", exact=True)).to_have_count(0)
    expect(page.get_by_role("button", name="Foraging Techniques 2", exact=True)).to_be_visible()
    bought = save_game(page)
    assert bought["basicResources"]["sugar"]["amount"] == before["basicResources"]["sugar"]["amount"] - 150 * UNIT

    # Distinguish a gap before future research from exhausting the current catalog.
    quiet = copy.deepcopy(game)
    quiet["exploration"] = {}
    later = {"AdditionalExpeditionSlot", "EstablishedTrails", "ReinforcedMandibles", "BroodCare",
        "AphidTending", "QueensRoutine", "ConstructionPlanning", "ColonyNutrition", "RepleteStorage"}
    for upgrade in quiet["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] not in later)
    load_game(page, quiet)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.locator(".upgrades-empty")).to_contain_text("No upgrades available right now.")
    expect(page.locator(".upgrades-grid .action-cell")).to_have_count(0)
    expect(page.locator("#upgradesContent")).not_to_contain_text("Colony Nutrition")
    for upgrade in quiet["upgrades"]["upgrades"]:
        upgrade["unlocked"] = True
    load_game(page, quiet)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.locator(".upgrades-empty")).to_have_text("All current upgrades researched.")
    assert_fits(page, width)

    load_game(page, game)
    explore_tab(page)
    rotten = page.locator(".exploration-destination").filter(has=page.get_by_role("button", name="Explore Rotten Log", exact=True))
    expect(rotten.get_by_role("button", name="Explore Rotten Log", exact=True)).to_be_disabled()
    expect(rotten.locator(".destination-minimum.text-danger")).to_have_text("5 workers minimum")
    page.get_by_role("button", name="Send 5 workers", exact=True).click()
    expect(rotten.locator(".destination-minimum")).to_have_count(0)
    expect(rotten.get_by_role("button", name="Explore Rotten Log", exact=True)).to_be_enabled()
    assert page.locator(".expedition-status").bounding_box()["y"] < page.locator(".exploration-destination").first.bounding_box()["y"]
    # At level 3 there is one slot: a launched trip explains other disabled actions.
    rotten.get_by_role("button", name="Explore Rotten Log", exact=True).click()
    expect(page.locator(".expedition-status .text-danger")).to_contain_text("All slots in use")
    expect(page.get_by_role("button", name="Explore Surroundings", exact=True)).to_be_disabled()
    assert_fits(page, width)
    page.clock.run_for(60000)
    expect(page.locator(".expedition-status")).not_to_contain_text("All slots in use")
    expect(page.get_by_role("button", name="Explore Surroundings", exact=True)).to_be_enabled()
    assert not errors, errors
    print(f"PASS {width}px: readable totals/negative rates, upgrade columns/empty states/purchases, party minimum and fleet status/recovery, layout", flush=True)


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
                    check_polish(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
