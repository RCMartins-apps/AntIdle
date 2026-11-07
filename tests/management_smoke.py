"""Construction planning, visible queue costs, and longer-lived new adults."""

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


def check_management(page, width, url):
    now = datetime(2026, 11, 5, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 10 * UNIT
    game["ants"]["scouts"] = 0
    game["ants"]["eggsAndLarvae"] = []
    game["ants"]["tasks"] = [[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, 0], [{"Explorer": {}}, 0]]
    game["basicResources"]["sugar"] = {"amount": 6000 * UNIT, "maxAmount": 10000 * UNIT}
    game["basicResources"]["tunnelingSpace"] = {"amount": 1000 * UNIT, "maxAmount": 2000 * UNIT}
    game["basicResources"]["protein"] = {"amount": 0, "maxAmount": 100 * UNIT}
    game["basicResources"]["knowledge"] = {"amount": 1000 * UNIT, "maxAmount": 9223372036854775807}
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 0
    game["exploration"] = {"experience": 99, "proteinDiscovered": True}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = False
    load_game(page, game)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Construction Planning", exact=True)).to_have_count(0)
    expect(page.get_by_role("button", name="Colony Nutrition", exact=True)).to_have_count(0)

    game["exploration"]["experience"] = 100
    load_game(page, game)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    planning = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Construction Planning", exact=True))
    expect(planning.locator(".action-costs")).to_contain_text("/500 sugar")
    expect(planning.locator(".action-costs")).to_contain_text("/50 knowledge")
    planning.get_by_role("button").click()
    planned = save_game(page)
    assert planned["nestAttributes"]["maxBuildQueue"] == 3
    assert planned["basicResources"]["sugar"]["amount"] == 5500 * UNIT
    assert planned["basicResources"]["knowledge"]["amount"] == 950 * UNIT
    page.get_by_role("tab", name="Nest", exact=True).click()
    nursery = page.get_by_role("button", name="Nursery Chamber", exact=True)
    for _ in range(3):
        nursery.click()
    expect(nursery).to_be_disabled()
    entries = page.locator(".queued-building")
    expect(entries).to_have_count(3)
    for index, cost in enumerate((3, 6, 9)):
        expect(entries.nth(index).locator(".action-costs")).to_contain_text(f"/{cost} protein")
        expect(entries.nth(index)).to_contain_text(f"→ {index + 1}")
    expect(entries.first.locator(".build-task .text-danger")).to_have_text("Never")
    expect(entries.nth(1).locator(".build-task")).to_contain_text("Queued")
    entries.first.locator(".build-task").press("Enter")
    expect(entries).to_have_count(2)
    expect(entries.first.locator(".action-costs")).to_contain_text("/3 protein")
    expect(entries.nth(1).locator(".action-costs")).to_contain_text("/6 protein")
    nursery.click()
    assert_fits(page, width)
    queued = save_game(page)
    queued["basicResources"]["protein"]["amount"] = 20 * UNIT
    load_game(page, queued)
    page.clock.run_for(600)
    built = save_game(page)
    assert not built["nestAttributes"]["buildQueue"]
    assert built["nestAttributes"]["chambers"]["nurseryChamber"]["level"] == 3
    assert built["basicResources"]["protein"]["amount"] == 2 * UNIT
    assert built["basicResources"]["tunnelingSpace"]["amount"] == 525 * UNIT

    # Nutrition is a later goal, and research never changes an existing expiry.
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Colony Nutrition", exact=True)).to_have_count(0)
    built["exploration"]["experience"] = 1000
    built["basicResources"]["protein"]["amount"] = 100 * UNIT
    load_game(page, built)
    before = save_game(page)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    nutrition = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Colony Nutrition", exact=True))
    for amount, label in ((2500, "sugar"), (60, "protein"), (200, "knowledge")):
        expect(nutrition.locator(".action-costs")).to_contain_text(f"/{amount} {label}")
    nutrition.get_by_role("button").click()
    researched = save_game(page)
    assert researched["ants"]["ages"] == before["ants"]["ages"]
    for amount, label in ((2500, "sugar"), (60, "protein"), (200, "knowledge")):
        assert researched["basicResources"][label]["amount"] == before["basicResources"][label]["amount"] - amount * UNIT
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_label("About Brood", exact=True).click()
    page.locator(".colony-aging > summary").click()
    expect(page.locator(".colony-aging")).to_contain_text("54–72 minutes")
    researched["ants"]["eggsAndLarvae"] = [{"Egg": {"initialTick": researched["world"]["currentTick"]}}]
    load_game(page, researched)
    page.clock.run_for(10000)
    grown = save_game(page)
    assert grown["ants"]["workers"] == 11 * UNIT
    previous = {c["expiresAt"]: c["count"] for c in researched["ants"]["ages"]["workers"]}
    added = [c["expiresAt"] for c in grown["ants"]["ages"]["workers"] if c["count"] > previous.get(c["expiresAt"], 0)]
    assert len(added) == 1
    assert grown["world"]["currentTick"] + 54 * 300 <= added[0] <= grown["world"]["currentTick"] + 72 * 300
    page.reload(wait_until="networkidle")
    restored = save_game(page)
    assert restored["ants"]["ages"] == grown["ants"]["ages"]
    assert restored["nestAttributes"]["maxBuildQueue"] == 3
    page.get_by_role("tab", name="Nest", exact=True).click()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: hidden upgrades, exact costs, planned levels, protein wait, cancellation, completion, longer new lifespans, persistence, layout")


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
                    check_management(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
