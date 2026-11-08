"""Aging, protected travel, automatic brood, and stored-time lifecycle checks."""

import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game, select_expedition
from progression_smoke import explore_tab


def check_lifecycle(page, width, url):
    now = datetime(2026, 11, 4, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 12 * UNIT
    game["ants"]["scouts"] = 0
    game["ants"]["eggsAndLarvae"] = []
    game["ants"]["ages"] = {}
    game["ants"]["queenRoutine"] = {}
    game["ants"]["tasks"] = [[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]]
    game["basicResources"]["sugar"] = {"amount": 5000 * UNIT, "maxAmount": 10000 * UNIT}
    game["basicResources"]["protein"] = {"amount": 100 * UNIT, "maxAmount": 100 * UNIT}
    game["basicResources"]["knowledge"] = {"amount": 1000 * UNIT, "maxAmount": 9223372036854775807}
    game["nestAttributes"]["maxAnts"] = 100 * UNIT
    game["nestAttributes"]["maxEggs"] = 2 * UNIT
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 0
    game["exploration"] = {"experience": 1000}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = upgrade["name"] == "DefaultWorkers"

    load_game(page, game)
    expect(page.locator(".colony-aging")).to_have_count(0)
    expect(page.locator(".queen-routine")).to_have_count(0)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Queen's Routine", exact=True)).to_have_count(0)

    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    load_game(page, game)
    expect(page.locator(".colony-aging")).to_contain_text("Next 5m: 0 workers")
    started = save_game(page)
    assert started["ants"]["ages"]["active"]
    assert sum(c["count"] for c in started["ants"]["ages"]["workers"]) == 12
    assert all(c["expiresAt"] >= started["world"]["currentTick"] + 45 * 300 for c in started["ants"]["ages"]["workers"])
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    action = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Queen's Routine", exact=True))
    for value, label in ((1200, "sugar"), (20, "protein"), (100, "knowledge")):
        expect(action.locator(".action-costs")).to_contain_text(f"/{value} {label}")
    action.get_by_role("button").click()
    page.get_by_role("tab", name="Nest", exact=True).click()
    expect(page.locator(".queen-routine-status")).to_have_text("Paused")
    expect(page.locator(".queen-routine")).to_contain_text("10 sugar / worker egg")
    page.locator("#queen-routine-target").fill("14")
    page.locator("#queen-routine-target").press("Tab")
    page.locator("#queen-routine-reserve").fill("4000")
    page.locator("#queen-routine-reserve").press("Tab")
    page.locator("#queen-routine-enabled").check()
    expect(page.locator(".queen-routine-status")).to_have_text("Keeping sugar reserve")
    page.clock.run_for(1000)
    assert not save_game(page)["ants"]["eggsAndLarvae"]
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.locator("#queen-routine-reserve").fill("100")
    page.locator("#queen-routine-reserve").press("Tab")
    page.clock.run_for(200)
    laid = save_game(page)
    assert len(laid["ants"]["eggsAndLarvae"]) == 1
    page.clock.run_for(140000)
    grown = save_game(page)
    assert grown["ants"]["workers"] == 14 * UNIT
    assert not grown["ants"]["eggsAndLarvae"]
    page.reload(wait_until="networkidle")
    expect(page.locator("#queen-routine-enabled")).to_be_checked()
    expect(page.locator("#queen-routine-target")).to_have_value("14")
    expect(page.locator(".queen-routine-status")).to_have_text("Target covered")
    assert_fits(page, width)

    # A scheduled death creates an automatic replacement, which joins the default task.
    aged = copy.deepcopy(grown)
    aged["world"]["currentTick"] = aged["world"]["targetTick"] = 299
    aged["ants"]["ages"] = {"active": True, "workers": [{"expiresAt": 300, "count": 1}, {"expiresAt": 18000, "count": 13}]}
    aged["ants"]["tasks"][0][1] = 14 * UNIT
    aged["ants"]["defaultTask"] = {"SugarCollector": {}}
    load_game(page, aged)
    expect(page.locator(".colony-aging")).to_contain_text("Next 5m: 1 workers")
    page.clock.run_for(200)
    replaced = save_game(page)
    assert replaced["ants"]["workers"] == 13 * UNIT
    assert len(replaced["ants"]["eggsAndLarvae"]) == 1
    assert sum(m.startswith("Aging:") for m in replaced["messages"]) == 1
    page.clock.run_for(120000)
    restored = save_game(page)
    assert restored["ants"]["workers"] == 14 * UNIT
    assert restored["ants"]["tasks"][0][1] == 14 * UNIT
    assert not restored["ants"]["eggsAndLarvae"]

    # Adults continue aging, but the expedition defers deaths until its return.
    traveling = copy.deepcopy(restored)
    traveling["world"]["currentTick"] = traveling["world"]["targetTick"] = 599
    traveling["ants"]["queenRoutine"]["enabled"] = False
    traveling["ants"]["scouts"] = UNIT
    traveling["ants"]["ages"] = {"active": True, "workers": [{"expiresAt": 600, "count": 14}], "scouts": [{"expiresAt": 600, "count": 1}]}
    load_game(page, traveling)
    explore_tab(page)
    page.locator("#explorer-count").fill("5")
    select_expedition(page, "nectar-meadow")
    page.get_by_role("button", name="Explore Nectar Meadow", exact=True).click()
    saved_trip = save_game(page)
    assert sum(c["count"] for c in saved_trip["exploration"]["explorationParties"][0]["ages"]["workers"]) == 5
    page.reload(wait_until="networkidle")
    page.clock.run_for(200)
    losses = save_game(page)
    assert losses["ants"]["workers"] == 5 * UNIT
    assert losses["ants"]["scouts"] == UNIT
    page.clock.run_for(89800)
    returned = save_game(page)
    assert not returned["exploration"]["explorationParties"]
    assert returned["ants"]["workers"] == 0
    assert returned["ants"]["scouts"] == 0
    assert not returned["ants"]["ages"]["workers"]
    assert not returned["ants"]["ages"]["scouts"]
    assert returned["basicResources"]["knowledge"]["amount"] > losses["basicResources"]["knowledge"]["amount"]
    explore_tab(page)
    expect(page.get_by_role("button", name="Explore Nectar Meadow", exact=True)).to_be_disabled()

    # Stored time does not instantly age ants on load; the multiplier advances normal ticks.
    stored = copy.deepcopy(game)
    stored["world"]["currentTick"] = 0
    stored["world"]["targetTick"] = 1000
    stored["ants"]["workers"] = UNIT
    stored["ants"]["tasks"][0][1] = UNIT
    stored["ants"]["ages"] = {"active": True, "workers": [{"expiresAt": 300, "count": 1}]}
    load_game(page, stored)
    assert save_game(page)["world"]["currentTick"] == 0
    page.clock.run_for(30000)
    before_boost = save_game(page)
    assert before_boost["world"]["currentTick"] < 300
    assert before_boost["ants"]["workers"] == UNIT
    page.locator("button.btn-sm").filter(has_text="Off").click()
    page.clock.run_for(16000)
    boosted = save_game(page)
    assert boosted["world"]["currentTick"] >= 300
    assert boosted["ants"]["workers"] == 0
    page.get_by_role("tab", name="Nest", exact=True).click()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: aging grace, grouped losses, research and automation, reserves and targets, saved expiry and deaths on return, replacement, stored-time multiplier, layout")


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
                    check_lifecycle(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
