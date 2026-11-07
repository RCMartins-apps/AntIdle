"""Optional territorial discovery, recurring losses, hauls and withdrawal."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game, select_expedition
from progression_smoke import explore_tab


def check_territory(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["world"].update(currentTick=100, targetTick=100, saveSeed=123)
    game["ants"].update(workers=40 * UNIT, scouts=3 * UNIT, repletes=0, daughterQueens=0, eggsAndLarvae=[], royalBrood=None,
        tasks=[[{"SugarCollector": {}}, 40 * UNIT], [{"Explorer": {}}, 0]], defaultTask={"SugarCollector": {}},
        queenRoutine={"enabled": True, "targetWorkers": 40, "sugarReserve": 0},
        ages={"active": True, "workers": [{"expiresAt": 5000, "count": 15}, {"expiresAt": 10000, "count": 25}],
            "scouts": [{"expiresAt": 10000, "count": 3}]})
    game["nestAttributes"].update(maxAnts=100 * UNIT, maxEggs=5 * UNIT)
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["nestAttributes"]["chambers"]["foodStorageChamber"]["level"] = 19
    game["basicResources"].update(sugar={"amount": 20000 * UNIT, "maxAmount": 30000 * UNIT},
        protein={"amount": 0, "maxAmount": 2000 * UNIT})
    game["exploration"] = {"experience": 1000}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] in ("DefaultWorkers", "ProteinStorage", "QueensRoutine"))
    tab = page.get_by_role("tab", name="Territory", exact=True)
    load_game(page, game)
    expect(tab).to_have_count(0)
    explore_tab(page)
    select_expedition(page, "rival-food-cache")
    page.locator("#explorer-count").fill("10")
    page.get_by_role("button", name="Raid Rival Food Cache", exact=True).click()
    sent = save_game(page)
    sent["world"]["currentTick"] = sent["world"]["targetTick"] = sent["exploration"]["explorationParties"][0]["targetTick"] - 1
    load_game(page, sent)
    page.clock.run_for(200)
    expect(tab).to_be_visible()
    returned = save_game(page)
    assert returned["exploration"]["surveys"]["rival-food-cache"] == 1
    # Resume with a replenished colony, to isolate territory costs and losses.
    game["exploration"]["surveys"] = {"rival-food-cache": 1}
    load_game(page, game)
    tab.click()
    hold = page.get_by_role("button", name="Hold Foraging Ground", exact=True)
    expect(hold).to_be_enabled()
    expect(page.locator(".territory-risk")).to_contain_text("2–4 of 10 workers")
    expect(page.locator(".territory-card .action-costs")).to_contain_text("/200 sugar")
    expect(page.locator(".territory-card .action-costs")).to_contain_text("/10 Sugar Collector workers")
    page.get_by_role("button", name="Hold with 37 workers", exact=True).click()
    expect(page.locator(".territory-risk")).to_contain_text("4–10 of 37 workers")
    expect(page.locator(".territory-card .action-costs")).to_contain_text("/740 sugar")
    page.get_by_role("button", name="Hold with 10 workers", exact=True).click()
    assert_fits(page, width)
    hold.click()
    expect(page.locator(".territory-held")).to_contain_text("10/10 workers holding")
    auto = page.get_by_label("Maintain staffing automatically", exact=True)
    expect(auto).not_to_be_checked()
    auto.evaluate("e => e.dataset.testIdentity = 'retained'")
    started = save_game(page)
    assert game["basicResources"]["sugar"]["amount"] - started["basicResources"]["sugar"]["amount"] == 200 * UNIT
    assert started["exploration"]["territory"]["ages"] == [{"expiresAt": 5000, "count": 10}]
    assert started["ants"]["ages"]["workers"] == [{"expiresAt": 5000, "count": 5}, {"expiresAt": 10000, "count": 25}]
    assert started["ants"]["workers"] == 40 * UNIT
    tab.click()
    page.clock.run_for(59000)
    expect(auto).to_have_attribute("data-test-identity", "retained")
    pending = save_game(page)
    assert pending["basicResources"]["protein"]["amount"] == 0
    assert pending["statistics"]["territoryContests"]["thisRun"] == 0
    assert pending["exploration"]["territory"]["pendingProtein"] == 177 * UNIT
    page.reload(wait_until="networkidle")
    tab.click()
    page.clock.run_for(1000)
    result = save_game(page)
    losses = result["statistics"]["territoryDeaths"]["thisRun"]
    assert 2 <= losses <= 4
    assert result["statistics"]["territoryContests"]["thisRun"] == 1
    assert result["basicResources"]["protein"]["amount"] == 180 * UNIT
    assert result["statistics"]["proteinU"]["thisRun"] == 180 * UNIT
    assert result["ants"]["workers"] == (40 - losses) * UNIT
    assert len(result["ants"]["eggsAndLarvae"]) == 1
    assert result["exploration"]["territory"]["round"] == 1
    assert result["exploration"]["territory"]["seed"] == started["exploration"]["territory"]["seed"]
    tab.click()
    expect(page.locator(".territory-reinforcements")).to_contain_text(f"/{losses} Sugar Collector workers")
    auto.check()
    page.clock.run_for(200)
    expect(page.locator(".territory-held")).to_contain_text("10/10 workers holding")
    maintained = save_game(page)
    assert maintained["exploration"]["territory"]["maintain"]
    assert maintained["ants"]["workers"] == (40 - losses) * UNIT
    page.clock.run_for(10000)
    replenished = save_game(page)
    assert replenished["ants"]["workers"] == (40 - losses) * UNIT
    assert len(replenished["ants"]["eggsAndLarvae"]) == 1
    tab.click()
    assert_fits(page, width)
    page.locator(".territory-card").screenshot(path=f"/tmp/antidle-territory-{width}.png")
    page.get_by_role("button", name="Withdraw", exact=True).click()
    withdrawn = save_game(page)
    assert not withdrawn["exploration"]["territory"]["held"]
    assert withdrawn["exploration"]["territory"]["pendingProtein"] == 0
    assert withdrawn["basicResources"]["protein"]["amount"] == 180 * UNIT
    assert withdrawn["ants"]["workers"] == (40 - losses) * UNIT
    assert next(n for t, n in withdrawn["ants"]["tasks"] if "SugarCollector" in t) == (40 - losses) * UNIT
    assert next(c["count"] for c in withdrawn["ants"]["ages"]["workers"] if c["expiresAt"] == 5000) == 15 - losses
    page.clock.run_for(60000)
    assert save_game(page)["statistics"]["territoryContests"]["thisRun"] == 1

    # Full stores never turn a hold into a casualty-free farm.
    full = copy.deepcopy(result)
    full["basicResources"]["protein"]["amount"] = 2000 * UNIT
    full["exploration"]["territory"]["pendingProtein"] = 100 * UNIT
    full["exploration"]["territory"]["nextContestTick"] = full["world"]["currentTick"] + 1
    load_game(page, full)
    tab.click()
    expect(page.locator(".territory-storage")).to_have_class("territory-storage small my-1 text-danger")
    page.clock.run_for(200)
    fought = save_game(page)
    assert fought["statistics"]["territoryDeaths"]["thisRun"] > losses
    assert fought["statistics"]["territoryContests"]["thisRun"] == 2
    assert fought["basicResources"]["protein"]["amount"] == 2000 * UNIT
    assert fought["statistics"]["proteinU"]["thisRun"] == 180 * UNIT
    # A crew whose surviving members expire loses the ground and unfinished haul.
    old = copy.deepcopy(result)
    holders = next(n for t, n in old["ants"]["tasks"] if "TerritoryForager" in t) // UNIT
    old["exploration"]["territory"].update(ages=[{"expiresAt": old["world"]["currentTick"] + 1, "count": holders}],
        nextContestTick=old["world"]["currentTick"] + 1, pendingProtein=100 * UNIT)
    load_game(page, old)
    tab.click()
    expect(page.locator(".territory-aging")).to_contain_text(f"{holders} holders expire")
    page.clock.run_for(200)
    lost = save_game(page)
    assert not lost["exploration"]["territory"]["held"]
    assert lost["basicResources"]["protein"]["amount"] == 180 * UNIT
    assert lost["statistics"]["workersLostToAging"]["thisRun"] > 0
    legacy = copy.deepcopy(game)
    legacy["exploration"].pop("territory", None)
    for field in ("territoryDeaths", "territoryContests", "territoriesClaimed"):
        legacy["statistics"].pop(field, None)
    legacy["exploration"]["surveys"] = {}
    legacy["statistics"]["raidDeaths"] = {"thisRun": 3, "total": 3}
    load_game(page, legacy)
    expect(tab).to_be_visible()
    recovered = save_game(page)
    assert not recovered["exploration"]["territory"]["held"]
    assert recovered["exploration"]["surveys"]["rival-food-cache"] == 1
    assert not errors, errors
    print(f"PASS {width}px: discovery/tab, costs/risk, delayed haul, recurring combat, saved countdown, replacements/reinforcement, withdrawal, full stores, old ages and layout", flush=True)


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
                    check_territory(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
