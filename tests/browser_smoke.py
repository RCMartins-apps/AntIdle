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


def select_expedition(page, destination):
    """Choose an unlocked mobile route; desktop retains the comparison grid."""
    picker = page.locator("#expedition-destination")
    if not picker.is_visible():
        return
    name = destination.removeprefix("Explore ").removeprefix("Raid ")
    options = picker.locator("option").evaluate_all("nodes => nodes.map(n => ({value: n.value, name: n.textContent.split(' · ')[0]}))")
    chosen = next((o["value"] for o in options if o["value"] == destination or o["name"] == name), None)
    if chosen is not None:
        picker.select_option(chosen)


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
        resource["amount"] = 100000 * UNIT
        if resource["maxAmount"] != 9223372036854775807:
            resource["maxAmount"] = 100000 * UNIT
    game["ants"]["standardWorkersUnlocked"] = True
    game["ants"]["workers"] = 300 * UNIT
    game["ants"]["tasks"] = [
        [{"SugarCollector": {}}, 0],
        [{"Tunneler": {}}, 0],
        [{"Explorer": {}}, 0],
    ]
    game["nestAttributes"]["maxAnts"] = 1000 * UNIT
    game["nestAttributes"]["maxEggs"] = 500 * UNIT
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = True
    return game


def check_nest_capacity(page, initial):
    game = progressed_game(initial)
    game["ants"]["workers"] = 0
    game["nestAttributes"]["chambers"]["nestChamber"]["level"] = 3
    game["basicResources"]["tunnelingSpace"] = {"amount": 2000 * UNIT, "maxAmount": 3500 * UNIT}
    load_game(page, game)
    restored = save_game(page)
    assert restored["version"] == 6
    assert restored["basicResources"]["tunnelingSpace"]["maxAmount"] == 3500 * UNIT
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_label("Details for Expand Nest", exact=True).click()
    expect(page.locator("details[open]")).to_contain_text("+500 max tunneling space")
    page.get_by_role("button", name="Expand Nest", exact=False).click()
    page.clock.run_for(400)
    upgraded = save_game(page)
    assert upgraded["nestAttributes"]["chambers"]["nestChamber"]["level"] == 4
    assert upgraded["basicResources"]["tunnelingSpace"]["maxAmount"] == 4000 * UNIT
    page.clock.run_for(1000)
    assert save_game(page)["basicResources"]["tunnelingSpace"]["maxAmount"] == 4000 * UNIT
    page.reload(wait_until="networkidle")
    assert save_game(page)["basicResources"]["tunnelingSpace"]["maxAmount"] == 4000 * UNIT
    game["nestAttributes"]["chambers"]["nestChamber"]["level"] = 0
    load_game(page, game)
    page.clock.run_for(400)
    assert save_game(page)["basicResources"]["tunnelingSpace"]["maxAmount"] == 3500 * UNIT
    # Current-version saves keep their stored capacity, including other bonuses.
    game["nestAttributes"]["chambers"]["nestChamber"]["level"] = 3
    game["basicResources"]["tunnelingSpace"]["maxAmount"] = 7250 * UNIT
    load_game(page, game)
    page.clock.run_for(400)
    assert save_game(page)["basicResources"]["tunnelingSpace"]["maxAmount"] == 7250 * UNIT
    print("PASS nest capacity: current save preservation, upgrades, stable ticks, save reload")


def check_compact_details(page):
    before = save_game(page)
    page.get_by_role("tab", name="Nest", exact=True).click()
    action = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Raise Worker Egg", exact=True))
    primary = action.get_by_role("button", name="Raise Worker Egg", exact=True)
    toggle = action.get_by_label("Details for Raise Worker Egg", exact=True)
    panel = action.locator(".action-details")
    assert panel.get_attribute("open") is None
    expect(toggle.locator("svg")).to_have_count(1)
    icon_box, button_box = toggle.bounding_box(), primary.bounding_box()
    assert icon_box["width"] >= 44 and icon_box["height"] >= 44
    assert button_box["x"] + button_box["width"] <= icon_box["x"]
    assert abs(icon_box["y"] - button_box["y"]) <= 1
    closed_height = action.bounding_box()["height"]
    # The closed disclosure adds no row below the action and its visible costs.
    assert closed_height <= button_box["height"] + action.locator(".action-costs").bounding_box()["height"] + 18
    toggle.focus()
    toggle.press("Space")
    expect(panel).to_have_attribute("open", "")
    expect(panel).to_contain_text("Order worker eggs")
    assert action.bounding_box()["height"] > closed_height
    expect(action.locator(".action-costs")).to_be_visible()
    toggle.press("Enter")
    assert panel.get_attribute("open") is None
    assert abs(action.bounding_box()["height"] - closed_height) <= 1
    after = save_game(page)
    assert (after["ants"], after["basicResources"]) == (before["ants"], before["basicResources"])


