"""Dry Clearing surveys, water scavenging, and durable milestone statistics."""

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


def check_water(page, width, url):
    now = datetime(2026, 11, 5, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["ants"]["workers"] = 30 * UNIT
    game["ants"]["scouts"] = 2 * UNIT
    game["ants"]["eggsAndLarvae"] = []
    game["ants"]["tasks"] = [[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]]
    game["basicResources"]["sugar"] = {"amount": 9000 * UNIT, "maxAmount": 10000 * UNIT}
    game["basicResources"]["water"] = {"amount": 0, "maxAmount": 100 * UNIT}
    game["statistics"] = copy.deepcopy(initial["statistics"])
    game["unlocks"]["resources"]["showWater"] = False
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["exploration"] = {"experience": 1500, "surveys": {"rotten-log": 3}}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = False

    def route(name):
        select_expedition(page, name)
        return page.locator(".exploration-destination").filter(has=page.get_by_role("button", name="Explore " + name, exact=True))

    def water_row():
        return page.locator(".resource-row").filter(has=page.get_by_text("Water", exact=True))

    def open_stats():
        page.get_by_role("tab", name="Settings", exact=True).click()
        if page.locator(".colony-statistics").get_attribute("open") is None:
            page.locator(".colony-statistics > summary").click()

    def stat(label):
        return page.locator(".statistic-row").filter(has=page.get_by_text(label, exact=True)).locator("b")

    load_game(page, game)
    expect(water_row()).to_have_count(0)
    page.get_by_role("button", name="Gather Sugar", exact=True).click()
    open_stats()
    expect(stat("Sugar collected")).to_have_text("1")
    expect(stat("Expeditions completed")).to_have_text("0")
    expect(page.get_by_text("Water collected", exact=True)).to_have_count(0)
    explore_tab(page)
    expect(route("Dry Clearing")).to_have_count(0)
    expect(route("Scavenge Water")).to_have_count(0)
    page.locator("#explorer-count").fill("5")
    route("Rotten Log").get_by_role("button").click()
    sent = save_game(page)
    assert sent["statistics"]["explorationsCount"]["thisRun"] == 1
    assert sent["statistics"]["explorationsCompleted"]["thisRun"] == 0
    page.reload(wait_until="networkidle")
    page.clock.run_for(60000)
    found = save_game(page)
    assert found["statistics"]["explorationsCompleted"]["thisRun"] == 1
    assert found["exploration"]["surveys"]["rotten-log"] == 4
    explore_tab(page)
    expect(route("Dry Clearing")).to_have_count(1)
    expect(route("Scavenge Water")).to_have_count(0)
    page.locator("#explorer-count").fill("10")
    expect(route("Dry Clearing").locator(".action-costs")).to_contain_text("/350 sugar")
    for _ in range(3):
        explore_tab(page)
        page.locator("#explorer-count").fill("10")
        route("Dry Clearing").get_by_role("button").click()
        page.clock.run_for(120000)
        surveyed = save_game(page)
    assert surveyed["exploration"]["surveys"]["dry-clearing"] == 3
    assert surveyed["statistics"]["explorationsCompleted"]["thisRun"] == 4
    expect(water_row()).to_have_count(0)
    explore_tab(page)
    page.locator("#explorer-count").fill("5")
    expect(route("Scavenge Water")).to_have_count(1)
    expect(route("Scavenge Water").locator(".action-costs")).to_contain_text("/125 sugar")
    expect(route("Scavenge Water").locator(".destination-preview")).to_contain_text("100 water")
    route("Scavenge Water").get_by_role("button").click()
    save_game(page)
    page.reload(wait_until="networkidle")
    page.clock.run_for(90000)
    harvested = save_game(page)
    assert harvested["basicResources"]["water"]["amount"] == 100 * UNIT
    assert harvested["statistics"]["waterU"]["thisRun"] == 100 * UNIT
    assert harvested["statistics"]["waterU"]["total"] == 100 * UNIT
    assert harvested["statistics"]["explorationsCompleted"]["thisRun"] == 5
    expect(water_row().locator(".resource-icon-water svg")).to_have_count(1)
    assert water_row().locator(".resource-amount").evaluate("el => el.classList.contains('text-primary') && el.classList.contains('fw-bold')")
    open_stats()
    expect(stat("Water collected")).to_have_text("100")
    expect(stat("Expeditions launched")).to_have_text("5")
    expect(stat("Expeditions completed")).to_have_text("5")
    assert_fits(page, width)

    # A full reservoir cannot farm water milestones through discarded overflow.
    explore_tab(page)
    page.locator("#explorer-count").fill("5")
    route("Scavenge Water").get_by_role("button").click()
    page.clock.run_for(90000)
    full = save_game(page)
    assert full["statistics"]["waterU"]["thisRun"] == 100 * UNIT
    assert full["statistics"]["explorationsCompleted"]["thisRun"] == 6
    assert full["statistics"]["sugarU"]["thisRun"] == UNIT
    page.reload(wait_until="networkidle")
    assert save_game(page)["statistics"] == full["statistics"]
    full["basicResources"]["water"]["amount"] = 0
    load_game(page, full)
    expect(water_row()).to_be_visible()
    open_stats()
    expect(stat("Water collected")).to_have_text("100")
    expect(stat("Expeditions completed")).to_have_text("6")
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: hidden route chain, surveys, visible costs, saved missions, water cap/icon, permanent visibility, collected statistics, overflow and reload")


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
                    check_water(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
