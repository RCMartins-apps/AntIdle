"""Aging preserves staffed jobs and refills remembered vacancies."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game, progressed_game


def check_aging_jobs(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["world"].update(currentTick=100, targetTick=100, saveSeed=42)
    game["ants"].update(workers=11 * UNIT, smallWorkers=0, scouts=0, repletes=0, daughterQueens=0,
        eggsAndLarvae=[], eggOrders=0, layingCooldown=0, sugarCumulativeDebt=0,
        defaultTask={"SugarCollector": {}}, agingJobVacancies=[],
        tasks=[[{"SugarCollector": {}}, 5 * UNIT], [{"Tunneler": {}}, 2 * UNIT],
               [{"Scavenger": {}}, 2 * UNIT], [{"BroodNurse": {}}, 2 * UNIT]],
        queenRoutine={"enabled": False, "targetWorkers": 3, "sugarReserve": 100 * UNIT},
        ages={"active": True, "workers": [{"expiresAt": 101, "count": 3}, {"expiresAt": 100000, "count": 8}]})
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] in ("QueensRoutine", "DefaultWorkers", "StandardWorkers"))

    def nest():
        page.get_by_role("tab", name="Nest", exact=True).click()

    def assigned(game, task):
        return next(n for (kind, n) in game["ants"]["tasks"] if task in kind)

    load_game(page, game)
    page.clock.run_for(200)
    aged = save_game(page)
    assert aged["ants"]["workers"] == 8 * UNIT
    assert assigned(aged, "SugarCollector") == 2 * UNIT
    for task in ("Tunneler", "Scavenger", "BroodNurse"):
        assert assigned(aged, task) == 2 * UNIT
    assert aged["ants"]["agingJobVacancies"] == []

    empty = copy.deepcopy(game)
    empty["ants"].update(workers=3 * UNIT,
        tasks=[[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, UNIT],
               [{"Scavenger": {}}, UNIT], [{"BroodNurse": {}}, UNIT]],
        ages={"active": True, "workers": [{"expiresAt": 101, "count": 1}, {"expiresAt": 100000, "count": 2}]})
    load_game(page, empty)
    page.clock.run_for(200)
    pending = save_game(page)
    assert assigned(pending, "BroodNurse") == 0
    assert pending["ants"]["agingJobVacancies"] == [[{"BroodNurse": {}}, UNIT]]
    page.reload(wait_until="networkidle")
    nest()
    expect(page.locator(".aging-job-replacements")).to_contain_text("Awaiting workers: 1 Brood Nurse")
    assert_fits(page, width)
    page.locator("#queen-routine-enabled").check()
    page.clock.run_for(125000)
    replaced = save_game(page)
    assert replaced["ants"]["workers"] == 3 * UNIT
    assert assigned(replaced, "BroodNurse") == UNIT
    assert assigned(replaced, "SugarCollector") == 0
    assert replaced["ants"]["agingJobVacancies"] == []
    nest()
    expect(page.locator(".aging-job-replacements")).to_have_count(0)
    assert_fits(page, width)

    load_game(page, pending)
    nest()
    page.get_by_role("button", name="Cancel replacements", exact=True).click()
    cancelled = save_game(page)
    assert cancelled["ants"]["agingJobVacancies"] == []
    cancelled["ants"]["workers"] += UNIT
    load_game(page, cancelled)
    assert assigned(save_game(page), "BroodNurse") == 0
    assert assigned(save_game(page), "SugarCollector") == UNIT

    # The shorter-lived small nurses are replaced by standard default workers.
    small = copy.deepcopy(game)
    small["ants"].update(workers=5 * UNIT, smallWorkers=4 * UNIT,
        smallTunnelers=UNIT, smallScavengers=UNIT, smallNurses=2 * UNIT,
        tasks=[[{"SugarCollector": {}}, 4 * UNIT], [{"Tunneler": {}}, UNIT],
               [{"Scavenger": {}}, 0], [{"BroodNurse": {}}, 0]],
        ages={"active": True, "workers": [{"expiresAt": 100000, "count": 5}],
               "smallWorkers": [{"expiresAt": 101, "count": 2}, {"expiresAt": 100000, "count": 2}]})
    load_game(page, small)
    page.clock.run_for(200)
    transitioned = save_game(page)
    assert transitioned["ants"]["smallNurses"] == 0
    assert assigned(transitioned, "BroodNurse") == 2 * UNIT
    assert assigned(transitioned, "SugarCollector") == 2 * UNIT
    assert transitioned["ants"]["agingJobVacancies"] == []
    nest()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: default pool preserves jobs, saved vacancies, Queen's Routine restores nurses, cancellation, small-to-standard replacement and layout", flush=True)


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
                    check_aging_jobs(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
