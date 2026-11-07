"""First founding, inherited production, Shared Paths and finite nectar trails."""
import copy
from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread
from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game
from progression_smoke import explore_tab


def check_prestige(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    initial = save_game(page)
    game = progressed_game(initial)
    game["world"]["currentTick"] = game["world"]["targetTick"] = 100
    game["ants"].update(workers=20 * UNIT, scouts=2 * UNIT, daughterQueens=UNIT,
        lineage={"foundings": 0, "dna": 0, "sharedPaths": False},
        tasks=[[{"SugarCollector": {}}, 20 * UNIT], [{"Explorer": {}}, 0]],
        defaultTask={"SugarCollector": {}}, royalBrood=None, eggsAndLarvae=[])
    game["statistics"]["sugarU"] = {"thisRun": 100 * UNIT, "total": 300 * UNIT}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] == "DefaultWorkers")
    load_game(page, game)

    def nest():
        page.get_by_role("tab", name="Nest", exact=True).click()

    nest()
    expect(page.locator(".lineage-section")).to_contain_text("Generation 1 · DNA: 0 · +0% sugar production")
    page.get_by_text("Review founding", exact=True).click()
    expect(page.locator(".founding-preview")).to_contain_text("Keep: DNA, Understandings, lifetime statistics")
    page.once("dialog", lambda dialog: dialog.dismiss())
    page.get_by_role("button", name="Found daughter colony", exact=True).click()
    assert save_game(page)["ants"]["daughterQueens"] == UNIT
    nest()
    page.once("dialog", lambda dialog: dialog.accept())
    page.get_by_role("button", name="Found daughter colony", exact=True).click()
    page.clock.run_for(1)
    expect(page.locator(".lineage-section")).to_contain_text("Generation 2 · DNA: 1 · +10% sugar production")
    expect(page.get_by_role("button", name="Found daughter colony", exact=True)).to_have_count(0)
    founded = save_game(page)
    assert founded["ants"]["workers"] == 0 and founded["ants"]["daughterQueens"] == 0
    assert founded["basicResources"]["sugar"]["amount"] == 0
    assert founded["statistics"]["sugarU"] == {"thisRun": 0, "total": 300 * UNIT}
    assert founded["exploration"]["experience"] == 0
    assert not any(u["unlocked"] for u in founded["upgrades"]["upgrades"])
    nest()
    page.get_by_role("button", name="Learn Shared Paths", exact=True).click()
    expect(page.locator(".lineage-section")).to_contain_text("DNA: 0")
    expect(page.get_by_role("button", name="Learn Shared Paths", exact=True)).to_have_count(0)
    assert_fits(page, width)
    learned = save_game(page)
    page.reload(wait_until="networkidle")
    assert save_game(page)["ants"]["lineage"] == learned["ants"]["lineage"]
    # Resetting the colony must recreate early-game reactive unlock subscriptions.
    nest()
    for _ in range(30):
        page.get_by_role("button", name="Gather Sugar", exact=True).click()
    expect(page.get_by_role("button", name="Raise Worker Egg", exact=True)).to_be_enabled()
    page.get_by_role("button", name="Raise Worker Egg", exact=True).click()
    page.clock.run_for(60000)
    expect(page.locator("#tasks-tab")).to_be_visible()
    page.locator("#tasks-tab").click()
    expect(page.locator(".small-worker-tasks")).to_contain_text("Small workers · 50%")
    expect(page.locator(".small-collector-summary")).to_contain_text("0.22 sugar/s")

    if width == 1920:
        sugar_row = page.locator(".resource-row").filter(has=page.get_by_text("Sugar", exact=True))
        sugar_row.locator(":scope > div").last.hover()
        expect(page.locator(".game-tooltip")).to_contain_text("+10.00%")
        page.get_by_role("tab", name="Nest", exact=True).hover()

    # A later colony state exercises the new exploration loop without replaying
    # all pre-existing research and surveys in each viewport.
    later = copy.deepcopy(game)
    later["ants"].update(daughterQueens=0, lineage={"foundings": 1, "dna": 0, "sharedPaths": True})
    later["exploration"] = {"experience": 600, "sugarTrail": {"remaining": 0, "marked": False}}
    later["basicResources"]["sugar"] = {"amount": 500 * UNIT, "maxAmount": 1000 * UNIT}
    later["ants"]["ages"] = {"active": True, "workers": [{"expiresAt": 18000, "count": 20}],
        "scouts": [{"expiresAt": 18000, "count": 2}]}
    load_game(page, later)
    explore_tab(page)
    expect(page.locator(".pheromone-trails")).to_contain_text("No nectar source")
    page.get_by_label("About Pheromone trail", exact=True).click()
    expect(page.locator(".pheromone-trails .info-content")).to_contain_text("Nectar Meadow")
    page.get_by_label("About Pheromone trail", exact=True).click()
    route = page.locator('.exploration-destination[data-destination="nectar-meadow"]')
    page.locator("#explorer-count").fill("5")
    route.get_by_role("button").first.click()
    page.clock.run_for(120000)
    explore_tab(page)
    expect(page.get_by_role("button", name="Mark trail", exact=True)).to_be_enabled()
    source = save_game(page)
    assert source["exploration"]["sugarTrail"]["remaining"] == 1200 * UNIT
    explore_tab(page)
    page.get_by_role("button", name="Mark trail", exact=True).click()
    marked = save_game(page)
    assert source["basicResources"]["sugar"]["amount"] - marked["basicResources"]["sugar"]["amount"] == 50 * UNIT
    explore_tab(page)
    expect(page.locator(".pheromone-trails")).to_contain_text("1 scout guiding")
    plus = page.get_by_role("button", name="Assign trail worker", exact=True)
    for _ in range(10):
        plus.click()
    expect(plus).to_be_disabled()
    expect(page.locator(".pheromone-trails")).to_contain_text("10/10 workers")
    # The expedition reward can fill storage; make room to test real harvest.
    assigned = save_game(page)
    assigned["basicResources"]["sugar"]["amount"] = 100 * UNIT
    load_game(page, assigned)
    explore_tab(page)
    expect(page.locator(".trail-production")).to_have_text("+8.80 sugar/s")
    assert_fits(page, width)
    page.locator(".pheromone-trails").screenshot(path=f"/tmp/antidle-trail-{width}.png")
    page.clock.run_for(1000)
    harvested = save_game(page)
    assert assigned["exploration"]["sugarTrail"]["remaining"] - harvested["exploration"]["sugarTrail"]["remaining"] == 88000
    assert next(count for task, count in harvested["ants"]["tasks"] if "TrailForager" in task) == 10 * UNIT
    page.reload(wait_until="networkidle")
    explore_tab(page)
    expect(page.locator(".pheromone-trails")).to_contain_text("10/10 workers")
    page.get_by_role("button", name="Recall trail", exact=True).click()
    recalled = save_game(page)
    assert not recalled["exploration"]["sugarTrail"]["marked"]
    assert recalled["exploration"]["sugarTrail"]["remaining"] == harvested["exploration"]["sugarTrail"]["remaining"]
    assert next(count for task, count in recalled["ants"]["tasks"] if "TrailForager" in task) == 0
    depleted = copy.deepcopy(harvested)
    depleted["exploration"]["sugarTrail"]["remaining"] = 10
    load_game(page, depleted)
    page.clock.run_for(200)
    ended = save_game(page)
    assert ended["exploration"]["sugarTrail"]["remaining"] == 0
    assert not ended["exploration"]["sugarTrail"]["marked"]
    assert next(count for task, count in ended["ants"]["tasks"] if "SugarCollector" in task) == 20 * UNIT
    old = copy.deepcopy(game)
    old["ants"].pop("lineage", None)
    old["exploration"].pop("sugarTrail", None)
    load_game(page, old)
    restored = save_game(page)
    assert restored["ants"]["lineage"] == {"foundings": 0, "dna": 0, "sharedPaths": False}
    assert not errors, errors
    print(f"PASS {width}px: founding/cancel, reset preview, DNA, inheritance, early unlocks after reset, trail discovery/mark/staff/harvest/recall/depletion and saves", flush=True)


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
                    check_prestige(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
