"""Concise upgrade/action facts and stable Royal brood help."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game, select_expedition


def check_polish(page, width, url):
    now = datetime(2026, 11, 8, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(seconds=1))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"].update(scouts=2 * UNIT, eggsAndLarvae=[])
    game["exploration"].update(experience=6000, surveys={"dry-clearing": 3})
    for key in ("royalNurseryChamber", "repleteChamber", "nurseryChamber", "queenChamber"):
        game["nestAttributes"]["chambers"][key]["level"] = 1
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["unlocked"] = upgrade["name"] in ("DefaultWorkers", "RepleteStorage")
    load_game(page, game)

    def tab(name):
        page.get_by_role("tab", name=name, exact=True).click()

    def action(name):
        return page.locator(".action-cell").filter(has=page.get_by_role("button", name=name, exact=True))

    tab("Upgrades")
    for name, text in [("Foraging Techniques 1", "+15% sugar collection."),
                       ("Expedition Coordination", "+1 expedition slot."),
                       ("Construction Planning", "+2 construction queue slots (3 total).")]:
        row = action(name)
        row.locator("summary").click()
        expect(row.locator(".action-description")).to_have_text(text)
        expect(row.locator(".action-costs")).to_be_visible()
        row.locator("summary").click()
    tab("Nest")
    for name, capacity in [("Establish Dry Clearing Outpost", "+50 colony slots"),
                           ("Expand Dry Clearing Outpost", "+25 colony slots")]:
        row = action(name)
        row.locator("summary").click()
        expect(row.locator(".action-description")).to_have_count(0)
        expect(row.locator(".action-result")).to_contain_text(capacity)
        expect(row.locator(".action-result")).to_contain_text("+5 Seed Forager slots")
        assert_fits(page, width)
        row.locator("summary").click()
        if name.startswith("Establish"):
            row.get_by_role("button").click()
    row = action("Promote Replete")
    row.locator("summary").click()
    expect(row.locator(".action-detail-row b")).to_have_text(["Worker: ", "Lifespan: ", "Upkeep: ", "Role: ", "Storage: "])
    expect(row.locator(".action-description")).not_to_contain_text("Nutrition")
    row.locator("summary").click()
    royal = page.locator(".nest-royal-nursery .info-details")
    page.get_by_label("About Royal brood", exact=True).click()
    royal.evaluate("el => el.dataset.probe = 'retained'")
    page.get_by_role("button", name="Queen's Chamber", exact=False).click()
    page.clock.run_for(1000)
    expect(royal).to_have_attribute("open", "")
    expect(royal).to_have_attribute("data-probe", "retained")
    tab("Upgrades")
    page.get_by_role("button", name="Foraging Techniques 1", exact=True).click()
    tab("Nest")
    expect(royal).to_have_attribute("open", "")
    expect(royal).to_have_attribute("data-probe", "retained")
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: concise upgrade facts, numeric outpost bonuses, structured replete help and Royal brood stability across construction/research", flush=True)


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
                    check_polish(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
