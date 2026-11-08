"""Concise founding copy, caste labels and dedicated Lineage tab."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game, select_expedition


def check_lineage(page, width, url):
    now = datetime(2026, 11, 8, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    page.get_by_role("tab", name="Nest", exact=True).click()
    expect(page.get_by_role("tab", name="Lineage", exact=True)).to_have_count(0)
    for _ in range(5):
        page.get_by_role("button", name="Gather Sugar", exact=True).click()
    expect(page.get_by_role("button", name="Raise Small Worker Egg", exact=True)).to_be_enabled()
    page.get_by_label("Details for Raise Small Worker Egg", exact=True).click()
    expect(page.locator(".egg-description:visible")).to_contain_text("15.0s base")
    game = progressed_game(initial)
    game["ants"]["smallWorkers"] = UNIT
    game["nestAttributes"]["chambers"]["royalNurseryChamber"]["level"] = 1
    load_game(page, game)
    expect(page.get_by_role("tab", name="Lineage", exact=True)).to_be_visible()
    expect(page.locator("#nestContent .lineage-section")).to_have_count(0)
    page.get_by_role("tab", name="Lineage", exact=True).click()
    expect(page.locator("#lineageContent")).to_be_visible()
    expect(page.locator(".lineage-section")).to_contain_text("Generation 1 · DNA: 0")
    expect(page.locator(".lineage-queen-status")).to_have_text("Daughter queen: not ready")
    page.get_by_label("About Queen's lineage", exact=True).click()
    expect(page.locator("#lineageContent .info-content")).to_contain_text("permanent Understandings")
    page.get_by_label("About Queen's lineage", exact=True).click()
    expect(page.get_by_role("button", name="Found daughter colony", exact=True)).to_have_count(0)
    assert_fits(page, width)
    small = page.locator(".resource-row").filter(has=page.get_by_text("Small workers", exact=True))
    expected = "Small founding workers: 50% task output and upkeep; live 30–40 simulated minutes."
    if width <= 767:
        small.locator("summary").click()
        expect(small.locator("details > div")).to_have_text(expected)
    else:
        small.locator(".resource-name").hover()
        expect(page.locator(".game-tooltip")).to_have_text(expected)
    inherited = copy.deepcopy(game)
    inherited["nestAttributes"]["chambers"]["royalNurseryChamber"]["level"] = 0
    inherited["ants"]["lineage"] = {"foundings": 1, "dna": 1, "sharedPaths": False}
    load_game(page, inherited)
    page.get_by_role("tab", name="Lineage", exact=True).click()
    expect(page.get_by_role("button", name="Learn Shared Paths", exact=True)).to_be_enabled()
    page.reload(wait_until="networkidle")
    expect(page.get_by_role("tab", name="Lineage", exact=True)).to_be_visible()
    print(f"PASS {width}px: founding action label/help, exact concise copy, Lineage tab gate/separation, inherited access and reload", flush=True)


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
                    check_lineage(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
