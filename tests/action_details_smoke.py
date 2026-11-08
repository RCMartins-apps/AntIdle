"""Related egg actions share fields, ordering and result formatting."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game


def check_details(page, width, url):
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["ants"]["standardWorkersUnlocked"] = True
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    load_game(page, game)
    page.get_by_role("tab", name="Nest", exact=True).click()
    for name, duration in [("Raise Worker Egg", "2m 0s"), ("Raise Scout Egg", "1m 30s")]:
        page.get_by_label("Details for " + name, exact=True).click()
        panel = page.locator(".action-details[open]").filter(has=page.locator(".egg-description:visible"))
        expect(panel).to_have_count(1)
        expect(panel.locator(".action-detail-row b")).to_have_text(["Queue: ", "Development: ", "Larval care: "])
        expect(panel).to_contain_text("Shared laying queue; costs paid when laid.")
        expect(panel).to_contain_text(duration + " base; varies with care.")
        expect(panel).to_contain_text("20 sugar · 2 protein per egg.")
        expect(panel.locator(".action-result")).to_be_empty()
        expect(panel).to_contain_text("Requires")
        assert_fits(page, width)
        page.get_by_label("Details for " + name, exact=True).click()
    small = copy.deepcopy(game)
    small["ants"]["standardWorkersUnlocked"] = False
    for upgrade in small["upgrades"]["upgrades"]:
        if upgrade["name"] == "StandardWorkers":
            upgrade["unlocked"] = False
    load_game(page, small)
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_label("Details for Raise Worker Egg", exact=True).click()
    expect(page.locator(".egg-description:visible")).to_contain_text("15.0s base")
    expect(page.locator(".egg-description:visible")).to_contain_text("No resources.")
    collapsed = copy.deepcopy(game)
    collapsed["ants"].update(workers=0, smallWorkers=0, scouts=0, eggsAndLarvae=[], tasks=[], standardWorkersUnlocked=True, queenRoutine={"enabled": False, "targetWorkers": 0, "sugarReserve": 0})
    load_game(page, collapsed)
    page.get_by_role("tab", name="Nest", exact=True).click()
    page.get_by_label("Details for Raise Emergency Worker", exact=True).click()
    emergency = page.locator(".egg-description:visible")
    expect(emergency.locator("b")).to_have_text(["Queue: ", "Development: ", "Larval care: "])
    expect(emergency).to_contain_text("Immediate; bypasses brood limit.")
    expect(emergency).to_contain_text("15.0s base")
    expect(emergency).to_contain_text("No resources.")
    assert_fits(page, width)
    if width == 320:
        page.locator(".action-details[open]").screenshot(path="/tmp/egg-details-320.png")
    print(f"PASS {width}px: matching egg detail fields, model-derived durations, care, requirements and results", flush=True)


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
                    check_details(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
