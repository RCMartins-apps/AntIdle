"""Mobile destination selection and nearby party/launch controls."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game, select_expedition


def check_mobile_explore(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["world"].update(currentTick=100, targetTick=100, saveSeed=42)
    game["ants"].update(workers=50 * UNIT, scouts=5 * UNIT, smallWorkers=0,
        defaultTask={"SugarCollector": {}}, tasks=[[{"SugarCollector": {}}, 50 * UNIT], [{"Explorer": {}}, 0]],
        eggsAndLarvae=[], lineage={"foundings": 1, "dna": 0, "sharedPaths": True},
        ages={"active": True, "workers": [{"expiresAt": 30000, "count": 50}], "scouts": [{"expiresAt": 30000, "count": 5}]})
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["basicResources"]["sugar"] = {"amount": 20000 * UNIT, "maxAmount": 50000 * UNIT}
    game["exploration"].update(experience=6000, surveys={"woodland-edge": 5, "nectar-meadow": 3, "rotten-log": 4, "dry-clearing": 3, "fallen-pine": 3, "rival-food-cache": 1})
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] == "DefaultWorkers")

    def explore():
        page.get_by_role("tab", name="Explore", exact=True).click()

    def card(destination):
        return page.locator(f'.exploration-destination[data-destination="{destination}"]')

    def choose(destination):
        if width <= 767:
            select_expedition(page, destination)

    early = copy.deepcopy(game)
    early["exploration"].update(experience=0, surveys={})
    early["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 0
    early["ants"]["scouts"] = 0
    load_game(page, early)
    explore()
    expect(page.locator(".exploration-destination")).to_have_count(1)
    carousel = page.locator(".destination-carousel")
    expect(page.locator(".carousel-position")).to_have_text("1 / 1")
    if width <= 767:
        expect(page.get_by_role("button", name="Next destination")).to_be_disabled()
    load_game(page, game)
    explore()
    expect(page.locator(".exploration-destination")).to_have_count(10)
    if width <= 767:
        expect(carousel).to_be_visible()
        expect(page.locator(".exploration-destination:visible")).to_have_count(1)
        for destination in page.locator(".exploration-destination").evaluate_all("nodes => nodes.map(n => n.dataset.destination)"):
            choose(destination)
            expect(card(destination)).to_be_visible()
            expect(card(destination).locator(".action-costs")).to_be_visible()
            expect(page.locator(".exploration-destination:visible")).to_have_count(1)
            assert_fits(page, width)
        choose("surroundings")
        page.get_by_role("button", name="Previous destination", exact=True).click()
        expect(card("collect-resin")).to_be_visible()
        page.get_by_role("button", name="Next destination", exact=True).focus()
        page.keyboard.press("Enter")
        expect(card("surroundings")).to_be_visible()
        # Swipe detection distinguishes deliberate horizontal movement from scrolling/jitter.
        def swipe(dx, dy):
            selected = page.locator(".exploration-destination.destination-selected")
            selected.evaluate("""(el, delta) => {
                const start = new Touch({identifier: 1, target: el, clientX: 180, clientY: 300});
                const end = new Touch({identifier: 1, target: el, clientX: 180 + delta[0], clientY: 300 + delta[1]});
                el.dispatchEvent(new TouchEvent('touchstart', {bubbles: true, touches: [start]}));
                el.dispatchEvent(new TouchEvent('touchend', {bubbles: true, cancelable: true, changedTouches: [end]}));
            }""", [dx, dy])
        swipe(-100, 5)
        expect(card("woodland-edge")).to_be_visible()
        swipe(100, 5)
        expect(card("surroundings")).to_be_visible()
        swipe(-100, 150)
        swipe(-20, 0)
        expect(card("surroundings")).to_be_visible()
        # A real Chromium touch gesture must navigate without launching the card.
        card("surroundings").scroll_into_view_if_needed()
        bounds = card("surroundings").bounding_box()
        touch = page.context.new_cdp_session(page)
        y = bounds["y"] + 15
        x = bounds["x"] + bounds["width"] - 30
        touch.send("Input.dispatchTouchEvent", {"type": "touchStart", "touchPoints": [{"x": x, "y": y}]})
        for step in (30, 60, 100):
            touch.send("Input.dispatchTouchEvent", {"type": "touchMove", "touchPoints": [{"x": x - step, "y": y}]})
        touch.send("Input.dispatchTouchEvent", {"type": "touchEnd", "touchPoints": []})
        expect(card("woodland-edge")).to_be_visible()
        touch.detach()
    else:
        expect(carousel).not_to_be_visible()
        expect(page.locator(".exploration-destination:visible")).to_have_count(10)

    choose("rival-food-cache")
    page.locator("#explorer-count").fill("1")
    expect(card("rival-food-cache").locator(".raid-risk")).to_be_visible()
    expect(card("rival-food-cache").get_by_role("button")).to_be_disabled()
    if width <= 767:
        page.get_by_role("button", name="Send 10 workers", exact=True).click()
    else:
        page.locator("#explorer-count").fill("10")
    expect(page.locator("#explorer-count")).to_have_value("10")
    expect(card("rival-food-cache").get_by_role("button")).to_be_enabled()
    expect(card("rival-food-cache").locator(".raid-risk")).to_contain_text("workers")
    if width <= 767:
        launch = card("rival-food-cache").get_by_role("button").bounding_box()
        setup = page.locator(".expedition-setup").bounding_box()
        assert launch["y"] + launch["height"] - setup["y"] < 700
        assert page.locator(".exploration-progress").bounding_box()["y"] > launch["y"]
        assert page.locator(".pheromone-trails").bounding_box()["y"] > launch["y"]

    page.locator("#explorer-count").fill("999")
    expect(page.locator("#explorer-count")).to_have_value("37")
    choose("collect-resin")
    if width <= 767:
        page.get_by_role("button", name="Send 10 workers", exact=True).click()
    else:
        page.locator("#explorer-count").fill("10")
    page.locator("#extra-scouts").select_option("1")
    choose("surroundings")
    choose("collect-resin")
    expect(page.locator("#explorer-count")).to_have_value("10")
    expect(page.locator("#extra-scouts")).to_have_value("1")
    expect(card("collect-resin").locator(".action-costs")).to_contain_text("/360 sugar")
    expect(card("collect-resin").locator(".action-costs")).to_contain_text("/3 scouts")
    page.locator("#exploreContent").screenshot(path=f"/tmp/antidle-mobile-explore-{width}.png")
    card("collect-resin").get_by_role("button").click()
    sent = save_game(page)
    party = sent["exploration"]["explorationParties"][0]
    assert party["destination"] == "collect-resin"
    assert party["amountWorkers"] == 10 * UNIT and party["scouts"] == 3 * UNIT
    assert sent["basicResources"]["sugar"]["amount"] == 19640 * UNIT
    explore()
    choose("surroundings")
    page.get_by_role("button", name="Send 17 workers", exact=True).click()
    card("surroundings").get_by_role("button").click()
    expect(page.locator(".expedition-status")).to_contain_text("All slots in use")
    expect(card("surroundings").get_by_role("button")).to_be_disabled()
    expect(card("surroundings").locator('.action-cost[aria-label^="expedition slots"] .text-danger')).to_have_text("0")
    assert_fits(page, width)

    no_workers = copy.deepcopy(game)
    no_workers["ants"].update(workers=0, tasks=[[{"SugarCollector": {}}, 0], [{"Explorer": {}}, 0]])
    load_game(page, no_workers)
    explore()
    choose("surroundings")
    expect(page.locator("#explorer-count")).to_be_disabled()
    expect(card("surroundings").get_by_role("button")).to_be_disabled()
    expect(card("surroundings").locator('.action-cost[aria-label^="Sugar Collector workers"] .text-danger')).to_have_text("0")
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: unlocked destination carousel/grid, one mobile action, nearby controls, minimum presets, visible risk/costs, capped workers, actual launch, slots and empty workforce", flush=True)


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
                    context = browser.new_context(viewport={"width": width, "height": 844}, has_touch=width <= 767, is_mobile=width <= 767)
                    check_mobile_explore(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
