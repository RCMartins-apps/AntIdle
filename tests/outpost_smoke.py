"""Dry Clearing establishment, water-funded protein, staffing limits and save recovery."""

import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game


def check_outpost(page, width, url):
    now = datetime(2026, 11, 6, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["world"]["currentTick"] = game["world"]["targetTick"] = 100
    game["ants"].update(workers=20 * UNIT, scouts=2 * UNIT, repletes=UNIT, eggsAndLarvae=[],
        tasks=[[{"SugarCollector": {}}, 20 * UNIT], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]],
        defaultTask={"SugarCollector": {}},
        ages={"active": True, "workers": [{"expiresAt": 18000, "count": 20}],
            "scouts": [{"expiresAt": 18000, "count": 2}], "repletes": [{"expiresAt": 36000, "count": 1}]})
    game["basicResources"].update(sugar={"amount": 6000 * UNIT, "maxAmount": 10000 * UNIT},
        protein={"amount": 80 * UNIT, "maxAmount": 100 * UNIT},
        water={"amount": 180 * UNIT, "maxAmount": 200 * UNIT},
        tunnelingSpace={"amount": 1000 * UNIT, "maxAmount": 1500 * UNIT},
        knowledge={"amount": 500 * UNIT, "maxAmount": 9223372036854775807})
    game["nestAttributes"]["maxAnts"] = 40 * UNIT
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["nestAttributes"]["chambers"]["repleteChamber"]["level"] = 1
    game["exploration"] = {"experience": 1500, "surveys": {"dry-clearing": 2}}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = upgrade["name"] in ("DefaultWorkers", "RepleteStorage")

    def nest():
        page.get_by_role("tab", name="Nest", exact=True).click()

    def tasks():
        page.get_by_role("tab", name="Tasks", exact=False).click()

    def resource(name):
        return page.locator(".resource-row").filter(has=page.get_by_text(name, exact=True))

    build = page.get_by_role("button", name="Establish Dry Clearing Outpost", exact=True)
    load_game(page, game)
    expect(build).to_have_count(0)
    tasks()
    expect(page.locator("#task-seedforager")).to_have_count(0)
    game["exploration"]["surveys"]["dry-clearing"] = 3
    early = copy.deepcopy(game)
    early["exploration"]["experience"] = 1499
    load_game(page, early)
    expect(build).to_have_count(0)
    poor = copy.deepcopy(game)
    poor["ants"]["repletes"] = 0
    poor["ants"]["ages"]["repletes"] = []
    poor["basicResources"]["water"] = {"amount": 100 * UNIT, "maxAmount": 100 * UNIT}
    load_game(page, poor)
    nest()
    expect(build).to_be_disabled()
    action = page.locator(".action-cell").filter(has=build)
    expect(action.locator(".action-cost").filter(has_text="water").locator(".text-danger")).to_have_count(2)
    load_game(page, game)
    nest()
    expect(build).to_be_enabled()
    for amount, label in ((3000, "sugar"), (40, "protein"), (300, "knowledge"), (750, "tunneling space"), (150, "water")):
        expect(action.locator(".action-costs")).to_contain_text(f"/{amount} {label}")
    assert_fits(page, width)
    build.click()
    built = save_game(page)
    assert built["nestAttributes"]["dryClearingOutpost"] is True
    assert built["nestAttributes"]["maxAnts"] == 90 * UNIT
    for label, cost in (("sugar", 3000), ("protein", 40), ("knowledge", 300), ("tunnelingSpace", 750), ("water", 150)):
        assert built["basicResources"][label]["amount"] == game["basicResources"][label]["amount"] - cost * UNIT
    assert built["ants"]["workers"] == game["ants"]["workers"]
    nest()
    expect(build).to_have_count(0)
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("5 Seed Forager slots")
    tasks()
    assign = page.get_by_role("button", name="Assign Seed Forager", exact=True)
    for _ in range(5):
        assign.click()
    expect(assign).to_be_disabled()
    expect(page.locator("#task-seedforager")).to_contain_text("5 / 5")
    before = save_game(page)
    page.clock.run_for(1000)
    working = save_game(page)
    assert working["basicResources"]["protein"]["amount"] - before["basicResources"]["protein"]["amount"] == 2000
    assert before["basicResources"]["water"]["amount"] - working["basicResources"]["water"]["amount"] == 1000
    expect(resource("Protein")).to_contain_text("+0.20/s")
    expect(resource("Water")).to_contain_text("-0.10/s")
    dry = copy.deepcopy(working)
    dry["basicResources"]["water"]["amount"] = 0
    load_game(page, dry)
    nest()
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("Needs water")
    page.clock.run_for(1000)
    paused = save_game(page)
    assert paused["basicResources"]["protein"]["amount"] == dry["basicResources"]["protein"]["amount"]
    assert paused["nestAttributes"]["maxAnts"] == 90 * UNIT
    assert paused["ants"]["workers"] == 20 * UNIT
    expect(resource("Water")).to_contain_text("0/s")
    full = copy.deepcopy(working)
    full["basicResources"]["protein"]["amount"] = 100 * UNIT
    load_game(page, full)
    nest()
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("Protein full")
    page.clock.run_for(1000)
    assert save_game(page)["basicResources"]["water"]["amount"] == full["basicResources"]["water"]["amount"]
    load_game(page, working)
    page.reload(wait_until="networkidle")
    tasks()
    expect(page.locator("#task-seedforager")).to_contain_text("5 / 5")
    page.locator("#default-worker-task").select_option("SeedForager")
    pooled = save_game(page)
    assert sum(count for task, count in pooled["ants"]["tasks"] if task == {"SeedForager": {}}) == 5 * UNIT
    assert pooled["nestAttributes"]["maxAnts"] == 90 * UNIT
    tasks()
    assert_fits(page, width)
    nest()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: hidden gates, visible costs/storage shortage, one-time establishment, capped staffing, water/protein ticks, dry/full pauses, save/default recovery, layout", flush=True)


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
                    check_outpost(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
