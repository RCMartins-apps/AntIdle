"""Exploration surveys, one-time discoveries, aphid husbandry, and the seed route."""

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


def check_discoveries(page, width, url):
    now = datetime(2026, 11, 4, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 40 * UNIT
    game["ants"]["scouts"] = 2 * UNIT
    game["ants"]["eggsAndLarvae"] = []
    game["basicResources"]["tunnelingSpace"] = {"amount": 5000 * UNIT, "maxAmount": 10000 * UNIT}
    game["ants"]["tasks"] = [[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]]
    game["basicResources"]["sugar"] = {"amount": 6000 * UNIT, "maxAmount": 10000 * UNIT}
    game["basicResources"]["protein"] = {"amount": 50 * UNIT, "maxAmount": 100 * UNIT}
    game["basicResources"]["knowledge"] = {"amount": 1000 * UNIT, "maxAmount": 9223372036854775807}
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["exploration"] = {"experience": 1000, "surveys": {"woodland-edge": 4, "nectar-meadow": 2}}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = upgrade["name"] == "DefaultWorkers"

    def route(name):
        return page.locator(".exploration-destination").filter(has=page.get_by_role("button", name="Explore " + name, exact=True))

    def resource(name):
        return page.locator(".resource-row").filter(has=page.get_by_text(name, exact=True))

    load_game(page, game)
    page.get_by_role("tab", name="Tasks", exact=False).click()
    expect(page.locator("#task-aphidtender")).to_have_count(0)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Aphid Tending", exact=True)).to_have_count(0)
    explore_tab(page)
    expect(page.locator(".exploration-discoveries")).to_have_count(0)
    expect(route("Seed Gathering")).to_have_count(0)
    expect(route("Woodland Edge").locator(".destination-survey")).to_have_text("Survey 4 / 5")
    route("Woodland Edge").get_by_role("button").click()
    assert save_game(page)["exploration"]["surveys"]["woodland-edge"] == 4
    # Save and reload during travel; discovery only happens on return.
    page.reload(wait_until="networkidle")
    page.clock.run_for(45000)
    found = save_game(page)
    assert found["exploration"]["surveys"]["woodland-edge"] == 5
    assert sum(m.startswith("Discovery: Wild Aphids!") for m in found["messages"]) == 1
    explore_tab(page)
    expect(page.locator(".discovery-entry")).to_have_count(1)
    expect(page.locator(".discovery-entry")).to_contain_text("Wild Aphids")
    expect(route("Woodland Edge").locator(".destination-survey")).to_have_count(0)
    before = save_game(page)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    action = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Aphid Tending", exact=True))
    for value, label in ((1500, "sugar"), (20, "protein"), (100, "knowledge")):
        expect(action.locator(".action-costs")).to_contain_text(f"/{value} {label}")
    action.get_by_role("button").click()
    after = save_game(page)
    for value, label in ((1500, "sugar"), (20, "protein"), (100, "knowledge")):
        assert after["basicResources"][label]["amount"] == before["basicResources"][label]["amount"] - value * UNIT
    page.get_by_role("tab", name="Tasks", exact=False).click()
    expect(page.locator("#task-aphidtender")).to_contain_text("0 / 0")
    expect(page.get_by_role("button", name="Assign Aphid Tender", exact=True)).to_be_disabled()
    page.get_by_role("tab", name="Nest", exact=True).click()
    chamber = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Aphid Chamber", exact=True))
    expect(chamber.locator(".action-costs")).to_contain_text("/75 tunneling space")
    expect(chamber.locator(".action-costs")).to_contain_text("/2 protein")
    chamber.get_by_role("button").click()
    page.clock.run_for(200)
    built = save_game(page)
    assert built["nestAttributes"]["chambers"]["aphidChamber"]["level"] == 1
    page.get_by_role("tab", name="Tasks", exact=False).click()
    page.get_by_role("button", name="Assign Aphid Tender", exact=True).click()
    expect(page.locator("#task-aphidtender")).to_contain_text("1 / 1")
    expect(page.get_by_role("button", name="Assign Aphid Tender", exact=True)).to_be_disabled()
    task = page.locator("#task-aphidtender")
    description = "0.8 sugar/s · −0.01 protein/s per tender"
    if width <= 767:
        expect(task.locator(".task-description")).to_have_text(description)
    else:
        task.hover()
        expect(page.locator(".game-tooltip")).to_have_text(description)
    expect(resource("Protein")).to_contain_text("-0.01/s")
    expect(resource("Sugar")).to_contain_text("-7.60/s")  # 40 workers + 2 scouts, one tender.
    before = save_game(page)
    page.clock.run_for(1000)
    after = save_game(page)
    ticks = after["world"]["currentTick"] - before["world"]["currentTick"]
    assert after["basicResources"]["sugar"]["amount"] - before["basicResources"]["sugar"]["amount"] == ticks * -15200
    assert after["basicResources"]["protein"]["amount"] - before["basicResources"]["protein"]["amount"] == ticks * -20
    # Protein shortage pauses production; feeding resumes when protein is restored.
    empty = copy.deepcopy(after)
    empty["basicResources"]["protein"]["amount"] = 0
    load_game(page, empty)
    expect(resource("Sugar")).to_contain_text("-8.40/s")
    expect(resource("Protein")).to_contain_text("0/s")
    page.get_by_role("tab", name="Tasks", exact=False).click()
    if width <= 767:
        expect(page.locator("#task-aphidtender .text-danger")).to_have_text("Needs protein")
    load_game(page, after)

    # The new task can be the default pool; expeditions borrow and return its workers.
    page.get_by_role("tab", name="Tasks", exact=False).click()
    page.locator("#default-worker-task").select_option("AphidTender")
    assert_fits(page, width)
    explore_tab(page)
    page.locator("#explorer-count").fill("5")
    expect(route("Nectar Meadow").locator(".destination-survey")).to_have_text("Survey 2 / 3")
    route("Nectar Meadow").get_by_role("button").click()
    page.clock.run_for(90000)
    seeded = save_game(page)
    assert seeded["exploration"]["surveys"]["nectar-meadow"] == 3
    assert next(amount for task, amount in seeded["ants"]["tasks"] if "AphidTender" in task) == UNIT
    explore_tab(page)
    expect(page.locator(".discovery-entry")).to_have_count(2)
    expect(route("Seed Gathering")).to_have_count(1)
    expect(route("Seed Gathering").get_by_role("button")).to_be_disabled()  # Needs ten workers.
    page.locator("#explorer-count").fill("10")
    expect(route("Seed Gathering").locator(".action-costs")).to_contain_text("/250 sugar")
    expect(route("Seed Gathering").locator(".destination-preview")).to_contain_text("protein")
    before = save_game(page)
    explore_tab(page)
    route("Seed Gathering").get_by_role("button").click()
    save_game(page)
    page.reload(wait_until="networkidle")
    page.clock.run_for(120000)
    returned = save_game(page)
    assert not returned["exploration"]["explorationParties"]
    assert returned["basicResources"]["protein"]["amount"] > before["basicResources"]["protein"]["amount"]
    assert len(returned["exploration"]["discoveredCarcasses"]) == len(before["exploration"]["discoveredCarcasses"])
    assert returned["ants"]["defaultTask"] == {"AphidTender": {}}
    assert sum(m.startswith("Discovery: Wild Aphids!") for m in returned["messages"]) == 1
    assert sum(m.startswith("Discovery: Seed Cache!") for m in returned["messages"]) == 1
    # Every revealed resource remains visible when all balances and populations reach zero.
    depleted = copy.deepcopy(returned)
    for value in depleted["basicResources"].values():
        value["amount"] = 0
    depleted["ants"]["workers"] = 0
    depleted["ants"]["scouts"] = 0
    depleted["ants"]["eggsAndLarvae"] = []
    depleted["ants"]["tasks"] = [[task, 0] for task, _ in depleted["ants"]["tasks"]]
    depleted["ants"]["defaultTask"] = None
    load_game(page, depleted)
    for name in ("Sugar", "Tunneling Space", "Protein", "Knowledge", "Eggs", "Ants", "Workers", "Scouts"):
        expect(resource(name)).to_be_visible()
    page.reload(wait_until="networkidle")
    for name in ("Sugar", "Tunneling Space", "Protein", "Knowledge", "Eggs", "Ants", "Workers", "Scouts"):
        expect(resource(name)).to_be_visible()
    explore_tab(page)
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: hidden discoveries, survey completion, research costs, live aphid production, default workers, seed rewards, persistence, layout")


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
                    check_discoveries(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
