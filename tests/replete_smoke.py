"""Living water storage, worker promotion, replete lifespans and safe capacity loss."""

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


def check_repletes(page, width, url):
    now = datetime(2026, 11, 5, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["world"]["currentTick"] = game["world"]["targetTick"] = 100
    game["ants"]["workers"] = 10 * UNIT
    game["ants"]["scouts"] = 0
    game["ants"]["repletes"] = 0
    game["ants"]["eggsAndLarvae"] = []
    game["ants"]["tasks"] = [[{"SugarCollector": {}}, 10 * UNIT], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]]
    game["ants"]["defaultTask"] = {"SugarCollector": {}}
    game["ants"]["ages"] = {"active": True, "workers": [{"expiresAt": 300, "count": 1}, {"expiresAt": 400, "count": 1}, {"expiresAt": 18000, "count": 8}]}
    game["basicResources"]["sugar"] = {"amount": 6000 * UNIT, "maxAmount": 10000 * UNIT}
    game["basicResources"]["protein"] = {"amount": 100 * UNIT, "maxAmount": 100 * UNIT}
    game["basicResources"]["knowledge"] = {"amount": 1000 * UNIT, "maxAmount": 9223372036854775807}
    game["basicResources"]["tunnelingSpace"] = {"amount": 1000 * UNIT, "maxAmount": 2000 * UNIT}
    game["basicResources"]["water"] = {"amount": 80 * UNIT, "maxAmount": 100 * UNIT}
    game["nestAttributes"]["maxAnts"] = 10 * UNIT
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["exploration"] = {"experience": 1500, "surveys": {"dry-clearing": 2}}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = upgrade["name"] == "DefaultWorkers"

    def nest_tab():
        page.get_by_role("tab", name="Nest", exact=True).click()

    def resource(name):
        return page.locator(".resource-row").filter(has=page.get_by_text(name, exact=True))

    load_game(page, game)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Replete Storage", exact=True)).to_have_count(0)
    game["exploration"]["surveys"]["dry-clearing"] = 3
    load_game(page, game)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    action = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Replete Storage", exact=True))
    for amount, label in ((1500, "sugar"), (20, "protein"), (150, "knowledge")):
        expect(action.locator(".action-costs")).to_contain_text(f"/{amount} {label}")
    action.get_by_role("button").click()
    nest_tab()
    promote = page.get_by_role("button", name="Promote Replete", exact=True)
    expect(promote).to_be_disabled()
    chamber = page.get_by_role("button", name="Replete Chamber", exact=True)
    chamber.click()
    page.clock.run_for(200)
    before = save_game(page)
    assert before["nestAttributes"]["chambers"]["repleteChamber"]["level"] == 1
    nest_tab()
    expect(promote).to_be_enabled()
    promotion = page.locator(".action-cell").filter(has=promote)
    expect(promotion.locator(".action-costs")).to_contain_text("/100 sugar")
    expect(promotion.locator(".action-costs")).to_contain_text("/5 protein")
    promote.click()
    first = save_game(page)
    assert first["ants"]["workers"] == 9 * UNIT
    assert first["ants"]["repletes"] == UNIT
    assert first["ants"]["tasks"][0][1] == 9 * UNIT
    assert first["basicResources"]["sugar"]["amount"] == before["basicResources"]["sugar"]["amount"] - 100 * UNIT
    assert first["basicResources"]["protein"]["amount"] == before["basicResources"]["protein"]["amount"] - 5 * UNIT
    assert first["basicResources"]["water"] == {"amount": 80 * UNIT, "maxAmount": 200 * UNIT}
    assert not any(c["expiresAt"] == 300 for c in first["ants"]["ages"]["workers"])
    expiry = first["ants"]["ages"]["repletes"][0]["expiresAt"]
    assert first["world"]["currentTick"] + 90 * 300 <= expiry <= first["world"]["currentTick"] + 120 * 300
    expect(resource("Ants")).to_contain_text("10")
    expect(resource("Repletes")).to_be_visible()
    nest_tab()
    expect(promote).to_be_disabled()
    page.get_by_role("button", name="Replete Chamber 1", exact=True).click()
    page.clock.run_for(200)
    nest_tab()
    promote.click()
    second = save_game(page)
    assert second["ants"]["workers"] == 8 * UNIT
    assert second["ants"]["repletes"] == 2 * UNIT
    assert second["basicResources"]["water"]["maxAmount"] == 300 * UNIT
    assert second["statistics"]["repletesPromoted"]["thisRun"] == 2
    page.reload(wait_until="networkidle")
    assert save_game(page)["ants"]["ages"]["repletes"] == second["ants"]["ages"]["repletes"]

    # Losing a living store reduces capacity immediately, without destroying the inventory.
    dying = copy.deepcopy(second)
    dying["world"]["currentTick"] = dying["world"]["targetTick"] = 299
    dying["ants"]["ages"] = {"active": True, "workers": [{"expiresAt": 18000, "count": 8}],
        "repletes": [{"expiresAt": 300, "count": 1}, {"expiresAt": 18000, "count": 1}]}
    dying["basicResources"]["water"] = {"amount": 280 * UNIT, "maxAmount": 300 * UNIT}
    load_game(page, dying)
    page.clock.run_for(200)
    lost = save_game(page)
    assert lost["ants"]["repletes"] == UNIT
    assert lost["basicResources"]["water"] == {"amount": 280 * UNIT, "maxAmount": 200 * UNIT}
    assert lost["statistics"]["repletesLostToAging"]["thisRun"] == 1
    expect(resource("Water")).to_contain_text("Collection paused")
    page.clock.run_for(1000)
    assert save_game(page)["basicResources"]["water"] == lost["basicResources"]["water"]
    page.reload(wait_until="networkidle")
    assert save_game(page)["basicResources"]["water"] == lost["basicResources"]["water"]
    nest_tab()
    promote.click()
    restored = save_game(page)
    assert restored["basicResources"]["water"] == {"amount": 280 * UNIT, "maxAmount": 300 * UNIT}
    assert restored["ants"]["repletes"] == 2 * UNIT
    nest_tab()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: research/housing gates, default-worker promotion, fresh lifespan, capacity/upkeep, saved repletes, aging loss without lost water, restored capacity, layout")


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
                    check_repletes(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
