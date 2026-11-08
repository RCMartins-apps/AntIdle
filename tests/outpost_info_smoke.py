"""Outpost disclosures survive live state updates."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game, select_expedition


def check_stability(page, width, url):
    now = datetime(2026, 11, 8, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"].update(workers=50 * UNIT, scouts=2 * UNIT, eggsAndLarvae=[],
        tasks=[[{"SugarCollector": {}}, 20 * UNIT], [{"Tunneler": {}}, 10 * UNIT], [{"Explorer": {}}, 0]])
    game["basicResources"].update(sugar={"amount": 10000 * UNIT, "maxAmount": 20000 * UNIT},
        protein={"amount": 300 * UNIT, "maxAmount": 1000 * UNIT},
        water={"amount": 1000 * UNIT, "maxAmount": 2000 * UNIT},
        tunnelingSpace={"amount": 2000 * UNIT, "maxAmount": 5000 * UNIT},
        knowledge={"amount": 1000 * UNIT, "maxAmount": 9223372036854775807})
    game["exploration"].update(experience=6000, surveys={"dry-clearing": 3})
    load_game(page, game)
    page.get_by_role("tab", name="Nest", exact=True).click()

    def action(name):
        return page.locator(".nest-outposts .action-cell").filter(has=page.get_by_role("button", name=name, exact=True))

    def keep_open(name):
        row = action(name)
        row.locator("summary").click()
        panel = row.locator("details")
        expect(panel).to_have_attribute("open", "")
        panel.evaluate("el => el.dataset.stabilityProbe = 'retained'")
        before = row.locator(".action-costs").inner_text()
        page.clock.run_for(2000)
        expect(panel).to_have_attribute("open", "")
        expect(panel).to_have_attribute("data-stability-probe", "retained")
        assert row.locator(".action-costs").inner_text() != before
        assert_fits(page, width)
        return row

    establish = keep_open("Establish Dry Clearing Outpost")
    establish.get_by_role("button").click()
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("Level 1")
    expansion = keep_open("Expand Dry Clearing Outpost")
    expansion.get_by_role("button").click()
    expect(page.locator(".dry-clearing-outpost")).to_contain_text("Level 2")
    expect(expansion.locator("details")).to_have_attribute("open", "")
    expect(expansion.locator(".action-costs")).to_contain_text("/8000 sugar")
    expansion.locator("summary").click()
    page.clock.run_for(1000)
    expect(expansion.locator("details")).not_to_have_attribute("open", "")
    assert not errors, errors
    print(f"PASS {width}px: establish/expand help stays open across live ticks, costs stay live, level transition works, manual close stays closed", flush=True)


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
                    check_stability(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
