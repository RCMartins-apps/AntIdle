"""Woodland resin surveys/collection and one-time Royal Nursery construction."""

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


def check_royal_nursery(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["world"]["currentTick"] = game["world"]["targetTick"] = 100
    game["ants"].update(workers=30 * UNIT, scouts=2 * UNIT, repletes=3 * UNIT, eggsAndLarvae=[],
        tasks=[[{"SugarCollector": {}}, 30 * UNIT], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]],
        defaultTask={"SugarCollector": {}},
        ages={"active": True, "workers": [{"expiresAt": 18000, "count": 30}],
            "scouts": [{"expiresAt": 18000, "count": 2}], "repletes": [{"expiresAt": 36000, "count": 3}]})
    game["nestAttributes"]["maxAnts"] = 100 * UNIT
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["nestAttributes"]["chambers"]["repleteChamber"]["level"] = 3
    game["nestAttributes"]["chambers"]["foodStorageChamber"]["level"] = 4
    game["basicResources"].update(sugar={"amount": 30000 * UNIT, "maxAmount": 40000 * UNIT},
        protein={"amount": 300 * UNIT, "maxAmount": 500 * UNIT}, water={"amount": 400 * UNIT, "maxAmount": 400 * UNIT},
        knowledge={"amount": 3000 * UNIT, "maxAmount": 9223372036854775807},
        tunnelingSpace={"amount": 5000 * UNIT, "maxAmount": 5000 * UNIT}, resin={"amount": 0, "maxAmount": 200 * UNIT})
    game["unlocks"]["resources"]["showResin"] = False
    game["exploration"] = {"experience": 2099}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] in ("DefaultWorkers", "ConstructionPlanning", "ProteinStorage", "RepleteStorage"))

    def nest():
        page.get_by_role("tab", name="Nest", exact=True).click()

    def route(destination):
        return page.locator(f'.exploration-destination[data-destination="{destination}"]')

    def resin_row():
        return page.locator(".resource-row").filter(has=page.get_by_text("Resin", exact=True))

    build = page.get_by_role("button", name="Royal Nursery", exact=True)
    research = page.get_by_role("button", name="Royal Nursery Planning", exact=True)
    load_game(page, game)
    expect(build).to_have_count(0)
    expect(resin_row()).to_have_count(0)
    explore_tab(page)
    expect(route("fallen-pine")).to_have_count(0)
    expect(route("collect-resin")).to_have_count(0)
    game["exploration"]["experience"] = 2100
    load_game(page, game)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(research).to_have_count(0)
    for index in range(3):
        explore_tab(page)
        page.locator("#explorer-count").fill("10")
        expect(route("fallen-pine").locator(".action-costs")).to_contain_text("/300 sugar")
        expect(route("collect-resin")).to_have_count(0)
        route("fallen-pine").get_by_role("button", name="Explore Fallen Pine", exact=True).click()
        page.clock.run_for(120000)
        surveyed = save_game(page)
        assert surveyed["exploration"]["surveys"]["fallen-pine"] == index + 1
    assert sum(m.startswith("Discovery: Resin Seep!") for m in surveyed["messages"]) == 1
    expect(resin_row()).to_contain_text("0")
    expect(resin_row().locator(".resource-icon-resin svg")).to_have_count(1)
    explore_tab(page)
    expect(route("collect-resin")).to_have_count(1)
    page.locator("#explorer-count").fill("10")
    expect(route("collect-resin").locator(".action-costs")).to_contain_text("/350 sugar")
    route("collect-resin").get_by_role("button", name="Collect Resin", exact=True).click()
    page.clock.run_for(120000)
    first = save_game(page)
    assert first["basicResources"]["resin"]["amount"] == 343750
    assert first["statistics"]["resinU"]["thisRun"] == 343750
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(research).to_be_enabled()
    research_card = page.locator(".action-cell").filter(has=research)
    for value, label in ((5000, "sugar"), (100, "protein"), (700, "knowledge"), (150, "water")):
        expect(research_card.locator(".action-costs")).to_contain_text(f"/{value} {label}")
    research.click()
    paid = save_game(page)
    for field, cost in (("sugar", 5000), ("protein", 100), ("knowledge", 700), ("water", 150)):
        assert first["basicResources"][field]["amount"] - paid["basicResources"][field]["amount"] == cost * UNIT
    nest()
    card = page.locator(".action-cell").filter(has=build)
    for value, label in ((3000, "sugar"), (50, "protein"), (100, "water"), (1500, "tunneling space"), (80, "resin")):
        expect(card.locator(".action-costs")).to_contain_text(f"/{value} {label}")
    expect(card.locator('.action-cost[aria-label^="resin"] .text-danger')).to_have_count(1)
    if width <= 767:
        page.get_by_label("Details for Royal Nursery", exact=True).click()
        expect(card.locator("details[open]")).to_contain_text("+1 royal brood slot")
    assert_fits(page, width)
    build.click()
    expect(build).to_be_disabled()
    expect(page.locator(".queued-building")).to_have_count(1)
    expect(page.locator(".build-task .text-danger")).to_have_text("Never")
    page.clock.run_for(1000)
    waiting = save_game(page)
    assert waiting["nestAttributes"]["chambers"]["royalNurseryChamber"]["level"] == 0
    for _ in range(2):
        explore_tab(page)
        page.locator("#explorer-count").fill("10")
        route("collect-resin").get_by_role("button", name="Collect Resin", exact=True).click()
        page.clock.run_for(120000)
        save_game(page)
    page.clock.run_for(200)
    built = save_game(page)
    assert built["nestAttributes"]["chambers"]["royalNurseryChamber"]["level"] == 1
    assert built["nestAttributes"]["buildQueue"] == []
    assert built["nestAttributes"]["maxAnts"] == 100 * UNIT
    assert built["ants"]["workers"] == 30 * UNIT and built["ants"]["eggsAndLarvae"] == []
    assert built["statistics"]["resinU"]["thisRun"] == 1031250
    assert built["basicResources"]["resin"]["amount"] == 1031250 - 80 * UNIT
    nest()
    expect(build).to_have_count(0)
    expect(page.locator(".royal-nursery-ready")).to_contain_text("Raise Royal Egg")
    assert_fits(page, width)
    page.reload(wait_until="networkidle")
    assert save_game(page)["nestAttributes"]["chambers"]["royalNurseryChamber"]["level"] == 1
    assert save_game(page)["basicResources"]["resin"] == built["basicResources"]["resin"]
    empty = copy.deepcopy(built)
    empty["basicResources"]["resin"]["amount"] = 0
    load_game(page, empty)
    expect(resin_row()).to_contain_text("0")
    old = copy.deepcopy(game)
    old["basicResources"].pop("resin")
    old["unlocks"]["resources"].pop("showResin")
    old["nestAttributes"]["chambers"].pop("royalNurseryChamber", None)
    old["statistics"].pop("resinU", None)
    old["upgrades"]["upgrades"] = [u for u in old["upgrades"]["upgrades"] if u["name"] != "RoyalNurseryPlanning"]
    load_game(page, old)
    restored = save_game(page)
    assert restored["basicResources"]["resin"] == {"amount": 0, "maxAmount": 200 * UNIT}
    assert restored["nestAttributes"]["chambers"]["royalNurseryChamber"]["level"] == 0
    expect(resin_row()).to_have_count(0)
    assert not errors, errors
    print(f"PASS {width}px: hidden gates, survey/discovery, resin reward/statistics, visible costs, queue waits, single nursery, saved zero balance and old defaults, layout", flush=True)


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
                    check_royal_nursery(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
