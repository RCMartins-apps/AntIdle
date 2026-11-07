"""Foraging population pressure, live rates, actual income, and paid expedition slots."""

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


def check_foraging(page, width, url):
    now = datetime(2026, 11, 4, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 80 * UNIT
    game["ants"]["scouts"] = 0
    game["ants"]["tasks"] = [[{"SugarCollector": {}}, 80 * UNIT],
                              [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]]
    game["nestAttributes"]["maxAnts"] = 200 * UNIT
    game["basicResources"]["sugar"] = {"amount": 500 * UNIT, "maxAmount": 1000 * UNIT}
    game["basicResources"]["knowledge"] = {"amount": 1000 * UNIT, "maxAmount": 9223372036854775807}
    game["exploration"] = {"experience": 1000}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = False

    def collector_description(text):
        page.get_by_role("tab", name="Tasks", exact=False).click()
        row = page.locator("#task-sugarcollector")
        row.get_by_label("About Sugar Collector", exact=True).click()
        expect(row.locator(".task-description")).to_have_text(text)
        row.get_by_label("About Sugar Collector", exact=True).click()

    def sugar_row():
        return page.locator(".resource-row").filter(has=page.get_by_text("Sugar", exact=True))

    load_game(page, game)
    collector_description("0.2025 sugar/s per collector · next: 0")
    expect(sugar_row()).to_contain_text("+0.20/s")
    before = save_game(page)
    page.clock.run_for(1000)
    after = save_game(page)
    ticks = after["world"]["currentTick"] - before["world"]["currentTick"]
    assert ticks > 0
    assert after["basicResources"]["sugar"]["amount"] - before["basicResources"]["sugar"]["amount"] == ticks * 400

    # The existing foraging upgrade improves both real income and displayed collector rates.
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    page.get_by_role("button", name="Foraging Techniques 1", exact=False).click()
    collector_description("0.2328 sugar/s per collector · next: 0")
    expect(sugar_row()).to_contain_text("+2.63/s")

    # A tiny positive marginal yield remains visible, and overpopulation drains sugar.
    smaller = copy.deepcopy(game)
    smaller["ants"]["workers"] = 79 * UNIT
    smaller["ants"]["tasks"][0][1] = 79 * UNIT
    load_game(page, smaller)
    collector_description("0.205 sugar/s per collector · next: 0.005")
    page.get_by_role("button", name="Remove Sugar Collector", exact=True).click()
    page.get_by_role("button", name="Assign Sugar Collector", exact=True).click()
    collector_description("0.205 sugar/s per collector · next: 0.005")
    crowded = copy.deepcopy(game)
    crowded["ants"]["workers"] = 82 * UNIT
    crowded["ants"]["tasks"][0][1] = 82 * UNIT
    load_game(page, crowded)
    expect(sugar_row()).to_contain_text("-0.20/s")
    before = save_game(page)
    page.clock.run_for(1000)
    after = save_game(page)
    ticks = after["world"]["currentTick"] - before["world"]["currentTick"]
    assert after["basicResources"]["sugar"]["amount"] - before["basicResources"]["sugar"]["amount"] == -ticks * 400

    # Knowledge upgrades are hidden before exploration, even with stale saved flags.
    early = copy.deepcopy(game)
    early["unlocks"]["tabs"]["exploreTabUnlocked"] = False
    for upgrade in early["upgrades"]["upgrades"]:
        if upgrade["name"] == "ImproveSugarCollectorTask1":
            upgrade["unlocked"] = True
        if upgrade["name"] == "ImproveSugarCollectorTask2":
            upgrade["show"] = True
    load_game(page, early)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Foraging Techniques 2", exact=True)).to_have_count(0)
    expect(page.get_by_role("button", name="Established Trails", exact=True)).to_have_count(0)
    early["unlocks"]["tabs"]["exploreTabUnlocked"] = True
    load_game(page, early)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Foraging Techniques 2", exact=True)).to_have_count(1)

    trails = copy.deepcopy(game)
    trails["exploration"]["experience"] = 99
    trails["basicResources"]["sugar"]["amount"] = 1000 * UNIT
    load_game(page, trails)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Established Trails", exact=True)).to_have_count(0)
    trails["exploration"]["experience"] = 100
    load_game(page, trails)
    before = save_game(page)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    action = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Established Trails", exact=True))
    expect(action.locator(".action-costs")).to_contain_text("/600 sugar")
    expect(action.locator(".action-costs")).to_contain_text("/60 knowledge")
    action.get_by_role("button").click()
    purchased = save_game(page)
    assert purchased["basicResources"]["sugar"]["amount"] == before["basicResources"]["sugar"]["amount"] - 600 * UNIT
    assert purchased["basicResources"]["knowledge"]["amount"] == before["basicResources"]["knowledge"]["amount"] - 60 * UNIT
    collector_description("0.242 sugar/s per collector · next: 0.08")
    expect(sugar_row()).to_contain_text("+3.36/s")
    before = save_game(page)
    page.clock.run_for(1000)
    after = save_game(page)
    ticks = after["world"]["currentTick"] - before["world"]["currentTick"]
    assert after["basicResources"]["sugar"]["amount"] - before["basicResources"]["sugar"]["amount"] == ticks * 6720
    page.reload(wait_until="networkidle")
    collector_description("0.242 sugar/s per collector · next: 0.08")

    # The paid slot is a permanent upgrade, revealed at level three.
    slots = copy.deepcopy(game)
    slots["ants"]["workers"] = 20 * UNIT
    slots["ants"]["tasks"][0][1] = 0
    slots["basicResources"]["sugar"] = {"amount": 5000 * UNIT, "maxAmount": 10000 * UNIT}
    slots["exploration"] = {"experience": 299}
    load_game(page, slots)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Expedition Coordination", exact=True)).to_have_count(0)
    slots["exploration"]["experience"] = 300
    load_game(page, slots)
    before = save_game(page)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    action = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Expedition Coordination", exact=True))
    expect(action.locator(".action-costs")).to_contain_text("/1000 sugar")
    expect(action.locator(".action-costs")).to_contain_text("/100 knowledge")
    action.get_by_role("button").click()
    purchased = save_game(page)
    assert purchased["basicResources"]["sugar"]["amount"] == before["basicResources"]["sugar"]["amount"] - 1000 * UNIT
    assert purchased["basicResources"]["knowledge"]["amount"] == before["basicResources"]["knowledge"]["amount"] - 100 * UNIT
    explore_tab(page)
    expect(page.locator(".expedition-status")).to_contain_text("Expeditions: 0 / 2")
    button = page.get_by_role("button", name="Explore Surroundings", exact=True)
    button.click()
    button.click()
    expect(button).to_be_disabled()
    assert len(save_game(page)["exploration"]["explorationParties"]) == 2
    page.reload(wait_until="networkidle")
    page.clock.run_for(15000)
    returned = save_game(page)
    assert not returned["exploration"]["explorationParties"]
    returned["exploration"]["experience"] = 1000
    load_game(page, returned)
    explore_tab(page)
    expect(page.locator(".expedition-status")).to_contain_text("Expeditions: 0 / 3")
    for _ in range(3):
        button.click()
    expect(button).to_be_disabled()
    assert len(save_game(page)["exploration"]["explorationParties"]) == 3
    explore_tab(page)
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: live diminishing rates, actual income, upgrades, population pressure, paid slot, persistence, slot stacking and limits")


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
                for width in (390, 1920):
                    context = browser.new_context(viewport={"width": width, "height": 844})
                    check_foraging(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
