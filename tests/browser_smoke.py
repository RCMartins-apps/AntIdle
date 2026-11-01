"""Responsive UI regression checks. See README.md for browser setup."""

import base64
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright

ROOT = Path(__file__).resolve().parents[1]
UNIT = 10000  # Constants.u: game resources use fixed-point integers.


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


def save_game(page):
    page.get_by_role("tab", name="Settings", exact=True).click()
    page.get_by_role("button", name="Manual Save", exact=True).click()
    return json.loads(base64.b64decode(page.evaluate("localStorage.getItem('save')")))


def load_game(page, game):
    page.evaluate("save => localStorage.setItem('save', save)", json.dumps(game))
    page.reload(wait_until="networkidle")
    expect(page.get_by_role("button", name="Gather Sugar", exact=True)).to_be_visible()


def assert_fits(page, width):
    assert page.evaluate("document.documentElement.scrollWidth") <= width
    for button in page.get_by_role("button").all():
        if button.is_visible():
            box = button.bounding_box()
            assert box["x"] >= 0 and box["x"] + box["width"] <= width + 1
            if width <= 767:
                assert box["height"] >= 44, button.inner_text()


def progressed_game(initial):
    game = copy.deepcopy(initial)
    for group in ("actions", "tabs", "resources"):
        for key in game["unlocks"][group]:
            game["unlocks"][group][key] = True
    for resource in game["basicResources"].values():
        resource["maxAmount"] = 100000 * UNIT
        resource["amount"] = resource["maxAmount"]
    game["ants"]["workers"] = 300 * UNIT
    game["ants"]["tasks"] = [
        [{"SugarCollector": {}}, 0],
        [{"Tunneler": {}}, 0],
        [{"Explorer": {}}, 0],
    ]
    game["nestAttributes"]["maxWorkers"] = 1000 * UNIT
    game["nestAttributes"]["maxEggs"] = 500 * UNIT
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = True
    return game


def check_nest_capacity(page, initial):
    game = progressed_game(initial)
    game["ants"]["workers"] = 0
    game["nestAttributes"]["chambers"]["nestChamber"]["level"] = 3
    # A version-1 save receives its missing bonus once, during loading.
    game["version"] = 1
    game["basicResources"]["colonyPoints"] = {"amount": 2000 * UNIT, "maxAmount": 2000 * UNIT}
    load_game(page, game)
    # No tick is needed for migration.
    migrated = save_game(page)
    assert migrated["version"] == 2
    assert migrated["basicResources"]["colonyPoints"] == {
        "amount": 2000 * UNIT, "maxAmount": 3500 * UNIT
    }
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_text("Details: Expand Nest", exact=True).click()
    expect(page.locator("details[open]")).to_contain_text("+500 max colony points")
    page.get_by_role("button", name="Expand Nest", exact=False).click()
    page.clock.run_for(400)
    upgraded = save_game(page)
    assert upgraded["nestAttributes"]["chambers"]["nestChamber"]["level"] == 4
    assert upgraded["basicResources"]["colonyPoints"]["maxAmount"] == 4000 * UNIT
    page.clock.run_for(1000)
    assert save_game(page)["basicResources"]["colonyPoints"]["maxAmount"] == 4000 * UNIT
    page.reload(wait_until="networkidle")
    assert save_game(page)["basicResources"]["colonyPoints"]["maxAmount"] == 4000 * UNIT
    game["nestAttributes"]["chambers"]["nestChamber"]["level"] = 0
    load_game(page, game)
    page.clock.run_for(400)
    assert save_game(page)["basicResources"]["colonyPoints"]["maxAmount"] == 2000 * UNIT
    # Current-version saves keep their stored capacity, including other bonuses.
    game["version"] = 2
    game["nestAttributes"]["chambers"]["nestChamber"]["level"] = 3
    game["basicResources"]["colonyPoints"]["maxAmount"] = 7250 * UNIT
    load_game(page, game)
    page.clock.run_for(400)
    assert save_game(page)["basicResources"]["colonyPoints"]["maxAmount"] == 7250 * UNIT
    print("PASS nest capacity: v1 migration, v2 preservation, upgrades, stable ticks, save reload")


