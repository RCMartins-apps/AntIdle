"""Fast founding workers, half-efficiency tasks and the staged replacement pipeline."""
import copy
import re
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, save_game


def check_brood(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    assert not initial["ants"]["standardWorkersUnlocked"]
    page.get_by_role("tab", name="Nest", exact=True).click()
    for _ in range(5):
        page.get_by_role("button", name="Gather Sugar", exact=True).click()
    raise_egg = page.get_by_role("button", name=re.compile(r"^Raise (?:Small )?Worker Egg"))
    expect(page.get_by_role("button", name="Raise Small Worker Egg", exact=True)).to_be_enabled()
    expect(page.locator(".brood-feeding")).to_contain_text("5 sugar")
    raise_egg.click()
    ordered = save_game(page)
    assert ordered["ants"]["eggOrders"] == 1 and ordered["ants"]["eggsAndLarvae"] == []
    page.clock.run_for(16200)
    born = save_game(page)
    assert born["ants"]["smallWorkers"] == UNIT
    assert born["ants"]["workers"] == 0
    assert born["ants"]["ages"]["smallWorkers"][0]["expiresAt"] > born["world"]["currentTick"] + 29 * 300
    page.locator("#tasks-tab").click()
    expect(page.locator(".small-worker-tasks")).to_contain_text("Small workers · 50%")
    expect(page.locator(".small-collector-summary")).to_contain_text("0.20 sugar/s")
    page.get_by_role("button", name="Assign small Tunneler", exact=True).click()
    page.clock.run_for(2000)
    dug = save_game(page)
    assert dug["basicResources"]["tunnelingSpace"]["amount"] == UNIT  # 0.5/s for two seconds.
    assert dug["ants"]["smallTunnelers"] == UNIT

    prepared = copy.deepcopy(initial)
    prepared["world"].update(currentTick=100, targetTick=100, saveSeed=123)
    prepared["ants"].update(workers=0, smallWorkers=6 * UNIT, smallTunnelers=0, smallScavengers=0, smallNurses=0,
        standardWorkersUnlocked=False, layingCooldown=0, eggOrders=0, sugarCumulativeDebt=0,
        tasks=[[{"SugarCollector": {}}, 0], [{"Tunneler": {}}, 0]],
        eggsAndLarvae=[{"Developing": {"initialTick": 100, "kind": "small", "care": 0}}],
        ages={"active": True, "smallWorkers": [{"expiresAt": 10000, "count": 6}]})
    prepared["nestAttributes"]["maxAnts"] = 20 * UNIT
    prepared["basicResources"].update(sugar={"amount": 100 * UNIT, "maxAmount": 1000 * UNIT},
        tunnelingSpace={"amount": 10 * UNIT, "maxAmount": 1000 * UNIT})
    load_game(page, prepared)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    research = page.get_by_role("button", name="Standard Worker Development", exact=True)
    expect(research).to_be_enabled()
    card = page.locator(".action-cell").filter(has=research)
    expect(card.locator(".action-costs")).to_contain_text("/75 sugar")
    expect(card.locator(".action-costs")).to_contain_text("/10 tunneling space")
    research.click()
    upgraded = save_game(page)
    assert upgraded["ants"]["standardWorkersUnlocked"]
    assert upgraded["ants"]["smallWorkers"] == 6 * UNIT
    assert upgraded["ants"]["ages"]["smallWorkers"] == prepared["ants"]["ages"]["smallWorkers"]
    assert upgraded["ants"]["eggsAndLarvae"] == prepared["ants"]["eggsAndLarvae"]
    page.get_by_role("tab", name="Nest", exact=True).click()
    expect(page.locator(".brood-feeding")).to_contain_text("20 sugar + 2 protein")
    expect(page.get_by_role("button", name="Raise Small Worker Egg", exact=True)).to_have_count(0)
    expect(page.get_by_role("button", name="Raise Worker Egg", exact=True)).to_be_enabled()
    raise_egg.click()
    page.clock.run_for(200)
    mixed = save_game(page)
    assert [next(iter(b.values()))["kind"] for b in mixed["ants"]["eggsAndLarvae"]] == ["small", "worker"]
    assert mixed["ants"]["layingCooldown"] > 0
    page.get_by_role("tab", name="Nest", exact=True).click()
    raise_egg.click()
    expect(page.locator(".brood-pipeline")).to_contain_text("Brood places full")
    page.locator("#tasks-tab").click()
    scraps = page.get_by_role("button", name="Assign small Scraps Gatherer", exact=True)
    scraps.click()
    scraps.click()
    expect(scraps).to_be_disabled()
    expect(page.get_by_role("button", name="Assign Scraps Gatherer", exact=True)).to_be_disabled()
    expect(page.locator(".small-worker-tasks")).to_contain_text("0.025 protein/s")
    assert_fits(page, width)

    feeding = copy.deepcopy(upgraded)
    feeding["ants"].update(eggOrders=0, layingCooldown=0, eggsAndLarvae=[{"Developing": {"initialTick": 100, "kind": "worker", "care": 100 * UNIT}}])
    feeding["basicResources"].update(sugar={"amount": 1000 * UNIT, "maxAmount": 1000 * UNIT},
        protein={"amount": 0, "maxAmount": 100 * UNIT})
    feeding["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    load_game(page, feeding)
    page.get_by_role("tab", name="Nest", exact=True).click()
    expect(page.locator(".brood-pipeline")).to_contain_text("Needs protein")
    expect(page.locator(".brood-pipeline")).to_have_class("brood-pipeline small mb-2 text-danger")
    page.clock.run_for(1000)
    hungry = save_game(page)
    assert hungry["ants"]["eggsAndLarvae"][0]["Developing"]["care"] == 100 * UNIT
    funded = copy.deepcopy(hungry)
    funded["basicResources"]["protein"]["amount"] = 10 * UNIT
    load_game(page, funded)
    page.clock.run_for(200)
    tended = save_game(page)
    assert tended["ants"]["eggsAndLarvae"][0]["Developing"]["care"] == 101 * UNIT
    assert funded["basicResources"]["protein"]["amount"] - tended["basicResources"]["protein"]["amount"] == 50
    page.locator("#tasks-tab").click()
    page.get_by_role("button", name="Assign small Brood Nurse", exact=True).click()
    nursed = save_game(page)
    load_game(page, nursed)
    page.clock.run_for(200)
    accelerated = save_game(page)
    assert accelerated["ants"]["eggsAndLarvae"][0]["Developing"]["care"] == 103 * UNIT
    assert accelerated["ants"]["smallNurses"] == UNIT
    page.get_by_role("tab", name="Nest", exact=True).click()
    assert_fits(page, width)
    page.locator(".nest-brood").screenshot(path=f"/tmp/antidle-brood-{width}.png")
    # Finish a standard pupa. Existing founding adults remain small; no caste conversion.
    pupa = copy.deepcopy(accelerated)
    pupa["ants"]["smallNurses"] = 0
    pupa["ants"]["eggsAndLarvae"][0]["Developing"]["care"] = 599 * UNIT
    load_game(page, pupa)
    page.clock.run_for(200)
    matured = save_game(page)
    assert matured["ants"]["workers"] == UNIT
    assert matured["ants"]["smallWorkers"] == 6 * UNIT
    assert matured["ants"]["eggsAndLarvae"] == []
    assert matured["ants"]["ages"]["workers"][0]["expiresAt"] >= matured["world"]["currentTick"] + 45 * 300
    page.reload(wait_until="networkidle")
    assert save_game(page)["ants"]["ages"] == matured["ants"]["ages"]
    # Legacy standard adults and already-laid legacy eggs retain their original type.
    legacy = copy.deepcopy(initial)
    for key in ("standardWorkersUnlocked", "smallWorkers", "smallTunnelers", "smallScavengers", "smallNurses", "eggOrders", "layingCooldown", "broodCursor"):
        legacy["ants"].pop(key, None)
    legacy["ants"].update(workers=UNIT, eggsAndLarvae=[{"Egg": {"initialTick": 0}}])
    load_game(page, legacy)
    assert save_game(page)["ants"]["standardWorkersUnlocked"]
    assert not errors, errors
    print(f"PASS {width}px: first small worker, half-speed tunnelling, research/type retention, queued laying, full brood beds, scraps/nurse caps, feeding pause/resume, standard maturity and saves", flush=True)


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
                    check_brood(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
