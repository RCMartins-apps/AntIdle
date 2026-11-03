"""Exploration destinations, party rewards, milestone slots and supplies regressions."""

from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game
from progression_smoke import explore_tab, task_count


def check_destinations(page, width, url):
    now = datetime(2026, 11, 3, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 50 * UNIT
    game["ants"]["scouts"] = 2 * UNIT
    game["nestAttributes"]["maxAnts"] = 100 * UNIT
    game["basicResources"]["sugar"] = {"amount": 10000 * UNIT, "maxAmount": 10000 * UNIT}
    game["basicResources"]["knowledge"] = {"amount": 1000 * UNIT, "maxAmount": 10000 * UNIT}
    game["exploration"] = {"experience": 299}

    def choose_workers(amount):
        page.locator("#explorer-count").fill(str(amount))

    def route(name):
        return page.locator(".exploration-destination").filter(
            has=page.get_by_role("button", name="Explore " + name, exact=True))

    load_game(page, game)
    explore_tab(page)
    expect(route("Rotten Log").get_by_role("button")).to_be_disabled()
    expect(route("Rotten Log")).to_contain_text("Level 3")
    expect(page.locator(".exploration-milestone")).to_contain_text("Level 3: Rotten Log")
    # Existing XP is retained; only the derived level/threshold changes.
    assert save_game(page)["exploration"]["experience"] == 299
    explore_tab(page)
    choose_workers(5)
    expect(route("Woodland Edge").locator(".destination-preview")).to_contain_text("60 XP")
    expect(route("Rotten Log")).to_contain_text("120 XP")
    assert_fits(page, width)

    # Newly unlocked XP/Knowledge route, with a smaller party blocked.
    game["exploration"] = {"experience": 300}
    load_game(page, game)
    explore_tab(page)
    expect(route("Rotten Log").get_by_role("button")).to_be_disabled()
    choose_workers(5)
    expect(route("Rotten Log").get_by_role("button")).to_be_enabled()
    expect(route("Rotten Log")).to_contain_text("60 knowledge")
    route("Rotten Log").get_by_role("button").click()
    sent = save_game(page)
    assert sent["exploration"]["experience"] == 300
    assert sent["exploration"]["explorationParties"][0]["destination"] == "rotten-log"
    assert task_count(sent, "Explorer") == 5 * UNIT
    page.reload(wait_until="networkidle")
    page.clock.run_for(60000)
    returned = save_game(page)
    assert returned["exploration"]["experience"] == 420
    # Knowledge is granted only on expedition completion.
    assert any("60 knowledge" in message for message in returned["messages"])
    assert not returned["exploration"]["discoveredCarcasses"]
    assert not returned["exploration"]["explorationParties"]
    assert task_count(returned, "Explorer") == 0

    # Supplies improve only departures made after purchasing them.
    game["exploration"] = {"experience": 300}
    load_game(page, game)
    explore_tab(page)
    page.get_by_text("Expedition supplies", exact=True).click()
    before = save_game(page)
    explore_tab(page)
    page.get_by_role("button", name="Improve Expedition Supplies", exact=True).click()
    improved = save_game(page)
    assert improved["exploration"]["suppliesLevel"] == 1
    assert improved["basicResources"]["sugar"]["amount"] == before["basicResources"]["sugar"]["amount"] - 500 * UNIT
    explore_tab(page)
    choose_workers(5)
    expect(route("Rotten Log")).to_contain_text("144 XP")
    expect(route("Rotten Log")).to_contain_text("72 knowledge")
    page.reload(wait_until="networkidle")
    assert save_game(page)["exploration"]["suppliesLevel"] == 1

    # Level 5 unlocks a second slot, and both scouts are reserved independently.
    game["exploration"] = {"experience": 1000}
    load_game(page, game)
    explore_tab(page)
    choose_workers(5)
    route("Rotten Log").get_by_role("button").click()
    route("Woodland Edge").get_by_role("button").click()
    both = save_game(page)
    assert len(both["exploration"]["explorationParties"]) == 2
    assert task_count(both, "Explorer") == 10 * UNIT
    explore_tab(page)
    expect(route("Surroundings").get_by_role("button")).to_be_disabled()
    page.clock.run_for(45000)
    first = save_game(page)
    assert len(first["exploration"]["explorationParties"]) == 1
    assert task_count(first, "Explorer") == 5 * UNIT
    explore_tab(page)
    expect(route("Surroundings").get_by_role("button")).to_be_enabled()
    page.clock.run_for(15000)
    assert task_count(save_game(page), "Explorer") == 0
    explore_tab(page)
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: destination gates, preview scaling, rewards, supplies, save reload, two parties, slot release, layout")


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
                    check_destinations(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
