"""Discoveries navigation, useful worker limits, and optional scout travel speed."""

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


def check_staffing(page, width, url):
    now = datetime(2026, 11, 5, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 100 * UNIT
    game["ants"]["scouts"] = 5 * UNIT
    game["ants"]["eggsAndLarvae"] = []
    game["ants"]["tasks"] = [[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]]
    game["basicResources"]["sugar"] = {"amount": 9000 * UNIT, "maxAmount": 10000 * UNIT}
    game["nestAttributes"]["maxAnts"] = 200 * UNIT
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["exploration"] = {"experience": 1500}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = False

    def route(name):
        return page.locator(".exploration-destination").filter(has=page.get_by_role("button", name="Explore " + name, exact=True))

    load_game(page, game)
    expect(page.get_by_role("tab", name="Discoveries", exact=True)).to_have_count(0)
    explore_tab(page)
    expect(page.locator("#explorer-count")).to_have_attribute("max", "37")
    field = page.locator("#explorer-count")
    more = page.get_by_role("button", name="Increase expedition workers", exact=True)
    less = page.get_by_role("button", name="Decrease expedition workers", exact=True)
    expect(field).to_have_attribute("type", "number")
    expect(field).to_have_attribute("inputmode", "numeric")
    expect(less).to_be_disabled()
    more.click()
    expect(field).to_have_value("2")
    expect(route("Surroundings").locator(".action-costs")).to_contain_text("/20 sugar")
    less.click()
    expect(field).to_have_value("1")
    field.fill("17")
    more.click()
    expect(field).to_have_value("18")
    less.click()
    expect(field).to_have_value("17")
    field.fill("999")
    expect(field).to_have_value("37")
    expect(more).to_be_disabled()
    field.fill("0")
    expect(field).to_have_value("1")
    expect(less).to_be_disabled()
    field.fill("")
    field.press("Tab")
    expect(field).to_have_value("1")
    page.get_by_role("button", name="Send 37 workers", exact=True).click()
    expect(page.locator("#explorer-count")).to_have_value("37")
    expect(route("Surroundings").locator(".action-costs")).to_contain_text("/370 sugar")
    expect(page.locator("#exploreContent .destination-survey")).to_have_count(0)

    game["exploration"]["surveys"] = {"woodland-edge": 4, "nectar-meadow": 2}
    load_game(page, game)
    page.get_by_role("tab", name="Discoveries", exact=True).click()
    expect(page.locator("#discoveriesContent")).to_be_visible()
    expect(page.locator('.survey-entry[data-destination="woodland-edge"]')).to_contain_text("Survey 4 / 5")
    expect(page.locator('.survey-entry[data-destination="nectar-meadow"]')).to_contain_text("Survey 2 / 3")
    expect(page.locator(".discovery-entry")).to_have_count(0)
    expect(page.locator("#discoveriesContent")).not_to_contain_text("Dew-covered Plants")
    assert_fits(page, width)
    explore_tab(page)
    expect(page.locator("#exploreContent .exploration-discoveries")).to_have_count(0)
    expect(page.locator("#exploreContent .destination-survey")).to_have_count(0)
    page.get_by_role("button", name="Send 5 workers", exact=True).click()
    page.locator("#extra-scouts").select_option("3")
    expect(route("Woodland Edge").locator(".destination-preview")).to_contain_text("25.8s")
    expect(route("Woodland Edge").locator(".action-costs")).to_contain_text("/130 sugar")
    expect(route("Woodland Edge").locator(".action-costs")).to_contain_text("/4 scouts")
    before = save_game(page)
    explore_tab(page)
    route("Woodland Edge").get_by_role("button").click()
    sent = save_game(page)
    party = sent["exploration"]["explorationParties"][0]
    assert party["amountWorkers"] == 5 * UNIT
    assert party["scouts"] == 4 * UNIT
    assert party["extraScouts"] == 3
    assert party["targetTick"] - party["startingTick"] == 129
    assert sent["basicResources"]["sugar"]["amount"] == before["basicResources"]["sugar"]["amount"] - 130 * UNIT
    explore_tab(page)
    expect(page.locator(".expedition-status")).to_contain_text("4 scouts")
    expect(page.locator("#extra-scouts")).to_have_value("1")
    expect(route("Woodland Edge").get_by_role("button")).to_be_disabled()
    expect(route("Woodland Edge").locator('.action-cost[aria-label^="scouts"] .text-danger')).to_have_text("1")
    page.locator("#extra-scouts").select_option("0")
    expect(route("Woodland Edge").get_by_role("button")).to_be_enabled()
    page.reload(wait_until="networkidle")
    page.clock.run_for(25600)
    assert len(save_game(page)["exploration"]["explorationParties"]) == 1
    page.clock.run_for(200)
    returned = save_game(page)
    assert not returned["exploration"]["explorationParties"]
    assert returned["exploration"]["surveys"]["woodland-edge"] == 5
    assert returned["statistics"]["explorationsCompleted"]["thisRun"] == 1
    page.get_by_role("tab", name="Discoveries", exact=True).click()
    expect(page.locator(".discovery-entry")).to_contain_text("Wild Aphids")
    expect(page.locator('.survey-entry[data-destination="woodland-edge"]')).to_have_count(0)
    explore_tab(page)
    expect(page.locator("#exploreContent .discovery-entry")).to_have_count(0)
    assert_fits(page, width)

    # Available workers shrinking automatically updates selection and the next action's real cost.
    smaller = copy.deepcopy(game)
    smaller["ants"]["workers"] = 38 * UNIT
    load_game(page, smaller)
    explore_tab(page)
    page.get_by_role("button", name="Send 37 workers", exact=True).click()
    route("Surroundings").get_by_role("button").click()
    expect(page.locator("#explorer-count")).to_have_attribute("max", "1")
    expect(page.locator("#explorer-count")).to_have_value("1")
    expect(page.get_by_role("button", name="Send 37 workers", exact=True)).to_be_disabled()
    expect(route("Surroundings").locator(".action-costs")).to_contain_text("/10 sugar")
    field.fill("37")
    expect(field).to_have_value("1")
    expect(more).to_be_disabled()
    expect(less).to_be_disabled()
    route("Surroundings").get_by_role("button").click()
    expect(field).to_be_disabled()
    expect(more).to_be_disabled()
    expect(less).to_be_disabled()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: separate discovery tab, hidden content, worker cap/presets, live clamping, scout costs/speed, reservations, saved returns and survey completion, layout")


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
                    check_staffing(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
