"""Colony upgrades, brood maturation, and guaranteed-sugar expeditions."""

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


def check_colony(page, width, url):
    now = datetime(2026, 11, 4, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 20 * UNIT
    game["ants"]["scouts"] = UNIT
    game["ants"]["eggsAndLarvae"] = []
    game["ants"]["tasks"] = [[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, 5 * UNIT], [{"Explorer": {}}, 0]]
    game["basicResources"]["sugar"] = {"amount": 6000 * UNIT, "maxAmount": 10000 * UNIT}
    game["basicResources"]["tunnelingSpace"] = {"amount": 0, "maxAmount": 10000 * UNIT}
    game["basicResources"]["protein"] = {"amount": 100 * UNIT, "maxAmount": 100 * UNIT}
    game["basicResources"]["knowledge"] = {"amount": 1000 * UNIT, "maxAmount": 9223372036854775807}
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["exploration"] = {"experience": 299}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = False

    def upgrades_tab():
        page.get_by_role("tab", name="Upgrades", exact=True).click()

    def route():
        return page.locator(".exploration-destination").filter(has=page.get_by_role("button", name="Explore Nectar Meadow", exact=True))

    def purchase(title, sugar, protein, knowledge):
        before = save_game(page)
        upgrades_tab()
        action = page.locator(".action-cell").filter(has=page.get_by_role("button", name=title, exact=True))
        for amount, resource in ((sugar, "sugar"), (protein, "protein"), (knowledge, "knowledge")):
            expect(action.locator(".action-costs")).to_contain_text(f"/{amount} {resource}")
        action.get_by_role("button").click()
        after = save_game(page)
        for amount, resource in ((sugar, "sugar"), (protein, "protein"), (knowledge, "knowledge")):
            assert after["basicResources"][resource]["amount"] == before["basicResources"][resource]["amount"] - amount * UNIT
        return after

    load_game(page, game)
    upgrades_tab()
    expect(page.get_by_role("button", name="Reinforced Mandibles", exact=True)).to_have_count(0)
    expect(page.get_by_role("button", name="Brood Care", exact=True)).to_have_count(0)
    explore_tab(page)
    expect(route()).to_have_count(0)

    game["exploration"]["experience"] = 300
    load_game(page, game)
    upgraded = purchase("Reinforced Mandibles", 1200, 20, 100)
    tunneling = page.locator(".resource-row").filter(has=page.get_by_text("Tunneling Space", exact=True))
    expect(tunneling).to_contain_text("+7.50/s")
    page.get_by_role("tab", name="Tasks", exact=False).click()
    task = page.locator("#task-tunneler")
    task.get_by_label("About Tunneler", exact=True).click()
    expect(task.locator(".task-description")).to_have_text("+1.5 tunneling space per second / ant")
    task.get_by_label("About Tunneler", exact=True).click()
    before = save_game(page)
    page.clock.run_for(1000)
    after = save_game(page)
    ticks = after["world"]["currentTick"] - before["world"]["currentTick"]
    assert after["basicResources"]["tunnelingSpace"]["amount"] - before["basicResources"]["tunnelingSpace"]["amount"] == ticks * 15000

    # Brood Care stays hidden without a Nursery, even at level four.
    upgraded["exploration"]["experience"] = 600
    no_nursery = copy.deepcopy(upgraded)
    no_nursery["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 0
    load_game(page, no_nursery)
    upgrades_tab()
    expect(page.get_by_role("button", name="Brood Care", exact=True)).to_have_count(0)
    load_game(page, upgraded)
    cared = purchase("Brood Care", 2000, 40, 180)
    tick = cared["world"]["currentTick"]
    cared["ants"]["eggsAndLarvae"] = [{"Egg": {"initialTick": tick}}, {"ScoutEgg": {"initialTick": tick}}]
    load_game(page, cared)
    page.clock.run_for(7800)
    growing = save_game(page)
    assert growing["ants"]["workers"] == 20 * UNIT
    page.clock.run_for(200)
    grown = save_game(page)
    assert grown["ants"]["workers"] == 21 * UNIT
    assert grown["ants"]["scouts"] == UNIT
    page.clock.run_for(16000)
    assert save_game(page)["ants"]["scouts"] == 2 * UNIT

    # A guaranteed sugar route is a repeatable alternative to passive foraging.
    load_game(page, upgraded)
    explore_tab(page)
    page.locator("#explorer-count").fill("5")
    expect(route().locator(".destination-preview")).to_contain_text("1050 sugar")
    expect(route().locator(".action-costs")).to_contain_text("/200 sugar")
    assert_fits(page, width)
    before = save_game(page)
    explore_tab(page)
    route().get_by_role("button").click()
    sent = save_game(page)
    assert sent["basicResources"]["sugar"]["amount"] == before["basicResources"]["sugar"]["amount"] - 200 * UNIT
    assert sent["exploration"]["explorationParties"][0]["destination"] == "nectar-meadow"
    page.reload(wait_until="networkidle")
    page.clock.run_for(90000)
    returned = save_game(page)
    assert not returned["exploration"]["explorationParties"]
    assert returned["exploration"]["experience"] == 690
    assert returned["basicResources"]["knowledge"]["amount"] == before["basicResources"]["knowledge"]["amount"] + 28 * UNIT
    assert returned["basicResources"]["sugar"]["amount"] > before["basicResources"]["sugar"]["amount"]
    assert not returned["exploration"]["discoveredCarcasses"]
    explore_tab(page)
    page.locator("#explorer-count").fill("5")
    expect(route().get_by_role("button")).to_be_enabled()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: hidden content, upgrade costs, live tunneling output, faster brood, meadow rewards, save persistence, layout")


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
                    check_colony(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
