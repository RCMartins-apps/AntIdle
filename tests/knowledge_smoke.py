"""Knowledge economy, sugar-only exploration unlocks and current save restoration."""

from datetime import datetime, timedelta, timezone
from functools import partial
from http.server import ThreadingHTTPServer
import os
import shutil
from threading import Thread

from playwright.sync_api import expect, sync_playwright
from browser_smoke import ROOT, UNIT, QuietHandler, assert_fits, load_game, progressed_game, save_game
from progression_smoke import explore_tab


def check_knowledge(page, width, url):
    now = datetime(2026, 11, 3, tzinfo=timezone.utc)
    page.clock.install(time=now)
    page.clock.pause_at(now + timedelta(milliseconds=100))
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    page.goto(url, wait_until="networkidle")
    game = progressed_game(save_game(page))
    game["ants"]["workers"] = 20 * UNIT
    game["ants"]["tasks"] = [[{"SugarCollector": {}}, 10 * UNIT],
                              [{"Tunneler": {}}, 5 * UNIT], [{"Explorer": {}}, 0]]
    game["nestAttributes"]["chambers"]["nestChamber"]["level"] = 1
    game["basicResources"]["sugar"] = {"amount": 3000 * UNIT, "maxAmount": 3000 * UNIT}
    game["basicResources"]["knowledge"] = {"amount": 0, "maxAmount": 9223372036854775807}
    game["unlocks"]["tabs"]["upgradesTabUnlocked"] = False
    game["unlocks"]["resources"]["showKnowledge"] = False
    for upgrade in game["upgrades"]["upgrades"]:
        upgrade["show"] = False
        upgrade["unlocked"] = False
    load_game(page, game)
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    for title in ("Unlock Queen's Chamber", "Foraging Techniques 1", "Unlock Food Storage Chamber", "Unlock Explorer Task"):
        button = page.get_by_role("button", name=title, exact=False)
        expect(button).to_be_enabled()
        button.click()
    unlocked = save_game(page)
    assert unlocked["basicResources"]["knowledge"]["amount"] == 0
    assert unlocked["basicResources"]["sugar"]["amount"] == 1600 * UNIT
    page.get_by_role("tab", name="Upgrades", exact=True).click()
    expect(page.get_by_role("button", name="Foraging Techniques 2", exact=False)).to_be_disabled()
    explore_tab(page)
    page.locator("#explorer-count").fill("5")
    page.get_by_role("button", name="Explore Surroundings", exact=True).click()
    page.clock.run_for(15000)
    earned = save_game(page)
    assert earned["basicResources"]["knowledge"]["amount"] == 4 * UNIT
    row = page.locator(".resource-row").filter(has=page.get_by_text("Knowledge", exact=True))
    expect(row).to_be_visible()
    expect(row.locator(".col-2")).to_have_text("")
    expect(row.locator(".resource-amount.text-primary")).to_have_count(0)
    page.clock.run_for(5000)
    assert save_game(page)["basicResources"]["knowledge"]["amount"] == 4 * UNIT
    assert_fits(page, width)

    # Current saves retain uncapped Knowledge and exact stored capacities.
    current = save_game(page)
    current["basicResources"]["knowledge"] = {"amount": 5000 * UNIT, "maxAmount": 9223372036854775807}
    current["basicResources"]["tunnelingSpace"] = {"amount": 100 * UNIT, "maxAmount": 2500 * UNIT}
    load_game(page, current)
    restored = save_game(page)
    assert restored["version"] == 6
    assert restored["basicResources"]["knowledge"]["amount"] == 5000 * UNIT
    assert restored["basicResources"]["tunnelingSpace"]["maxAmount"] == 2500 * UNIT
    page.reload(wait_until="networkidle")
    again = save_game(page)
    assert again["basicResources"] == restored["basicResources"]
    assert "Colony Points" not in page.locator("body").inner_text()
    assert_fits(page, width)
    assert not errors, errors
    print(f"PASS {width}px: sugar-only unlock chain, knowledge income, no cap or passive income, save restoration, Nest capacity, layout")


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
                for width in (390, 1920):
                    context = browser.new_context(viewport={"width": width, "height": 844})
                    check_knowledge(context.new_page(), width, f"http://127.0.0.1:{server.server_port}")
                    context.close()
            finally:
                browser.close()
    finally:
        server.shutdown()
        server.server_close()
