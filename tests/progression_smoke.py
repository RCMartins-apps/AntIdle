"""Carcass retrieval and scout progression browser regressions. See README.md."""

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


def explore_tab(page):
    page.get_by_role("tab", name="Explore", exact=True).click()


def carcass(food=120 * UNIT, workers=5 * UNIT, ticks=50, protein=5 * UNIT):
    return dict(foodRemaining=food, totalFood=food, proteinReward=protein,
                requiredWorkers=workers, retrievalTicks=ticks)


def check_progression(page, width, url):
    now = datetime(2026, 11, 2, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    assert initial["version"] == 6
    if width == 390:
        for version in (1, 2, 3, 4, 5, 7):
            unsupported = copy.deepcopy(initial)
            unsupported["version"] = version
            unsupported["ants"]["workers"] = 123 * UNIT
            load_game(page, unsupported)
            assert save_game(page)["ants"]["workers"] == initial["ants"]["workers"]
    game = progressed_game(initial)
    game["ants"]["workers"] = 20 * UNIT
    game["ants"]["scouts"] = 0
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 0
    game["basicResources"]["protein"] = {"amount": 0, "maxAmount": 100 * UNIT}
    game["basicResources"]["sugar"] = {"amount": 10000 * UNIT, "maxAmount": 10000 * UNIT}
    game["exploration"] = {}
    load_game(page, game)
    explore_tab(page)
    page.get_by_role("button", name="Explore Surroundings", exact=True).click()
    page.clock.run_for(15000)
    found = save_game(page)
    assert found["exploration"]["experience"] == 10
    assert len(found["exploration"]["discoveredCarcasses"]) == 1
    assert not found["exploration"]["carcasses"]
    assert not found["exploration"]["retrievals"]
    assert found["basicResources"]["protein"]["amount"] == 0
    assert task_count(found, "Explorer") == 0
    explore_tab(page)
    expect(page.locator(".carcass-capacity")).to_have_text("0 / 1")
    page.get_by_role("button", name="Retrieve Carcass", exact=True).click()
    sent = save_game(page)
    assert task_count(sent, "CarcassCarrier") == 5 * UNIT
    assert len(sent["exploration"]["retrievals"]) == 1
    assert not sent["exploration"]["discoveredCarcasses"]
    page.clock.run_for(9000)
    traveling = save_game(page)
    assert not traveling["exploration"]["carcasses"]
    assert traveling["basicResources"]["protein"]["amount"] == 0
    # Reload mid-retrieval; the carriers and reserved slot must survive.
    page.reload(wait_until="networkidle")
    explore_tab(page)
    expect(page.locator(".carcass-capacity")).to_have_text("1 / 1")
    page.clock.run_for(1000)
    returned = save_game(page)
    assert not returned["exploration"]["retrievals"]
    assert task_count(returned, "CarcassCarrier") == 0
    assert returned["basicResources"]["protein"]["amount"] == 5 * UNIT
    assert 119 * UNIT <= returned["exploration"]["carcasses"][0]["foodRemaining"] < 120 * UNIT
    sugar = returned["basicResources"]["sugar"]["amount"]
    food = returned["exploration"]["carcasses"][0]["foodRemaining"]
    page.clock.run_for(1000)
    fed = save_game(page)
    assert fed["basicResources"]["sugar"]["amount"] == sugar
    assert fed["exploration"]["carcasses"][0]["foodRemaining"] == food - 4 * UNIT
    assert fed["basicResources"]["protein"]["amount"] == 5 * UNIT
    explore_tab(page)
    assert_fits(page, width)
    assert page.get_by_role("button", name="Assign Carcass Harvester").count() == 0
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_role("button", name="Nursery Chamber", exact=False).click()
    page.clock.run_for(200)
    nursery = save_game(page)
    assert nursery["nestAttributes"]["chambers"]["nurseryChamber"]["level"] == 1
    assert nursery["nestAttributes"]["maxAnts"] == fed["nestAttributes"]["maxAnts"] + 3 * UNIT
    assert nursery["basicResources"]["protein"]["amount"] == 2 * UNIT
    explore_tab(page)
    expect(page.locator(".carcass-capacity")).to_have_text("1 / 2")
    # Nearby scraps supply the scout's newly required larval protein.
    page.get_by_role("tab", name="Tasks").click()
    for _ in range(2):
        page.get_by_role("button", name="Assign Scraps Gatherer", exact=True).click()
    page.clock.run_for(20000)
    for _ in range(2):
        page.get_by_role("button", name="Remove Scraps Gatherer", exact=True).click()
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_role("button", name="Raise Scout Egg", exact=True).click()
    page.clock.run_for(90000)
    adult = save_game(page)
    assert adult["ants"]["scouts"] == UNIT
    assert not adult["ants"]["eggsAndLarvae"]
    explore_tab(page)
    page.get_by_role("button", name="Explore Woodland Edge", exact=True).click()
    page.clock.run_for(45000)
    distant = save_game(page)
    assert not distant["exploration"]["explorationParties"]
    assert distant["exploration"]["experience"] == 40
    assert distant["ants"]["scouts"] == UNIT
    assert not distant["exploration"]["carcasses"]
    large = distant["exploration"]["discoveredCarcasses"][0]
    assert large == carcass(240 * UNIT, 10 * UNIT, 100, 10 * UNIT)
    explore_tab(page)
    page.get_by_role("button", name="Retrieve Carcass", exact=True).click()
    page.clock.run_for(20000)
    large_home = save_game(page)
    assert large_home["basicResources"]["protein"]["amount"] == 10 * UNIT
    assert task_count(large_home, "CarcassCarrier") == 0
    assert len(large_home["exploration"]["carcasses"]) == 1
    page.reload(wait_until="networkidle")
    assert save_game(page)["exploration"]["carcasses"] == large_home["exploration"]["carcasses"]
    assert not errors, errors
    print(f"PASS {width}px: discovery, carriers, timed retrieval, immediate protein, food upkeep, Nursery slots, scouts, save reload, layout")
    return game


def check_experience(page, base):
    game = copy.deepcopy(base)
    game["exploration"] = {"experience": 90}
    load_game(page, game)
    explore_tab(page)
    panel = page.locator(".exploration-progress")
    expect(panel).to_contain_text("Level 1 · 90 / 100 XP")
    page.get_by_role("button", name="Explore Surroundings", exact=True).click()
    assert save_game(page)["exploration"]["experience"] == 90  # Award on return only.
    page.clock.run_for(15000)
    leveled = save_game(page)
    assert leveled["exploration"]["experience"] == 100
    reward = leveled["exploration"]["discoveredCarcasses"][0]
    assert reward == carcass(150 * UNIT, 5 * UNIT, 50, 62500)
    page.reload(wait_until="networkidle")
    explore_tab(page)
    expect(panel).to_contain_text("Level 2 · 0 / 200 XP")
    expect(panel).to_contain_text("Reward bonus: +25%")
    expect(panel).to_contain_text("Next level: +50%")
    assert_fits(page, 390)

    # Every possible nearby outcome earns XP; compare identical seeded trips
    # across a level boundary to verify sugar and carcass reward scaling.
    outcomes = set()
    for seed in range(1, 51):
        results = []
        for xp in (0, 90):
            trial = copy.deepcopy(base)
            trial["world"]["saveSeed"] = seed
            trial["exploration"] = {"experience": xp, "carcassDiscovered": True}
            load_game(page, trial)
            explore_tab(page)
            page.get_by_role("button", name="Explore Surroundings", exact=True).click()
            page.clock.run_for(15000)
            result = save_game(page)
            assert result["exploration"]["experience"] == xp + 10
            results.append(result)
        low, high = results
        if low["exploration"]["discoveredCarcasses"]:
            outcomes.add("carcass")
            assert high["exploration"]["discoveredCarcasses"][0]["totalFood"] == 150 * UNIT
        else:
            difference = high["basicResources"]["sugar"]["amount"] - low["basicResources"]["sugar"]["amount"]
            if difference > 0:
                outcomes.add("sugar")
                assert difference % (UNIT // 4) == 0
                assert UNIT // 4 <= difference <= 5 * UNIT
            else:
                outcomes.add("nothing")
        if len(outcomes) == 3:
            break
    assert outcomes == {"carcass", "sugar", "nothing"}, outcomes
    for xp, text in ((299, "Level 2 · 199 / 200 XP"),
                     (300, "Level 3 · 0 / 300 XP"),
                     (52428700, "Maximum level")):
        game["exploration"] = {"experience": xp}
        load_game(page, game)
        explore_tab(page)
        expect(panel).to_contain_text(text)
    print("PASS exploration XP: all outcomes, reward scaling, thresholds, level cap, persistence, layout")


def check_limits(page, base):
    game = copy.deepcopy(base)
    game["ants"]["workers"] = 5 * UNIT
    game["exploration"] = {"carcassDiscovered": True,
                           "discoveredCarcasses": [carcass(), carcass()]}
    load_game(page, game)
    explore_tab(page)
    buttons = page.get_by_role("button", name="Retrieve Carcass", exact=True)
    expect(buttons.first).to_be_enabled()
    buttons.first.click()
    expect(buttons).to_have_count(1)
    expect(buttons.first).to_be_disabled()  # The only slot is reserved.
    # Carriers remain unavailable to other jobs and exploration, without nest upkeep.
    expect(page.get_by_role("button", name="Explore Surroundings", exact=True)).to_be_disabled()
    page.clock.run_for(1000)
    traveling = save_game(page)
    assert traveling["basicResources"]["sugar"]["amount"] == game["basicResources"]["sugar"]["amount"]
    assert task_count(traveling, "CarcassCarrier") == 5 * UNIT
    page.clock.run_for(9000)
    full = save_game(page)
    explore_tab(page)
    expect(buttons.first).to_be_disabled()  # Full stores still block retrieval.
    # Too few idle workers cannot retrieve a carcass, even with storage free.
    full["exploration"]["carcasses"] = []
    full["ants"]["workers"] = UNIT
    load_game(page, full)
    explore_tab(page)
    expect(buttons.first).to_be_disabled()

    # All slots can be reserved concurrently after a Nursery expansion.
    game["ants"]["workers"] = 10 * UNIT
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["exploration"]["discoveredCarcasses"] = [carcass(), carcass(), carcass()]
    game["basicResources"]["protein"]["amount"] = 99 * UNIT
    load_game(page, game)
    explore_tab(page)
    buttons.first.click()
    buttons.first.click()
    expect(buttons.first).to_be_disabled()
    reserved = save_game(page)
    assert len(reserved["exploration"]["retrievals"]) == 2
    assert task_count(reserved, "CarcassCarrier") == 10 * UNIT
    page.reload(wait_until="networkidle")
    page.clock.run_for(10000)
    both = save_game(page)
    assert len(both["exploration"]["carcasses"]) == 2
    assert both["basicResources"]["protein"]["amount"] == 100 * UNIT
    assert task_count(both, "CarcassCarrier") == 0

    # Food drains oldest-first and crosses reserve boundaries without wasting food.
    both["exploration"]["carcasses"] = [carcass(food=100), carcass(food=1000)]
    both["ants"]["workers"] = 2 * UNIT  # 800 food units per tick.
    both["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
    load_game(page, both)
    page.clock.run_for(200)
    partial = save_game(page)
    assert len(partial["exploration"]["carcasses"]) == 1
    assert partial["exploration"]["carcasses"][0]["foodRemaining"] == 300
    assert partial["basicResources"]["sugar"]["amount"] == both["basicResources"]["sugar"]["amount"]
    page.clock.run_for(200)
    depleted = save_game(page)
    assert not depleted["exploration"]["carcasses"]
    assert depleted["basicResources"]["sugar"]["amount"] == partial["basicResources"]["sugar"]["amount"] - 500
    assert depleted["basicResources"]["protein"]["amount"] == partial["basicResources"]["protein"]["amount"]
    explore_tab(page)
    expect(page.locator(".carcass-capacity")).to_have_text("0 / 2")
    expect(buttons.first).to_be_disabled()  # Two workers are still too few to carry it.
    depleted["ants"]["workers"] = 5 * UNIT
    load_game(page, depleted)
    explore_tab(page)
    expect(buttons.first).to_be_enabled()  # Released storage is usable with enough carriers.

    # No ants at home: food stays stored instead of being discarded.
    both["ants"]["workers"] = 0
    both["exploration"]["carcasses"] = [carcass()]
    both["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
    load_game(page, both)
    page.clock.run_for(1000)
    assert save_game(page)["exploration"]["carcasses"] == both["exploration"]["carcasses"]
    print("PASS limits: reserved storage, idle carriers, parallel returns, protein cap, FIFO food, partial upkeep, released slots, zero demand")


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
                    base = check_progression(page, width, f"http://127.0.0.1:{server.server_port}")
                    if width == 390:
                        check_limits(page, base)
                        check_experience(page, base)
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
