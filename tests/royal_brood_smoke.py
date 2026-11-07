"""Royal nurse staffing, feeding, pause/save, shortages and daughter maturation."""

import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game


def check_royal_brood(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["world"]["currentTick"] = game["world"]["targetTick"] = 100
    game["ants"].update(workers=35 * UNIT, scouts=0, repletes=0, eggsAndLarvae=[],
        tasks=[[{"SugarCollector": {}}, 35 * UNIT], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]],
        defaultTask={"SugarCollector": {}}, royalBrood=None, daughterQueens=0,
        ages={"active": True, "workers": [{"expiresAt": 18000, "count": 35}]})
    game["nestAttributes"]["maxAnts"] = 100 * UNIT
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["nestAttributes"]["chambers"]["royalNurseryChamber"]["level"] = 1
    game["nestAttributes"]["chambers"]["foodStorageChamber"]["level"] = 4
    game["basicResources"].update(sugar={"amount": 20000 * UNIT, "maxAmount": 40000 * UNIT},
        protein={"amount": 500 * UNIT, "maxAmount": 500 * UNIT},
        water={"amount": 100 * UNIT, "maxAmount": 100 * UNIT})
    game["unlocks"]["resources"]["showDaughterQueens"] = False
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] in ("DefaultWorkers", "ProteinStorage", "RoyalNurseryPlanning"))

    def nest():
        page.get_by_role("tab", name="Nest", exact=True).click()

    def resource(name):
        return page.locator(".resource-row").filter(has=page.get_by_text(name, exact=True))

    raise_egg = page.get_by_role("button", name="Raise Royal Egg", exact=True)
    early = copy.deepcopy(game)
    early["nestAttributes"]["chambers"]["royalNurseryChamber"]["level"] = 0
    for upgrade in early["upgrades"]["upgrades"]:
        if upgrade["name"] == "RoyalNurseryPlanning":
            upgrade["unlocked"] = False
    load_game(page, early)
    nest()
    expect(raise_egg).to_have_count(0)
    page.locator("#tasks-tab").click()
    expect(page.get_by_role("button", name="Assign Royal Nurse", exact=True)).to_have_count(0)

    load_game(page, game)
    nest()
    expect(raise_egg).to_be_disabled()
    expect(resource("Daughter queens")).to_have_count(0)
    card = page.locator(".action-cell").filter(has=raise_egg)
    for amount, name in ((500, "sugar"), (50, "protein"), (20, "water")):
        expect(card.locator(".action-costs")).to_contain_text(f"/{amount} {name}")
    expect(card.locator('.action-cost[aria-label^="royal nurses"] .text-danger')).to_have_text("0")
    expect(page.locator(".royal-total-care")).to_contain_text("120 protein")
    page.locator("#tasks-tab").click()
    assign = page.get_by_role("button", name="Assign Royal Nurse", exact=True)
    for _ in range(5):
        assign.click()
    expect(assign).to_be_disabled()
    expect(page.locator("#task-royalnurse")).to_contain_text("5 / 5")
    assert_fits(page, width)
    nest()
    expect(raise_egg).to_be_enabled()
    raise_egg.click()
    started = save_game(page)
    assert started["ants"]["workers"] == 35 * UNIT
    assert started["ants"]["royalBrood"] == {"careUnits": 0, "paused": False}
    assert started["statistics"]["royalEggsRaised"]["thisRun"] == 1
    for name, amount in (("sugar", 500), ("protein", 50), ("water", 20)):
        assert game["basicResources"][name]["amount"] - started["basicResources"][name]["amount"] == amount * UNIT
    nest()
    expect(raise_egg).to_have_count(0)
    expect(page.locator(".royal-brood-card")).to_contain_text("Royal egg")
    expect(page.locator(".royal-feeding")).to_contain_text("0.05 water/s")
    expect(resource("Daughter queens").locator(".resource-icon-queens svg")).to_have_count(1)
    page.clock.run_for(1000)
    developed = save_game(page)
    assert developed["ants"]["royalBrood"]["careUnits"] == 25
    nest()
    page.get_by_role("button", name="Pause care", exact=True).click()
    toggle = page.get_by_role("button", name="Resume care", exact=True)
    toggle.evaluate("e => e.dataset.testIdentity = 'retained'")
    page.clock.run_for(1000)
    expect(toggle).to_have_attribute("data-test-identity", "retained")
    paused = save_game(page)
    assert paused["ants"]["royalBrood"] == {"careUnits": 25, "paused": True}
    assert paused["basicResources"]["water"] == developed["basicResources"]["water"]
    assert paused["basicResources"]["protein"] == developed["basicResources"]["protein"]
    page.reload(wait_until="networkidle")
    nest()
    expect(page.locator(".royal-care-status")).to_have_text("Care paused")
    page.get_by_role("button", name="Resume care", exact=True).click()
    page.clock.run_for(200)
    assert save_game(page)["ants"]["royalBrood"]["careUnits"] == 30

    larva = copy.deepcopy(started)
    larva["ants"]["royalBrood"]["careUnits"] = 1500
    load_game(page, larva)
    nest()
    expect(page.locator(".royal-brood-card")).to_contain_text("Royal larva")
    expect(page.locator(".royal-feeding")).to_contain_text("2.50 sugar/s · −0.25 protein/s · −0.05 water/s")
    expect(resource("Protein")).to_contain_text("-0.25/s")
    expect(resource("Water")).to_contain_text("-0.05/s")
    expect(resource("Sugar")).to_contain_text("+0.325/s")
    assert_fits(page, width)
    page.locator(".nest-royal-nursery").screenshot(path=f"/tmp/antidle-royal-larva-{width}.png")
    before_protein = larva["basicResources"]["protein"]["amount"]
    page.clock.run_for(1000)
    fed = save_game(page)
    assert fed["ants"]["royalBrood"]["careUnits"] == 1525
    assert before_protein - fed["basicResources"]["protein"]["amount"] == 2500
    dry = copy.deepcopy(fed)
    dry["basicResources"]["water"]["amount"] = 0
    dry["basicResources"]["protein"]["amount"] = 0
    load_game(page, dry)
    nest()
    expect(page.locator(".royal-care-status")).to_have_class("royal-care-status small text-danger")
    expect(page.locator(".royal-care-status")).to_have_text("Needs protein · water")
    page.clock.run_for(1000)
    assert save_game(page)["ants"]["royalBrood"]["careUnits"] == 1525

    pupa = copy.deepcopy(fed)
    pupa["ants"]["royalBrood"]["careUnits"] = 14995
    load_game(page, pupa)
    nest()
    expect(page.locator(".royal-brood-card")).to_contain_text("Royal pupa")
    expect(page.locator(".royal-feeding")).not_to_contain_text("protein/s")
    page.clock.run_for(200)
    expect(page.locator(".royal-queen-ready")).to_contain_text("Daughter queen mature")
    expect(raise_egg).to_have_count(0)
    expect(resource("Daughter queens").locator(".resource-amount")).to_have_text("1")
    expect(resource("Ants").locator(".resource-amount")).to_have_text("36")
    assert_fits(page, width)
    page.locator(".nest-royal-nursery").screenshot(path=f"/tmp/antidle-royal-ready-{width}.png")
    mature = save_game(page)
    assert mature["ants"]["daughterQueens"] == UNIT
    assert mature["ants"].get("royalBrood") is None
    assert mature["ants"]["workers"] == 35 * UNIT
    assert mature["statistics"]["daughterQueensMatured"]["thisRun"] == 1
    assert mature["messages"].count("A daughter queen has matured!") == 1
    page.reload(wait_until="networkidle")
    page.clock.run_for(400)
    restored = save_game(page)
    assert restored["statistics"]["daughterQueensMatured"]["thisRun"] == 1
    assert restored["messages"].count("A daughter queen has matured!") == 1
    empty = copy.deepcopy(mature)
    empty["ants"]["daughterQueens"] = 0
    load_game(page, empty)
    expect(resource("Daughter queens")).to_have_count(1)
    old = copy.deepcopy(game)
    old["ants"].pop("royalBrood", None)
    old["ants"].pop("daughterQueens", None)
    old["unlocks"]["resources"].pop("showDaughterQueens", None)
    old["statistics"].pop("royalEggsRaised", None)
    old["statistics"].pop("daughterQueensMatured", None)
    load_game(page, old)
    nest()
    expect(raise_egg).to_be_disabled()  # Nurse task is restored at zero staff.
    assert save_game(page)["ants"]["daughterQueens"] == 0
    assert not errors, errors
    print(f"PASS {width}px: hidden nursery/nurses, capped staffing, costs, stages, feeding, persistent pause, shortages, maturation once, housing, old saves and layout", flush=True)


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
                    check_royal_brood(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
