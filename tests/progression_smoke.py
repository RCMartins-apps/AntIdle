"""End-to-end exploration progression checks; uses the setup in README.md."""

import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game


def task_count(game, name):
    return next((amount for task, amount in game["ants"]["tasks"] if name in task), 0)


def check_progression(page, width, url):
    now = datetime(2026, 11, 1, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["version"] = 2
    game["ants"]["workers"] = 20 * UNIT
    game["ants"].pop("scouts", None)
    game["nestAttributes"].pop("maxScouts", None)
    game["basicResources"].pop("protein", None)
    game["basicResources"]["sugar"] = {"amount": 500 * UNIT, "maxAmount": 1000 * UNIT}
    game["exploration"] = {"explorationParties": []}
    load_game(page, game)
    migrated = save_game(page)
    assert migrated["version"] == 3
    assert migrated["ants"]["scouts"] == 0
    assert migrated["basicResources"]["protein"] == {"amount": 0, "maxAmount": 100 * UNIT}
    assert migrated["ants"]["workers"] == 20 * UNIT
    page.get_by_role("tab", name="Nest", exact=True).click()
    assert page.get_by_role("button", name="Nursery Chamber", exact=False).count() == 0
    if width <= 767:
        page.get_by_label("Action amount").select_option("10")
    page.get_by_role("tab", name="Explore", exact=True).click()
    page.get_by_role("button", name="Explore Surroundings", exact=True).click()
    page.clock.run_for(15000)
    found = save_game(page)
    assert found["exploration"]["carcassDiscovered"]
    assert len(found["exploration"]["carcasses"]) == 1
    assert found["exploration"]["carcasses"][0]["foodRemaining"] == 120 * UNIT
    assert not found["exploration"]["explorationParties"]
    assert task_count(found, "Explorer") == 0
    page.get_by_role("tab", name="Explore", exact=True).click()
    if width <= 767:
        page.get_by_label("Action amount").select_option("10")
    page.locator("#task-carcassharvester-explore").get_by_role(
        "button", name="Assign Carcass Harvester", exact=True
    ).click(modifiers=[] if width <= 767 else ["Control"])
    assigned = save_game(page)
    assert task_count(assigned, "CarcassHarvester") == 10 * UNIT
    sugar_before = assigned["basicResources"]["sugar"]["amount"]
    page.clock.run_for(1000)
    partial = save_game(page)
    assert partial["exploration"]["carcasses"][0]["foodRemaining"] == 112 * UNIT
    assert partial["basicResources"]["sugar"]["amount"] == sugar_before
    assert partial["basicResources"]["protein"]["amount"] == 0
    page.reload(wait_until="networkidle")
    assert save_game(page)["exploration"]["carcasses"] == partial["exploration"]["carcasses"]
    page.clock.run_for(14000)
    harvested = save_game(page)
    assert not harvested["exploration"]["carcasses"]
    assert harvested["basicResources"]["protein"]["amount"] == 5 * UNIT
    assert task_count(harvested, "CarcassHarvester") == 0
    page.get_by_role("tab", name="Explore", exact=True).click()
    expect(page.locator("#task-carcassharvester-explore").get_by_role(
        "button", name="Assign Carcass Harvester", exact=True)).to_be_disabled()
    page.clock.run_for(1000)
    assert save_game(page)["basicResources"]["protein"]["amount"] == 5 * UNIT
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_role("button", name="Nursery Chamber", exact=False).click()
    page.clock.run_for(200)
    nursery = save_game(page)
    assert nursery["nestAttributes"]["chambers"]["nurseryChamber"]["level"] == 1
    assert nursery["nestAttributes"]["maxScouts"] == 3 * UNIT
    assert nursery["basicResources"]["protein"]["amount"] == 2 * UNIT
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_role("button", name="Raise Scout Egg", exact=True).click()
    expect(page.get_by_role("button", name="Raise Scout Egg", exact=True)).to_be_disabled()
    brood = save_game(page)
    assert brood["basicResources"]["protein"]["amount"] == 0
    assert len(brood["ants"]["eggsAndLarvae"]) == 1
    assert "ScoutEgg" in brood["ants"]["eggsAndLarvae"][0]
    page.reload(wait_until="networkidle")
    page.clock.run_for(30000)
    adult = save_game(page)
    assert adult["ants"]["scouts"] == UNIT
    assert not adult["ants"]["eggsAndLarvae"]
    assert adult["ants"]["workers"] == 20 * UNIT
    page.get_by_role("tab", name="Explore", exact=True).click()
    page.get_by_role("button", name="Explore Woodland Edge", exact=True).click()
    expect(page.get_by_role("button", name="Explore Woodland Edge", exact=True)).to_be_disabled()
    away = save_game(page)
    assert away["exploration"]["explorationParties"][0]["scouts"] == UNIT
    page.clock.run_for(1000)
    save_game(page)
    page.reload(wait_until="networkidle")
    page.clock.run_for(44000)
    returned = save_game(page)
    assert returned["ants"]["scouts"] == UNIT
    assert not returned["exploration"]["explorationParties"]
    assert task_count(returned, "Explorer") == 0
    assert returned["exploration"]["carcasses"][0]["totalFood"] == 240 * UNIT
    assert returned["exploration"]["carcasses"][0]["proteinReward"] == 10 * UNIT
    for tab in ("Nest", "Tasks", "Explore", "Settings"):
        page.get_by_role("tab", name=tab, exact=tab != "Tasks").click()
        assert_fits(page, width)
    # Scouts can starve without making the worker count negative.
    returned["ants"]["workers"] = 0
    returned["ants"]["tasks"] = []
    returned["ants"]["sugarCumulativeDebt"] = 10000 * UNIT
    returned["basicResources"]["sugar"]["amount"] = 0
    returned["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
    load_game(page, returned)
    page.clock.run_for(200)
    starved = save_game(page)
    assert starved["ants"]["workers"] == 0
    assert starved["ants"]["scouts"] == 0
    assert not errors, errors
    print(f"PASS {width}px: legacy save, exploration, food offset, protein, Nursery, scout growth, woodland trip, persistence, starvation")


def check_limits(page):
    initial = save_game(page)
    game = progressed_game(initial)
    game["ants"]["workers"] = 20 * UNIT
    game["ants"]["scouts"] = UNIT
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["nestAttributes"]["maxScouts"] = 3 * UNIT
    game["basicResources"]["protein"] = {"amount": 99 * UNIT, "maxAmount": 100 * UNIT}
    game["exploration"] = {
        "explorationParties": [], "carcassDiscovered": True, "proteinDiscovered": True,
        "carcasses": [{"foodRemaining": 1600, "totalFood": 120 * UNIT, "proteinReward": 5 * UNIT}],
    }
    game["ants"]["tasks"].append([{"CarcassHarvester": {}}, UNIT])
    game["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
    load_game(page, game)
    page.clock.run_for(200)
    capped = save_game(page)
    assert capped["basicResources"]["protein"]["amount"] == 100 * UNIT
    assert not capped["exploration"]["carcasses"]
    assert task_count(capped, "CarcassHarvester") == 0

    # A full insect store cannot overflow, even after a scout-guided trip.
    carcass = {"foodRemaining": 120 * UNIT, "totalFood": 120 * UNIT, "proteinReward": 5 * UNIT}
    capped["exploration"]["carcasses"] = [dict(carcass) for _ in range(3)]
    tick = capped["world"]["currentTick"]
    capped["exploration"]["explorationParties"] = [{
        "amountWorkers": UNIT, "startingTick": tick, "targetTick": tick + 1, "scouts": UNIT,
    }]
    capped["ants"]["tasks"] = [[{"Explorer": {}}, UNIT]]
    capped["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
    load_game(page, capped)
    page.get_by_role("tab", name="Explore", exact=True).click()
    expect(page.get_by_role("button", name="Explore Woodland Edge", exact=True)).to_be_disabled()
    page.clock.run_for(200)
    full = save_game(page)
    assert len(full["exploration"]["carcasses"]) == 3
    assert full["ants"]["scouts"] == UNIT
    assert not full["exploration"]["explorationParties"]
    assert task_count(full, "Explorer") == 0

    # Away scouts, like away workers, do not eat at the nest or starve there.
    away = copy.deepcopy(full)
    away["ants"]["workers"] = UNIT
    away["ants"]["tasks"] = [[{"Explorer": {}}, UNIT]]
    away["ants"]["sugarCumulativeDebt"] = 10000 * UNIT
    away["basicResources"]["sugar"]["amount"] = 0
    tick = away["world"]["currentTick"]
    away["exploration"]["explorationParties"] = [{
        "amountWorkers": UNIT, "startingTick": tick, "targetTick": tick + 100, "scouts": UNIT,
    }]
    away["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
    load_game(page, away)
    page.clock.run_for(200)
    reserved = save_game(page)
    assert reserved["ants"]["workers"] == UNIT
    assert reserved["ants"]["scouts"] == UNIT

    # Scout brood reserves scout slots before it matures.
    full["ants"]["eggsAndLarvae"] = [{"ScoutEgg": {"initialTick": full["world"]["currentTick"]}} for _ in range(2)]
    load_game(page, full)
    expect(page.get_by_role("button", name="Raise Scout Egg", exact=True)).to_be_disabled()
    print("PASS limits: protein cap, full stores, scout reservation and brood capacity")


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
                    context = browser.new_context(viewport={"width": width, "height": 844},
                                                  is_mobile=width <= 767, has_touch=width <= 767)
                    page = context.new_page()
                    check_progression(page, width, f"http://127.0.0.1:{server.server_port}")
                    if width == 390:
                        check_limits(page)
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
