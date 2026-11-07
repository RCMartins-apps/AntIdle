"""Scout egg ordering during laying, mixed FIFO orders and mobile controls."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game


def check_scout_queue(page, width, url):
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
        queenRoutine={"enabled": True, "targetWorkers": 30, "sugarReserve": 0},
        tasks=[[{"SugarCollector": {}}, 10 * UNIT]])
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["nestAttributes"].update(maxAnts=100 * UNIT, maxEggs=10 * UNIT)
    game["basicResources"]["sugar"]["amount"] = 1000 * UNIT
    game["basicResources"]["protein"]["amount"] = 100 * UNIT
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] == "QueensRoutine")

    def nest():
        page.get_by_role("tab", name="Nest", exact=True).click()

    def kinds(saved):
        return [b["Developing"]["kind"] for b in saved["ants"]["eggsAndLarvae"]]

    load_game(page, game)
    nest()
    worker = page.get_by_role("button", name="Raise Worker Egg", exact=True)
    scout = page.get_by_role("button", name="Raise Scout Egg", exact=True)
    worker.click()
    page.clock.run_for(200)
    laying = save_game(page)
    assert kinds(laying) == ["worker"] and laying["ants"]["layingCooldown"] > 0
    nest()
    expect(scout).to_be_enabled()
    scout.click()
    queued_scout = save_game(page)
    assert queued_scout["ants"]["scoutEggOrders"] == [0]
    assert queued_scout["basicResources"] == laying["basicResources"]
    assert kinds(queued_scout) == ["worker"]
    nest()
    worker.click()
    scout.click()
    queued = save_game(page)
    assert queued["ants"]["eggOrders"] == 1
    assert queued["ants"]["scoutEggOrders"] == [0, 1]
    nest()
    expect(page.locator(".brood-pipeline")).to_contain_text("1 worker · 2 scout eggs ordered")
    card = page.locator(".action-cell").filter(has=scout)
    expect(card.locator(".action-costs")).to_contain_text("/50 sugar")
    expect(card.locator(".action-costs")).to_contain_text("/2 protein")
    assert_fits(page, width)
    page.clock.run_for(20000)
    first = save_game(page)
    assert kinds(first) == ["worker", "scout"]
    assert first["ants"]["scoutEggOrders"] == [1]
    page.reload(wait_until="networkidle")
    assert save_game(page)["ants"]["scoutEggOrders"] == [1]
    page.clock.run_for(20000)
    assert kinds(save_game(page)) == ["worker", "scout", "worker"]
    page.clock.run_for(20000)
    completed = save_game(page)
    assert kinds(completed) == ["worker", "scout", "worker", "scout"]
    assert completed["ants"]["scoutEggOrders"] == []
    assert completed["ants"]["eggOrders"] == 0

    # A queued scout lacking protein cannot be overtaken by automatic workers.
    hungry = copy.deepcopy(queued)
    hungry["basicResources"]["protein"]["amount"] = 0
    load_game(page, hungry)
    page.clock.run_for(22000)
    waiting = save_game(page)
    assert kinds(waiting) == ["worker"]
    assert waiting["ants"]["scoutEggOrders"] == [0, 1]
    assert waiting["ants"]["eggOrders"] == 1
    nest()
    expect(page.locator(".brood-pipeline")).to_contain_text("Needs protein for scout egg")
    page.get_by_role("button", name="Cancel egg orders", exact=True).click()
    cancelled = save_game(page)
    assert cancelled["ants"]["scoutEggOrders"] == [] and cancelled["ants"]["eggOrders"] == 0
    assert kinds(cancelled) == ["worker"]

    # Full brood beds are not a reason to reject an order; future housing is.
    full = copy.deepcopy(game)
    full["ants"]["queenRoutine"]["enabled"] = False
    full["ants"]["eggsAndLarvae"] = [{"Developing": {"initialTick": 100, "kind": "worker", "care": care * UNIT}} for care in (599, 100, 100, 100)]
    full["nestAttributes"].update(maxEggs=UNIT, maxAnts=15 * UNIT)
    load_game(page, full)
    nest()
    expect(scout).to_be_enabled()
    scout.click()
    expect(scout).to_be_disabled()
    expect(worker).to_be_disabled()
    expect(page.locator(".brood-pipeline")).to_contain_text("Brood places full")
    reserved = save_game(page)
    assert reserved["ants"]["scoutEggOrders"] == [0]
    for resource in ("sugar", "protein"):
        assert reserved["basicResources"][resource]["amount"] == full["basicResources"][resource]["amount"]
    page.clock.run_for(200)
    opened = save_game(page)
    assert opened["ants"]["scoutEggOrders"] == []
    assert kinds(opened).count("scout") == 1
    assert opened["ants"]["workers"] == 11 * UNIT
    nest()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: scouts queue during laying, mixed FIFO, costs, persistence, no automatic overtaking, cancellation, full beds/housing and layout", flush=True)


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
                    check_scout_queue(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
