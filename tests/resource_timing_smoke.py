"""Visible resource full/empty estimates and compact mobile placement."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import re
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game


def check_resource_timing(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["world"].update(currentTick=100, targetTick=100, saveSeed=42)
    game["ants"].update(workers=13 * UNIT, smallWorkers=0, scouts=0, repletes=0, daughterQueens=0,
        defaultTask=None, eggsAndLarvae=[], eggOrders=0, sugarCumulativeDebt=0,
        tasks=[[{"SugarCollector": {}}, 10 * UNIT], [{"Tunneler": {}}, UNIT], [{"Scavenger": {}}, 2 * UNIT]])
    game["basicResources"].update(sugar={"amount": 100 * UNIT, "maxAmount": 1000 * UNIT},
        tunnelingSpace={"amount": 50 * UNIT, "maxAmount": 1000 * UNIT},
        protein={"amount": 5 * UNIT, "maxAmount": 100 * UNIT},
        water={"amount": 100 * UNIT, "maxAmount": 100 * UNIT},
        resin={"amount": 200 * UNIT, "maxAmount": 200 * UNIT},
        knowledge={"amount": 100 * UNIT, "maxAmount": 9223372036854775807})
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=False)

    def row(name):
        return page.locator(".resource-row").filter(has=page.get_by_text(name, exact=True))

    load_game(page, game)
    for name in ("Sugar", "Tunneling Space", "Protein"):
        expect(row(name).locator(".resource-timing")).to_contain_text("Full in ~")
    expect(row("Water").locator(".resource-timing")).to_have_text("Full")
    expect(row("Resin").locator(".resource-timing")).to_have_text("Full")
    expect(row("Knowledge").locator(".resource-timing")).to_have_count(0)
    expect(row("Workers").locator(".resource-timing")).to_have_count(0)
    if width <= 767:
        for name in ("Sugar", "Protein", "Water"):
            resource = row(name)
            label = resource.locator(".resource-label").bounding_box()
            timing = resource.locator(".resource-timing").bounding_box()
            amount = resource.locator(".resource-amount").bounding_box()
            rate = resource.locator(":scope > div").last.bounding_box()
            assert timing["y"] >= label["y"] + label["height"] - 1
            assert timing["x"] < amount["x"]
            assert timing["x"] + timing["width"] <= amount["x"] + 1
            assert rate["x"] >= amount["x"] - 1
            assert rate["y"] >= amount["y"] + amount["height"] - 1
    assert_fits(page, width)

    draining = copy.deepcopy(game)
    draining["ants"].update(workers=10 * UNIT, tasks=[[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, 10 * UNIT]])
    draining["basicResources"]["sugar"]["amount"] = 50 * UNIT
    load_game(page, draining)
    expect(row("Sugar").locator(".resource-timing")).to_have_text("Empty in ~25.0s")
    expect(row("Sugar").locator(".resource-timing .text-danger")).to_have_count(1)
    page.clock.run_for(5000)
    expect(row("Sugar").locator(".resource-timing")).to_have_text("Empty in ~20.0s")
    page.screenshot(path=f"/tmp/antidle-resource-timing-{width}.png", full_page=True)
    assert_fits(page, width)

    feeding = copy.deepcopy(game)
    feeding["ants"].update(workers=UNIT, tasks=[[{"SugarCollector": {}}, 0], [{"AphidTender": {}}, UNIT]])
    feeding["nestAttributes"]["chambers"]["aphidChamber"]["level"] = 1
    for upgrade in feeding["upgrades"]["upgrades"]:
        upgrade["unlocked"] = upgrade["name"] == "AphidTending"
    load_game(page, feeding)
    expect(row("Protein").locator(".resource-timing")).to_have_text("Empty in ~8m 20s")
    assert_fits(page, width)

    full = copy.deepcopy(game)
    full["basicResources"]["sugar"]["amount"] = 1000 * UNIT
    load_game(page, full)
    expect(row("Sugar").locator(".resource-timing")).to_have_text("Full")
    expect(row("Sugar").locator(".resource-amount")).to_have_class(re.compile("fw-bold"))
    expect(row("Sugar").locator(":scope > div").last).to_have_text("0/s")
    stationary = copy.deepcopy(game)
    stationary["ants"].update(workers=0, tasks=[])
    load_game(page, stationary)
    expect(row("Sugar").locator(".resource-timing")).to_be_empty()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: filling/draining/steady/full resources, live countdown, protein feeding, no unlimited/population timers and compact layout", flush=True)


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
                for width in (320, 390, 844, 1920):
                    context = browser.new_context(viewport={"width": width, "height": 844})
                    check_resource_timing(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
