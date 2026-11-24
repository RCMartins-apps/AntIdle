"""Manual and automatic recovery after losing the workforce."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from ci_browser_smoke import ProductionHandler
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game, select_tab


def check_recovery(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["world"].update(currentTick=100, targetTick=100, saveSeed=42)
    game["ants"].update(workers=0, smallWorkers=0, scouts=0, repletes=0, daughterQueens=0,
        eggsAndLarvae=[], eggOrders=0, layingCooldown=0, emergencyForagingTicks=0, sugarCumulativeDebt=0,
        queenRoutine={"enabled": False, "targetWorkers": 10, "sugarReserve": 1000 * UNIT})
    game["basicResources"]["sugar"]["amount"] = 0
    game["basicResources"]["protein"]["amount"] = 0
    game["nestAttributes"].update(maxEggs=UNIT, maxAnts=10 * UNIT)
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] in ("QueensRoutine", "StandardWorkers"))

    def nest():
        select_tab(page, "Nest")

    load_game(page, game)
    nest()
    emergency = page.get_by_role("button", name="Raise Emergency Worker", exact=True)
    expect(emergency).to_have_count(0)
    expect(page.locator(".brood-pipeline")).to_contain_text("Queen foraging")
    page.clock.run_for(25000)
    free_egg = save_game(page)
    assert free_egg["basicResources"]["sugar"]["amount"] == 0
    assert free_egg["ants"]["eggsAndLarvae"][0]["Developing"]["kind"] == "emergency-small"
    page.clock.run_for(21200)
    assert save_game(page)["ants"]["smallWorkers"] == UNIT

    funded = copy.deepcopy(game)
    funded["basicResources"]["sugar"]["amount"] = 5 * UNIT
    load_game(page, funded)
    nest()
    expect(emergency).to_be_enabled()
    expect(page.locator(".emergency-recovery .action-costs")).to_contain_text("/5 sugar")
    emergency.click()
    expect(emergency).to_have_count(0)
    expect(page.locator(".brood-pipeline")).to_contain_text("Laying")
    laid = save_game(page)
    assert laid["basicResources"]["sugar"]["amount"] == 0
    assert laid["ants"]["standardWorkersUnlocked"]
    assert laid["ants"]["eggsAndLarvae"][0]["Developing"]["kind"] == "emergency-small"
    page.clock.run_for(21200)
    recovered = save_game(page)
    assert recovered["ants"]["smallWorkers"] == UNIT
    assert recovered["basicResources"]["sugar"]["amount"] > 0
    nest()
    expect(emergency).to_have_count(0)
    assert_fits(page, width)

    # No stock/protein, hungry larvae fill every brood/housing place, reserve kept.
    auto = copy.deepcopy(game)
    auto["ants"]["queenRoutine"]["enabled"] = True
    auto["ants"]["eggsAndLarvae"] = [{"Developing": {"initialTick": 100, "kind": "worker", "care": 100 * UNIT, "laying": False}}]
    auto["nestAttributes"]["maxAnts"] = UNIT
    load_game(page, auto)
    nest()
    expect(page.locator(".queen-routine-status")).to_contain_text("Queen foraging")
    expect(page.locator(".brood-pipeline")).to_contain_text("Needs protein")
    page.clock.run_for(10000)
    partial = save_game(page)
    assert partial["ants"]["emergencyForagingTicks"] == 50
    page.reload(wait_until="networkidle")
    assert save_game(page)["ants"]["emergencyForagingTicks"] == 50
    page.clock.run_for(15000)
    waiting = save_game(page)
    assert len(waiting["ants"]["eggsAndLarvae"]) == 2
    assert waiting["basicResources"]["sugar"]["amount"] == 0
    assert waiting["statistics"]["sugarU"]["thisRun"] == 5 * UNIT
    nest()
    expect(page.locator(".queen-routine-status")).to_contain_text("Rearing one emergency small worker")
    assert_fits(page, width)
    page.clock.run_for(23000)
    restored = save_game(page)
    assert restored["ants"]["smallWorkers"] == UNIT
    assert restored["ants"]["emergencyForagingTicks"] == 0
    assert restored["ants"]["queenRoutine"]["sugarReserve"] == 1000 * UNIT
    assert restored["ants"]["eggsAndLarvae"] == auto["ants"]["eggsAndLarvae"]
    assert restored["statistics"]["eggsCount"]["thisRun"] == 1

    # Pausing routine spending leaves the queen’s zero-stock fallback active.
    load_game(page, auto)
    nest()
    page.locator("#queen-routine-enabled").uncheck()
    page.clock.run_for(30000)
    paused = save_game(page)
    assert paused["ants"]["emergencyForagingTicks"] == 0
    assert len(paused["ants"]["eggsAndLarvae"]) == 2
    starving = copy.deepcopy(game)
    starving["ants"].update(workers=UNIT, tasks=[[{"Tunneler": {}}, UNIT]])
    load_game(page, starving)
    nest()
    expect(page.locator(".starvation-warning")).to_contain_text("Colony is starving")
    expect(page.locator(".starvation-warning")).to_contain_text("Sugar deficit:")
    expect(emergency).to_have_count(0)
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: manual costs/recovery, protected automatic provisions, hungry/full brood, save/reload, reserve, paused routine, starvation warning and layout", flush=True)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 0), partial(ProductionHandler if os.environ.get("ANTIDLE_PRODUCTION") else QuietHandler, directory=str(ROOT)))
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
                    check_recovery(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
