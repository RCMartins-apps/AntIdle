"""Protein Storage research and retroactive chamber capacity."""

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


def check_storage(page, width, url):
    now = datetime(2026, 11, 4, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 0
    game["ants"]["scouts"] = 0
    game["ants"]["eggsAndLarvae"] = []
    game["basicResources"]["sugar"] = {"amount": 5000 * UNIT, "maxAmount": 10000 * UNIT}
    game["basicResources"]["protein"] = {"amount": 80 * UNIT, "maxAmount": 100 * UNIT}
    game["basicResources"]["knowledge"] = {"amount": 1000 * UNIT, "maxAmount": 9223372036854775807}
    game["exploration"] = {"experience": 300}
    game["nestAttributes"]["chambers"]["foodStorageChamber"]["level"] = 3
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = upgrade["name"] == "UnlockFoodStorageChamber"

    # Research remains undiscovered before protein becomes available.
    early = copy.deepcopy(game)
    early["basicResources"]["protein"]["amount"] = 0
    early["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 0
    load_game(page, early)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Protein Storage", exact=True)).to_have_count(0)

    load_game(page, game)
    before = save_game(page)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    research = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Protein Storage", exact=True))
    for amount, resource in ((1000, "sugar"), (25, "protein"), (75, "knowledge")):
        expect(research.locator(".action-costs")).to_contain_text(f"/{amount} {resource}")
    research.get_by_role("button").click()
    purchased = save_game(page)
    for amount, resource in ((1000, "sugar"), (25, "protein"), (75, "knowledge")):
        assert purchased["basicResources"][resource]["amount"] == before["basicResources"][resource]["amount"] - amount * UNIT
    assert purchased["basicResources"]["protein"]["maxAmount"] == 400 * UNIT
    protein = page.locator(".resource-row").filter(has=page.get_by_text("Protein", exact=True))
    expect(protein).to_contain_text("/400")
    page.get_by_role("tab", name="Nest", exact=True).click()
    chamber = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Food Storage Chamber 3", exact=True))
    if width <= 767:
        chamber.get_by_text("Details: Food Storage Chamber", exact=True).click()
        expect(chamber.locator("details[open]")).to_contain_text("+100 max protein")
        expect(chamber.locator("details[open]")).to_contain_text("+1000 max sugar")
    else:
        chamber.get_by_role("button").hover()
        expect(page.locator(".game-tooltip")).to_contain_text("+100 max protein")
    chamber.get_by_role("button").click()
    page.clock.run_for(200)
    built = save_game(page)
    assert built["nestAttributes"]["chambers"]["foodStorageChamber"]["level"] == 4
    assert built["basicResources"]["protein"]["maxAmount"] == 500 * UNIT
    assert built["basicResources"]["protein"]["amount"] == 55 * UNIT
    assert built["basicResources"]["sugar"]["maxAmount"] == 11000 * UNIT
    page.reload(wait_until="networkidle")
    page.clock.run_for(1000)
    restored = save_game(page)
    assert restored["basicResources"]["protein"] == built["basicResources"]["protein"]
    expect(protein).to_contain_text("/500")
    page.get_by_role("tab", name="Nest", exact=True).click()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: hidden research, exact costs, retroactive capacity, chamber bonus details, future levels, save reload, layout")


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
                    check_storage(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
