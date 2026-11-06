"""Outpost growth, staged research, exact costs, live production and saved progression."""

import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game


def check_progression(page, width, url):
    now = datetime(2026, 11, 6, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["world"]["currentTick"] = game["world"]["targetTick"] = 100
    game["ants"].update(workers=30 * UNIT, scouts=2 * UNIT, repletes=9 * UNIT, eggsAndLarvae=[],
        tasks=[[{"SugarCollector": {}}, 0], [{"SeedForager": {}}, 5 * UNIT], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]],
        defaultTask={"SeedForager": {}},
        ages={"active": True, "workers": [{"expiresAt": 18000, "count": 30}],
            "scouts": [{"expiresAt": 18000, "count": 2}], "repletes": [{"expiresAt": 36000, "count": 9}]})
    game["nestAttributes"].update(dryClearingOutpost=True, dryClearingExtensions=0, maxAnts=100 * UNIT)
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["nestAttributes"]["chambers"]["repleteChamber"]["level"] = 9
    game["nestAttributes"]["chambers"]["foodStorageChamber"]["level"] = 9
    game["basicResources"].update(sugar={"amount": 30000 * UNIT, "maxAmount": 40000 * UNIT},
        protein={"amount": 800 * UNIT, "maxAmount": 1000 * UNIT}, water={"amount": 900 * UNIT, "maxAmount": 1000 * UNIT},
        knowledge={"amount": 5000 * UNIT, "maxAmount": 9223372036854775807},
        tunnelingSpace={"amount": 4000 * UNIT, "maxAmount": 4000 * UNIT})
    game["exploration"] = {"experience": 1500, "surveys": {"dry-clearing": 3}}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] in ("DefaultWorkers", "RepleteStorage", "ProteinStorage"))

    def nest():
        page.get_by_role("tab", name="Nest", exact=True).click()

    def research():
        page.get_by_role("tab", name="Upgrades", exact=True).click()

    def resource(name):
        return page.locator(".resource-row").filter(has=page.get_by_text(name, exact=True))

    def action(name):
        return page.locator(".action-cell").filter(has=page.get_by_role("button", name=name, exact=True))

    def buy(name, costs):
        before = save_game(page)
        research()
        for field, value, label in costs:
            expect(action(name).locator(".action-costs")).to_contain_text(f"/{value} {label}")
        page.get_by_role("button", name=name, exact=True).click()
        after = save_game(page)
        for field, value, label in costs:
            assert before["basicResources"][field]["amount"] - after["basicResources"][field]["amount"] == value * UNIT
        return after

    expand = page.get_by_role("button", name="Expand Dry Clearing Outpost", exact=True)
    load_game(page, game)
    expect(expand).to_have_count(0)
    research()
    expect(page.get_by_role("button", name="Covered Trails", exact=True)).to_have_count(0)
    expect(page.get_by_role("button", name="Seed Processing", exact=True)).to_have_count(0)
    level7 = copy.deepcopy(game)
    level7["exploration"]["experience"] = 2100
    poor = copy.deepcopy(level7)
    poor["ants"]["repletes"] = UNIT
    poor["ants"]["ages"]["repletes"] = [{"expiresAt": 36000, "count": 1}]
    poor["basicResources"]["water"] = {"amount": 200 * UNIT, "maxAmount": 200 * UNIT}
    load_game(page, poor)
    nest()
    expect(expand).to_be_disabled()
    expect(action("Expand Dry Clearing Outpost").locator('.action-cost[aria-label^="water"] .text-danger')).to_have_count(2)

    load_game(page, level7)
    buy("Covered Trails", [("sugar", 3000, "sugar"), ("protein", 60, "protein"), ("knowledge", 300, "knowledge"), ("water", 100, "water")])
    expect(resource("Water")).to_contain_text("-0.075/s")
    nest()
    expect(expand).to_be_enabled()
    costs = [("sugar", 5000, "sugar"), ("protein", 75, "protein"), ("knowledge", 500, "knowledge"),
        ("tunnelingSpace", 1000, "tunneling space"), ("water", 250, "water")]
    for field, value, label in costs:
        expect(action("Expand Dry Clearing Outpost").locator(".action-costs")).to_contain_text(f"/{value} {label}")
    if width <= 767:
        page.get_by_label("Details for Expand Dry Clearing Outpost", exact=True).click()
        expect(action("Expand Dry Clearing Outpost").locator("details[open]")).to_contain_text("+5 Seed Forager slots")
    assert_fits(page, width)
    before = save_game(page)
    nest()
    expand.click()
    second = save_game(page)
    for field, value, label in costs:
        assert before["basicResources"][field]["amount"] - second["basicResources"][field]["amount"] == value * UNIT
    assert second["nestAttributes"]["dryClearingExtensions"] == 1
    assert second["nestAttributes"]["maxAnts"] == 125 * UNIT
    assert sum(n for task, n in second["ants"]["tasks"] if task == {"SeedForager": {}}) == 10 * UNIT
    nest()
    expect(expand).to_have_count(0)
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("Level 2")
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("10 Seed Forager slots")
    page.clock.run_for(1000)
    working = save_game(page)
    assert working["basicResources"]["protein"]["amount"] - second["basicResources"]["protein"]["amount"] == 4000
    assert second["basicResources"]["water"]["amount"] - working["basicResources"]["water"]["amount"] == 1500
    working["exploration"]["experience"] = 2800
    load_game(page, working)
    buy("Seed Processing", [("sugar", 6000, "sugar"), ("protein", 100, "protein"), ("knowledge", 700, "knowledge"), ("water", 150, "water")])
    nest()
    expect(expand).to_be_enabled()
    for value, label in ((8000, "sugar"), (125, "protein"), (800, "knowledge"), (1500, "tunneling space"), (350, "water")):
        expect(action("Expand Dry Clearing Outpost").locator(".action-costs")).to_contain_text(f"/{value} {label}")
    assert_fits(page, width)
    expand.click()
    final = save_game(page)
    assert final["nestAttributes"]["dryClearingExtensions"] == 2
    assert final["nestAttributes"]["maxAnts"] == 150 * UNIT
    nest()
    expect(expand).to_have_count(0)
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("Level 3")
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("15 Seed Forager slots")
    expect(resource("Protein")).to_contain_text("+0.90/s")
    expect(resource("Water")).to_contain_text("-0.225/s")
    page.clock.run_for(1000)
    active = save_game(page)
    assert active["basicResources"]["protein"]["amount"] - final["basicResources"]["protein"]["amount"] == 9000
    assert final["basicResources"]["water"]["amount"] - active["basicResources"]["water"]["amount"] == 2250
    page.reload(wait_until="networkidle")
    restored = save_game(page)
    assert restored["nestAttributes"] == active["nestAttributes"]
    assert restored["ants"]["ages"] == active["ants"]["ages"]
    page.get_by_role("tab", name="Tasks", exact=False).click()
    row = page.locator("#task-seedforager")
    expect(row).to_contain_text("15 / 15")
    if width <= 767:
        expect(row.locator(".task-description")).to_contain_text("0.06 protein/s · −0.015 water/s")
    else:
        row.hover()
        expect(page.locator(".game-tooltip")).to_contain_text("0.06 protein/s · −0.015 water/s")
    assert_fits(page, width)
    dry = copy.deepcopy(active)
    dry["basicResources"]["water"]["amount"] = 0
    load_game(page, dry)
    page.clock.run_for(1000)
    paused = save_game(page)
    assert paused["basicResources"]["protein"]["amount"] == dry["basicResources"]["protein"]["amount"]
    assert paused["nestAttributes"]["maxAnts"] == 150 * UNIT
    nest()
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("Needs water")
    assert_fits(page, width)
    old = copy.deepcopy(game)
    old["nestAttributes"].pop("dryClearingExtensions", None)
    old["upgrades"]["upgrades"] = [u for u in old["upgrades"]["upgrades"] if u["name"] not in ("CoveredTrails", "SeedProcessing")]
    load_game(page, old)
    nest()
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("Level 1")
    assert save_game(page)["nestAttributes"]["maxAnts"] == 100 * UNIT
    assert not errors, errors
    print(f"PASS {width}px: hidden progression, research/expansion costs, storage shortages, new staffing, upgraded live rates/ticks, level cap, old/current saves and dry pause", flush=True)


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
                    check_progression(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
