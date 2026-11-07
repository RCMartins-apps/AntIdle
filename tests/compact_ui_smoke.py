"""Compact descriptions, explicit information controls and visible live costs/risk."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game


def check_compact(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["world"].update(currentTick=100, targetTick=100, saveSeed=42)
    game["ants"].update(workers=50 * UNIT, scouts=2 * UNIT, smallWorkers=6 * UNIT,
        smallTunnelers=UNIT, smallScavengers=0, smallNurses=0,
        tasks=[[{name: {}}, amount * UNIT] for name, amount in (("SugarCollector", 40), ("Tunneler", 3), ("Scavenger", 2), ("BroodNurse", 2), ("AphidTender", 1), ("SeedForager", 1), ("RoyalNurse", 1))],
        defaultTask={"SugarCollector": {}}, eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[],
        lineage={"foundings": 1, "dna": 0, "sharedPaths": True},
        ages={"active": True, "workers": [{"expiresAt": 30000, "count": 50}], "smallWorkers": [{"expiresAt": 10000, "count": 6}], "scouts": [{"expiresAt": 30000, "count": 2}]})
    game["nestAttributes"].update(dryClearingOutpost=True)
    for chamber, level in (("nurseryChamber", 2), ("aphidChamber", 1), ("repleteChamber", 1), ("royalNurseryChamber", 1)):
        game["nestAttributes"]["chambers"][chamber]["level"] = level
    game["exploration"].update(experience=6000, surveys={"woodland-edge": 5, "nectar-meadow": 3, "rotten-log": 4, "dry-clearing": 3, "fallen-pine": 3, "rival-food-cache": 1}, carcassDiscovered=True)
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=True)
    load_game(page, game)

    def tab(name):
        page.get_by_role("tab", name=name, exact=name != "Tasks").click()

    def help_panel(label):
        return page.get_by_label("About " + label, exact=True).and_(page.locator(":visible")).locator("..")

    def check_help(label, content):
        control = page.get_by_label("About " + label, exact=True).and_(page.locator(":visible"))
        details = help_panel(label)
        expect(details.locator(".info-content")).not_to_be_visible()
        box = control.bounding_box()
        assert box["width"] >= 44 and box["height"] >= 44
        assert box["x"] >= 0 and box["x"] + box["width"] <= width + 1
        control.click()
        expect(details.locator(".info-content")).to_be_visible()
        expect(details.locator(".info-content")).to_contain_text(content)
        assert_fits(page, width)
        control.click()
        expect(details.locator(".info-content")).not_to_be_visible()

    tab("Tasks")
    visible = page.locator("#tasksContent").inner_text()
    for redundant in ("Small nurses provide half the care", "half a standard nurse", "New and returning workers join", "Unassigned small workers", "two shared scraps-gathering places"):
        assert redundant not in visible, redundant
    expect(page.locator("#task-broodnurse .task-description")).not_to_be_visible()
    expect(page.locator("#task-sugarcollector .task-rate")).to_contain_text("sugar/s each")
    check_help("Brood Nurse", "Food shortages")
    check_help("Default task", "New and returning")
    check_help("Small workers · 50%", "share scraps and nurse places")
    assert_fits(page, width)
    page.locator("#tasksContent").screenshot(path=f"/tmp/antidle-compact-tasks-{width}.png")

    tab("Nest")
    expect(page.locator(".brood-feeding")).not_to_be_visible()
    expect(page.locator(".brood-care-cost")).to_be_visible()
    expect(page.locator(".brood-care-cost")).to_contain_text("20 sugar · 2 protein")
    check_help("Brood", "Larval care")
    check_help("Queen's Routine", "worker target")
    check_help("Royal brood", "Royal Nurses")
    # Native disclosure controls also work from the keyboard.
    info = page.get_by_label("About Brood", exact=True)
    info.focus()
    page.keyboard.press("Enter")
    expect(help_panel("Brood").locator(".info-content")).to_be_visible()
    page.keyboard.press("Enter")
    expect(help_panel("Brood").locator(".info-content")).not_to_be_visible()
    action = page.get_by_label("Details for Raise Worker Egg", exact=True)
    action.click()
    expect(page.locator(".action-details[open]")).to_contain_text("Order worker eggs")
    assert_fits(page, width)
    action.click()
    page.locator("#nestContent").screenshot(path=f"/tmp/antidle-compact-nest-{width}.png")

    tab("Explore")
    check_help("Exploration experience", "Every trip earns XP")
    check_help("Pheromone trail", "Nectar Meadow")
    check_help("Carcass storage", "consumed before stored sugar")
    for cell in page.locator("#exploreContent .action-cell:visible").all():
        expect(cell.locator(".action-costs")).to_be_visible()
    tab("Discoveries")
    expect(page.locator(".discovery-entry")).to_have_count(6)
    for entry in page.locator(".discovery-entry").all():
        expect(entry.locator(".info-content")).not_to_be_visible()
    check_help("Wild Aphids", "Aphid Tending")
    tab("Territory")
    expect(page.locator(".territory-risk")).to_be_visible()
    check_help("Contested Foraging Ground", "Full storage does not stop")
    assert_fits(page, width)

    hungry = copy.deepcopy(game)
    hungry["basicResources"]["protein"]["amount"] = 0
    hungry["basicResources"]["water"]["amount"] = 0
    load_game(page, hungry)
    tab("Tasks")
    expect(page.locator("#task-aphidtender .task-rate")).to_contain_text("Needs protein")
    expect(page.locator("#task-seedforager .task-rate")).to_contain_text("Needs water")
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: descriptions collapsed, live rates/costs/warnings visible, task/section/action [i], keyboard, discoveries, risk and layout", flush=True)


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
                    check_compact(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
