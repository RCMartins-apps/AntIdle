"""Visible raid risk, oldest-worker departure, saved outcome and combat returns."""
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


def check_raid(page, width, url):
    now = datetime(2026, 11, 7, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["world"]["currentTick"] = game["world"]["targetTick"] = 100
    game["ants"].update(workers=40 * UNIT, scouts=3 * UNIT, repletes=0, daughterQueens=0, eggsAndLarvae=[], royalBrood=None,
        tasks=[[{"SugarCollector": {}}, 40 * UNIT], [{"Explorer": {}}, 0]], defaultTask={"SugarCollector": {}},
        ages={"active": True, "workers": [{"expiresAt": 5000, "count": 15}, {"expiresAt": 10000, "count": 25}],
            "scouts": [{"expiresAt": 10000, "count": 3}]})
    game["nestAttributes"].update(maxAnts=100 * UNIT, maxEggs=5 * UNIT)
    game["nestAttributes"]["chambers"]["nurseryChamber"]["level"] = 1
    game["nestAttributes"]["chambers"]["foodStorageChamber"]["level"] = 19
    game["basicResources"].update(sugar={"amount": 20000 * UNIT, "maxAmount": 30000 * UNIT},
        protein={"amount": 0, "maxAmount": 2000 * UNIT})
    game["exploration"] = {"experience": 999}
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade.update(show=False, unlocked=upgrade["name"] in ("DefaultWorkers", "ProteinStorage"))
    card = page.locator('.exploration-destination[data-destination="rival-food-cache"]')
    raid = page.get_by_role("button", name="Raid Rival Food Cache", exact=True)
    load_game(page, game)
    explore_tab(page)
    select_expedition(page, "rival-food-cache")
    expect(card).to_have_count(0)
    game["exploration"]["experience"] = 1000
    load_game(page, game)
    explore_tab(page)
    select_expedition(page, "rival-food-cache")
    expect(card).to_be_visible()
    page.locator("#explorer-count").fill("5")
    expect(raid).to_be_disabled()
    expect(card.locator(".raid-risk")).to_contain_text("10 workers minimum")
    page.locator("#explorer-count").fill("10")
    expect(card.locator(".raid-risk")).to_contain_text("2–4 of 10 workers (certain)")
    expect(card.locator(".raid-risk")).to_contain_text("Scouts: no combat losses")
    expect(card.locator(".raid-risk")).to_have_class("raid-risk text-danger small mb-2")
    expect(card.locator(".destination-preview")).to_contain_text("200–350 protein")
    expect(card.locator(".action-costs")).to_contain_text("/300 sugar")
    expect(card.locator(".action-costs")).to_contain_text("/1 scouts")
    expect(raid).to_be_enabled()
    page.locator("#explorer-count").fill("37")
    expect(card.locator(".raid-risk")).to_contain_text("4–10 of 37 workers")
    expect(card.locator(".destination-preview")).to_contain_text("740–1295 protein")
    expect(card.locator(".action-costs")).to_contain_text("/1110 sugar")
    assert_fits(page, width)
    page.locator("#explorer-count").fill("10")
    raid.click()
    sent = save_game(page)
    party = sent["exploration"]["explorationParties"][0]
    outcome = party["raidOutcome"]
    assert 2 <= outcome["workerLosses"] <= 4
    assert 200 * UNIT <= outcome["protein"] <= 350 * UNIT
    assert party["ages"]["workers"] == [{"expiresAt": 5000, "count": 10}]
    assert sent["ants"]["ages"]["workers"] == [{"expiresAt": 5000, "count": 5}, {"expiresAt": 10000, "count": 25}]
    assert sent["ants"]["workers"] == 40 * UNIT
    assert game["basicResources"]["sugar"]["amount"] - sent["basicResources"]["sugar"]["amount"] == 300 * UNIT
    page.reload(wait_until="networkidle")
    restored = save_game(page)
    assert restored["exploration"]["explorationParties"][0]["raidOutcome"] == outcome
    # Bring the saved party to its return tick without simulating all two minutes;
    # engine coverage separately exercises its unmodified target tick.
    returning = copy.deepcopy(restored)
    returning["world"]["currentTick"] = returning["world"]["targetTick"] = party["targetTick"] - 1
    load_game(page, returning)
    page.clock.run_for(200)
    result = save_game(page)
    losses = outcome["workerLosses"]
    assert result["ants"]["workers"] == (40 - losses) * UNIT
    assert result["ants"]["scouts"] == 3 * UNIT
    assert result["exploration"]["explorationParties"] == []
    assert result["statistics"]["raidDeaths"]["thisRun"] == losses
    assert result["statistics"]["workersLostToAging"]["thisRun"] == 0
    assert result["basicResources"]["protein"]["amount"] == outcome["protein"]
    assert result["statistics"]["proteinU"]["thisRun"] == outcome["protein"]
    assert sum(c["count"] for c in result["ants"]["ages"]["workers"]) == 40 - losses
    assert next(n for t, n in result["ants"]["tasks"] if "SugarCollector" in t) == (40 - losses) * UNIT
    page.reload(wait_until="networkidle")
    page.clock.run_for(200)
    assert save_game(page)["statistics"]["raidDeaths"]["thisRun"] == losses

    cramped = copy.deepcopy(game)
    cramped["basicResources"]["protein"]["amount"] = 1990 * UNIT
    cramped["ants"]["ages"]["workers"] = [{"expiresAt": 200, "count": 10}, {"expiresAt": 10000, "count": 30}]
    cramped["ants"]["ages"]["scouts"] = [{"expiresAt": 200, "count": 1}, {"expiresAt": 10000, "count": 2}]
    load_game(page, cramped)
    explore_tab(page)
    select_expedition(page, "rival-food-cache")
    page.locator("#explorer-count").fill("10")
    expect(card.locator(".raid-storage")).to_contain_text("10")
    expect(card.locator(".raid-storage")).to_have_class("raid-storage small mb-1 text-danger")
    expect(card.locator(".raid-aging")).to_contain_text("10 workers")
    expect(card.locator(".raid-aging")).to_contain_text("1 scout")
    assert_fits(page, width)
    card.screenshot(path=f"/tmp/antidle-raid-{width}.png")
    # Casualties are an ordinary population deficit: Queen's Routine already
    # handles replacements, without a new automation unlock.
    automated = copy.deepcopy(returning)
    automated["ants"]["queenRoutine"] = {"enabled": True, "targetWorkers": 40, "sugarReserve": 0}
    for upgrade in automated["upgrades"]["upgrades"]:
        if upgrade["name"] == "QueensRoutine":
            upgrade["unlocked"] = True
    load_game(page, automated)
    page.clock.run_for(200)
    replaced = save_game(page)
    assert len(replaced["ants"]["eggsAndLarvae"]) == 1
    assert replaced["ants"]["layingCooldown"] > 0
    assert replaced["statistics"]["raidDeaths"]["thisRun"] == losses
    old = copy.deepcopy(game)
    old["statistics"].pop("raidDeaths", None)
    load_game(page, old)
    assert save_game(page)["statistics"]["raidDeaths"]["thisRun"] == 0
    assert not errors, errors
    print(f"PASS {width}px: hidden gate, visible risk/reward/costs, oldest workers, fixed saved outcome, combat returns, age/storage warnings, automatic replacements and layout", flush=True)


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
                    check_raid(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