def check_chamber_bonuses(page, initial):
    def capacities(game):
        return {
            "workers": game["nestAttributes"]["maxWorkers"],
            "eggs": game["nestAttributes"]["maxEggs"],
            "sugar": game["basicResources"]["sugar"]["maxAmount"],
            "colony": game["basicResources"]["colonyPoints"]["maxAmount"],
        }

    for name, chamber, increases, details in (
        ("Expand Nest", "nestChamber", {"workers": 10, "colony": 500},
         ("+10 max workers", "+500 max colony points", "+0.01 colony points/worker/s")),
        ("Queen's Chamber", "queenChamber", {"workers": 5, "eggs": 2},
         ("+5 max workers", "+2 max eggs")),
        ("Food Storage Chamber", "foodStorageChamber", {"sugar": 1000},
         ("+1000 max sugar",)),
    ):
        game = progressed_game(initial)
        game["ants"]["workers"] = 0
        game["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
        load_game(page, game)
        page.get_by_text(f"Details: {name}", exact=True).click()
        for text in details:
            expect(page.locator("details[open]")).to_contain_text(text)
        page.get_by_role("button", name=name, exact=False).click()
        page.clock.run_for(200)
        result = save_game(page)
        assert result["nestAttributes"]["chambers"][chamber]["level"] == (
            game["nestAttributes"]["chambers"][chamber]["level"] + 1
        )
        assert capacities(result) == {
            key: value + increases.get(key, 0) * UNIT
            for key, value in capacities(game).items()
        }, name
        assert result["nestAttributes"]["buildQueue"] == []
        page.clock.run_for(400)
        assert capacities(save_game(page)) == capacities(result)
        page.reload(wait_until="networkidle")
        assert capacities(save_game(page)) == capacities(result)

    game = progressed_game(initial)
    game["ants"]["workers"] = 10 * UNIT
    game["nestAttributes"]["chambers"]["nestChamber"]["level"] = 3
    game["basicResources"]["colonyPoints"]["amount"] = 0
    game["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
    load_game(page, game)
    page.clock.run_for(200)
    result = save_game(page)
    assert result["basicResources"]["colonyPoints"]["amount"] == 600
    print("PASS shared chamber bonuses: displayed and applied caps, Queen worker bonus, income, save reload")


def run_checks(browser, url):
    outcomes = {}
    for width in (320, 390, 767, 768, 844, 1024, 1920):
        context = browser.new_context(
            viewport={"width": width, "height": 844},
            is_mobile=width <= 767,
            has_touch=width <= 767,
        )
        page = context.new_page()
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        # Freeze simulation time so desktop/mobile action results can be compared exactly.
        now = datetime(2026, 10, 31, tzinfo=timezone.utc)
        page.clock.install(time=now)
        page.clock.pause_at(now + timedelta(milliseconds=100))
        page.goto(url, wait_until="networkidle")
        # Ensure Bootstrap actually loaded; otherwise layout assertions are meaningless.
        expect(page.get_by_role("button", name="Gather Sugar", exact=True)).to_have_css(
            "background-color", "rgb(13, 110, 253)"
        )
        page.get_by_role("button", name="Gather Sugar", exact=True).click()
        if width <= 767:
            page.get_by_text("Details: Gather Sugar", exact=True).click()
            expect(page.locator("details[open]")).to_contain_text("+1 sugar")
        initial = save_game(page)
        assert initial["basicResources"]["sugar"]["amount"] == UNIT
        page.reload(wait_until="networkidle")
        assert save_game(page)["basicResources"] == initial["basicResources"]
        assert_fits(page, width)

        game = progressed_game(initial)
        load_game(page, game)
        for tab in ("Nest", "Tasks", "Upgrades", "Explore", "Settings"):
            page.get_by_role("tab", name=tab, exact=tab != "Tasks").click()
            assert_fits(page, width)

        page.get_by_role("tab", name="Nest", exact=True).click()
        page.get_by_role("button", name="Expand Nest", exact=True).click()
        queued = page.locator(".build-task")
        expect(queued).to_have_count(1)
        assert_fits(page, width)
        queued.click()
        expect(queued).to_have_count(0)
        page.get_by_role("button", name="Expand Nest", exact=True).click()
        queued.press("Enter")
        expect(queued).to_have_count(0)

        for multiplier, modifiers in ((1, []), (10, ["Control"]), (25, ["Shift"]), (250, ["Control", "Shift"])):
            load_game(page, game)
            if width <= 767:
                page.get_by_label("Action amount").select_option(str(multiplier))
                modifiers = []
                page.get_by_text("Details: Lay Egg", exact=True).click()
                expect(page.locator("details[open]")).to_contain_text(f"+{multiplier} eggs")
            page.get_by_role("button", name="Lay Egg", exact=False).click(modifiers=modifiers)
            page.get_by_role("tab", name="Tasks").click()
            page.get_by_role("button", name="Assign Sugar Collector", exact=True).click(modifiers=modifiers)
            page.get_by_role("button", name="Remove Sugar Collector", exact=True).click(modifiers=modifiers)
            page.get_by_role("button", name="Assign Tunneler", exact=True).click(modifiers=modifiers)
            result = save_game(page)
            actual = (result["ants"], result["basicResources"])
            if multiplier in outcomes:
                assert actual == outcomes[multiplier], (width, multiplier)
            else:
                outcomes[multiplier] = actual
            assert len(result["ants"]["eggsAndLarvae"]) == multiplier
            assert result["ants"]["tasks"][1][1] == multiplier * UNIT

        # Crossing the breakpoint must restore desktop shortcuts and mobile details.
        page.set_viewport_size({"width": 1920, "height": 844})
        page.get_by_role("tab", name="Nest", exact=True).click()
        page.get_by_role("button", name="Lay Egg", exact=False).hover()
        expect(page.locator(".game-tooltip")).to_be_visible()
        expect(page.locator(".game-tooltip")).to_contain_text("Ants require a constant sugar supply")
        page.set_viewport_size({"width": 390, "height": 844})
        page.get_by_text("Details: Lay Egg", exact=True).click()
        expect(page.locator("details[open]")).to_contain_text("Ants require a constant sugar supply")
        assert_fits(page, 390)
        if width == 390:
            check_nest_capacity(page, initial)
            check_chamber_bonuses(page, initial)
        assert not errors, errors
        print(f"PASS {width}px: layout, tabs, saves, batch parity, resize")
        context.close()


if __name__ == "__main__":
    assert (ROOT / "target/scala-2.13/antidle-fastopt/main.js").exists(), "Run sbt fastOptJS first"
    server = ThreadingHTTPServer(("127.0.0.1", 0), partial(QuietHandler, directory=str(ROOT)))
    Thread(target=server.serve_forever, daemon=True).start()
    try:
        with sync_playwright() as playwright:
            launch = {"headless": True}
            chromium = os.environ.get("CHROMIUM_PATH") or shutil.which("chromium")
            if chromium:
                launch["executable_path"] = chromium
            proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
            if proxy:
                launch["proxy"] = {"server": proxy, "bypass": "127.0.0.1,localhost"}
            browser = playwright.chromium.launch(**launch)
            try:
                run_checks(browser, f"http://127.0.0.1:{server.server_port}")
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
