"""Numeric chamber effects and concise expedition outcomes."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game, select_expedition


def check_chambers(page, width, url):
    now = datetime(2026, 11, 8, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["exploration"].update(experience=6000, proteinDiscovered=True, surveys={"woodland-edge": 5, "nectar-meadow": 3, "rotten-log": 4, "dry-clearing": 3, "fallen-pine": 3})
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["unlocked"] = True
    for key in ("queenChamber", "nurseryChamber", "foodStorageChamber"):
        game["nestAttributes"]["chambers"][key]["level"] = 1
    load_game(page, game)
    expected = {
        "Expand Nest": ["+10 colony slots", "+500 tunneling space capacity"],
        "Queen's Chamber": ["+2 brood places", "−4.0s laying interval"],
        "Food Storage Chamber": ["+1000 sugar capacity", "+100 protein capacity"],
        "Nursery Chamber": ["+3 brood places", "+5 Brood Nurse slots", "+1 carcass slot"],
        "Replete Chamber": ["+1 replete slot"],
        "Aphid Chamber": ["+1 Aphid Tender slot"],
        "Royal Nursery": ["+1 royal brood slot"],
    }
    for name, bonuses in expected.items():
        page.get_by_role("tab", name="Nest", exact=True).click()
        page.get_by_label("Details for " + name, exact=True).click()
        panel = page.locator(".action-details[open]")
        expect(panel).to_have_count(1)
        expect(panel.locator(".action-description")).to_have_count(0)
        for bonus in bonuses:
            expect(panel.locator(".action-result")).to_contain_text(bonus)
        assert_fits(page, width)
        if width == 390 and name == "Nursery Chamber":
            panel.screenshot(path="/tmp/nursery-bonuses-390.png")
        page.get_by_label("Details for " + name, exact=True).click()
    page.get_by_role("tab", name="Explore", exact=True).click()
    destinations = page.locator(".exploration-destination").evaluate_all("nodes => nodes.map(n => n.dataset.destination)")
    assert len(destinations) == 10
    for destination in destinations:
        select_expedition(page, destination)
        card = page.locator(f'.exploration-destination[data-destination="{destination}"]')
        card.locator(".action-details > summary").click()
        panel = card.locator(".action-details[open]")
        expect(panel.locator(".exploration-outcomes")).to_contain_text("Finds:")
        expect(panel.locator(".exploration-outcomes")).not_to_contain_text("XP")
        expect(card.locator(".destination-preview")).to_contain_text("XP")
        if destination == "surroundings":
            expect(panel).to_contain_text("Carcass guaranteed")
        if destination == "rival-food-cache":
            expect(card.locator(".raid-risk")).to_be_visible()
            expect(panel).to_contain_text("uniformly rolled")
        assert_fits(page, width)
        card.locator(".action-details > summary").click()
    print(f"PASS {width}px: numeric chamber bonuses, derived nursery/queen effects, consistent labels and concise expedition outcomes", flush=True)


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
                    check_chambers(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