def check_chamber_bonuses(page, initial):
    def capacities(game):
        return {
            "ants": game["nestAttributes"]["maxAnts"],
            "eggs": game["nestAttributes"]["maxEggs"],
            "sugar": game["basicResources"]["sugar"]["maxAmount"],
            "tunneling": game["basicResources"]["tunnelingSpace"]["maxAmount"],
        }

    for name, chamber, increases, details in (
        ("Expand Nest", "nestChamber", {"ants": 10, "tunneling": 500},
         ("+10 max ants", "+500 max tunneling space")),
        ("Queen's Chamber", "queenChamber", {"ants": 5, "eggs": 2},
         ("+5 max ants", "+2 brood places")),
        ("Food Storage Chamber", "foodStorageChamber", {"sugar": 1000},
         ("+1000 max sugar",)),
    ):
        game = progressed_game(initial)
        game["ants"]["workers"] = 0
        game["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
        load_game(page, game)
        page.get_by_label(f"Details for {name}", exact=True).click()
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
    game["basicResources"]["knowledge"]["amount"] = 0
    game["world"]["lastUpdateTime"] = page.evaluate("Date.now()")
    load_game(page, game)
    page.clock.run_for(200)
    result = save_game(page)
    assert result["basicResources"]["knowledge"]["amount"] == 0
    print("PASS shared chamber bonuses: displayed and applied caps, Queen ant capacity bonus, income, save reload")


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
            page.get_by_label("Details for Gather Sugar", exact=True).click()
            expect(page.locator("details[open]")).to_contain_text("+1 sugar")
        initial = save_game(page)
        assert initial["basicResources"]["sugar"]["amount"] == UNIT
        page.reload(wait_until="networkidle")
        assert save_game(page)["basicResources"] == initial["basicResources"]
        assert_fits(page, width)

        game = progressed_game(initial)
        load_game(page, game)
        if width <= 767:
            check_compact_details(page)
        for row in page.locator(".resource-row:visible").all():
            expect(row.locator(".resource-name .resource-icon svg")).to_have_count(1)
            assert row.locator(".resource-name").inner_text().strip()
            label_box = row.locator(".resource-label").bounding_box()
            amount_box = row.locator(".resource-amount").bounding_box()
            assert label_box["x"] + label_box["width"] <= amount_box["x"] + 1
        for tab in ("Nest", "Tasks", "Upgrades", "Explore", "Settings"):
            page.get_by_role("tab", name=tab, exact=tab != "Tasks").click()
            assert_fits(page, width)
            for cell in page.locator(".action-cell:visible").all():
                expect(cell.locator(".action-costs")).to_be_visible()
                assert cell.locator(".action-costs").inner_text().strip()
                for cost in cell.locator(".action-costs .action-cost").all():
                    expect(cost.locator(".resource-icon svg")).to_have_count(1)
                    assert cost.get_attribute("title")
                    assert cost.get_attribute("aria-label")
                    expect(cost.locator(".visually-hidden")).to_have_count(1)

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
                page.locator(".nest-brood").get_by_label("Action amount").select_option(str(multiplier))
                modifiers = []
                page.get_by_label("Details for Raise Worker Egg", exact=True).click()
                expect(page.locator("details[open]")).to_contain_text(f"+{multiplier} eggs")
            page.get_by_role("button", name="Raise Worker Egg", exact=False).click(modifiers=modifiers)
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
            assert result["ants"]["eggOrders"] == multiplier
            assert result["ants"]["eggsAndLarvae"] == []
            assert result["ants"]["tasks"][1][1] == multiplier * UNIT

        # Visible batch price follows what will actually be bought, including partial batches.
        partial = progressed_game(initial)
        partial["basicResources"]["sugar"]["amount"] = 35 * UNIT
        partial["ants"]["workers"] = 0
        load_game(page, partial)
        if width <= 767:
            page.locator(".nest-brood").get_by_label("Action amount").select_option("10")
            batch_modifiers = []
        else:
            page.keyboard.down("Control")
            batch_modifiers = ["Control"]
        egg_action = page.locator(".action-cell").filter(has=page.get_by_role("button", name="Raise Worker Egg", exact=False))
        expect(egg_action.locator(".action-costs")).to_contain_text("35/30 sugar")
        page.get_by_role("button", name="Raise Worker Egg", exact=False).click(modifiers=batch_modifiers)
        if width > 767:
            page.keyboard.up("Control")
        bought = save_game(page)
        assert bought["basicResources"]["sugar"]["amount"] == 35 * UNIT
        assert bought["ants"]["eggOrders"] == 3
        assert bought["ants"]["eggsAndLarvae"] == []
        page.clock.run_for(200)
        laid = save_game(page)
        assert laid["basicResources"]["sugar"]["amount"] == 25 * UNIT
        assert laid["ants"]["eggOrders"] == 2
        assert len(laid["ants"]["eggsAndLarvae"]) == 1
        page.get_by_role("tab", name="Nest", exact=True).click()
        expect(egg_action.locator(".action-costs")).to_contain_text("25/20 sugar" if width <= 767 else "25/10 sugar")
        load_game(page, game)

        if width <= 767:
            blocked = copy.deepcopy(game)
            blocked["basicResources"]["sugar"]["amount"] = 0
            load_game(page, blocked)
            expect(page.get_by_role("button", name="Raise Worker Egg", exact=True)).to_be_disabled()
            page.get_by_label("Details for Raise Worker Egg", exact=True).click()
            expect(page.locator(".action-details[open]")).to_contain_text("Order worker eggs")
            load_game(page, game)

        # Crossing the breakpoint must restore desktop shortcuts and mobile details.
        page.set_viewport_size({"width": 1920, "height": 844})
        page.get_by_role("tab", name="Nest", exact=True).click()
        page.get_by_label("Details for Raise Worker Egg", exact=True).click()
        expect(page.locator(".action-details[open]")).to_contain_text("Order worker eggs")
        page.get_by_label("Details for Raise Worker Egg", exact=True).click()
        page.set_viewport_size({"width": 390, "height": 844})
        page.get_by_label("Details for Raise Worker Egg", exact=True).click()
        expect(page.locator("details[open]")).to_contain_text("Order worker eggs")
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
