"""Clear queue blocking reasons and consistent colony reservation feedback."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game


def check_feedback(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["world"].update(currentTick=100, targetTick=100, saveSeed=42)
    game["ants"].update(workers=10 * UNIT, smallWorkers=0, scouts=0, repletes=0, daughterQueens=0,
        defaultTask=None, eggsAndLarvae=[], eggOrders=0, scoutEggOrders=[], layingCooldown=0,
        tasks=[[{"SugarCollector": {}}, 10 * UNIT]])
    game["nestAttributes"].update(maxAnts=50 * UNIT, maxBuildQueue=1, buildQueue=[])
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["basicResources"]["tunnelingSpace"]["amount"] = 0
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] in ("RoyalNurseryPlanning", "AphidTending", "RepleteStorage"))

    def nest():
        page.get_by_role("tab", name="Nest", exact=True).click()

    def card(button):
        return page.locator(".action-cell").filter(has=button)

    load_game(page, game)
    nest()
    expand = page.get_by_role("button", name="Expand Nest", exact=True)
    expect(page.locator(".action-blocked-reason")).to_have_count(0)
    expand.click()
    expect(expand).to_be_disabled()
    expect(page.locator(".action-blocked-reason")).to_have_count(7)
    for reason in page.locator(".action-blocked-reason").all():
        expect(reason).to_have_text("Build queue full")
    assert_fits(page, width)
    page.get_by_role("button", name="Cancel Expand Nest", exact=True).click()
    expect(expand).to_be_enabled()
    expect(page.locator(".action-blocked-reason")).to_have_count(0)

    planning = copy.deepcopy(game)
    for upgrade in planning["upgrades"]["upgrades"]:
        if upgrade["name"] == "ConstructionPlanning":
            upgrade["unlocked"] = True
    load_game(page, planning)
    nest()
    royal_nursery = page.get_by_role("button", name="Royal Nursery", exact=True)
    royal_nursery.click()
    expect(royal_nursery).to_be_disabled()
    expect(card(royal_nursery).locator(".action-blocked-reason")).to_have_text("Already queued")
    expect(expand).to_be_enabled()
    expect(card(expand).locator(".action-blocked-reason")).to_have_count(0)
    assert_fits(page, width)
    page.get_by_role("button", name="Cancel Royal Nursery", exact=True).click()
    expect(royal_nursery).to_be_enabled()
    expect(card(royal_nursery).locator(".action-blocked-reason")).to_have_count(0)

    reserved = copy.deepcopy(game)
    reserved["nestAttributes"]["chambers"]["royalNurseryChamber"]["level"] = 1
    reserved["nestAttributes"]["maxAnts"] = 12 * UNIT
    reserved["ants"].update(eggOrders=1, scoutEggOrders=[1],
        tasks=[[{"SugarCollector": {}}, 9 * UNIT], [{"RoyalNurse": {}}, UNIT]])
    load_game(page, reserved)
    nest()
    royal = page.get_by_role("button", name="Raise Royal Egg", exact=True)
    expect(royal).to_be_disabled()
    slots = card(royal).locator(".action-cost").filter(has_text="colony slots")
    expect(slots).to_contain_text("0/1 colony slots")
    expect(slots.locator(".text-danger")).to_have_text("0")
    page.get_by_role("button", name="Cancel egg orders", exact=True).click()
    expect(royal).to_be_enabled()
    expect(slots).to_contain_text("2/1 colony slots")
    expect(slots.locator(".text-success")).to_have_text("2")
    assert_fits(page, width)

    blocked = copy.deepcopy(game)
    blocked["nestAttributes"]["maxAnts"] = 10 * UNIT
    blocked["ants"]["eggOrders"] = 1
    load_game(page, blocked)
    nest()
    expect(page.locator(".brood-pipeline")).to_contain_text("Colony slots full")
    expect(page.locator(".brood-pipeline")).not_to_contain_text("Queen ready")
    cooling = copy.deepcopy(game)
    cooling["ants"]["layingCooldown"] = 10
    load_game(page, cooling)
    nest()
    expect(page.locator(".brood-pipeline")).to_contain_text("Queen ready in 2.0s")
    expect(page.locator(".brood-pipeline")).not_to_contain_text("Next egg")
    page.clock.run_for(2000)
    expect(page.locator(".brood-pipeline")).to_contain_text("Queen ready")
    expect(page.locator(".brood-pipeline")).not_to_contain_text("Queen ready in")
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: full construction queue, duplicate royal plan, cancellation, royal reserved-space colors, blocked housing, idle queen cooldown and layout", flush=True)


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
                    check_feedback(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
