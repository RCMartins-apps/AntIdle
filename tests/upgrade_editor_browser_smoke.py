"""Exercise the offline editor against an isolated balance file and the real engine catalog."""
import json
from pathlib import Path
import shutil
import sys
from tempfile import TemporaryDirectory
from threading import Thread

from playwright.sync_api import expect, sync_playwright

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from upgrade_editor import EditorServer, balance_bundle  # noqa: E402


def check(browser, origin, tuning, output):
    context = browser.new_context(viewport={'width': 1440, 'height': 940})
    page = context.new_page()
    errors = []
    unexpected_requests = []
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.on('request', lambda request: unexpected_requests.append(request.url)
            if not request.url.startswith(origin + '/') else None)
    page.goto(origin)
    expect(page.locator('#record-title')).to_have_text('Standard Worker Development')
    expect(page.get_by_label('Sugar', exact=True)).to_have_value('75')
    expect(page.get_by_label('Tunneling Space', exact=True)).to_have_value('10')
    expect(page.locator('#upgrade .resource-icon')).to_have_count(7)
    expect(page.locator('.node[aria-pressed=true] .price .resource-sugar')).to_have_count(1)
    expect(page.locator('.node[aria-pressed=true] .price .resource-tunneling')).to_have_count(1)
    expect(page.locator('.node').first).to_be_visible()
    first_count = page.locator('.node').count()
    assert first_count >= 25
    if output:
        page.screenshot(path=str(output / 'upgrade-tree.png'))

    page.get_by_label('Find an upgrade').fill('Local Exploration')
    page.get_by_label('Find an upgrade').press('Enter')
    expect(page.locator('#record-title')).to_have_text('Local Exploration')
    page.get_by_label('Add prerequisite upgrade').select_option('ExpeditionSupplies1')
    expect(page.get_by_role('status')).to_contain_text('Dependency cycle')
    expect(page.get_by_role('button', name='Save changes')).to_be_disabled()
    page.get_by_role('button', name='Restore defaults').click()
    expect(page.get_by_role('status')).to_have_text('No unsaved changes')
    expect(page.get_by_label('Explore tab unlocked')).to_have_count(0)
    expect(page.get_by_label('Local Exploration available')).to_have_count(0)

    page.get_by_label('Find an upgrade').fill('Standard Worker Development')
    page.get_by_label('Find an upgrade').press('Enter')
    page.get_by_label('Sugar', exact=True).fill('82.25')
    page.get_by_label('Small workers', exact=True).fill('6')
    page.get_by_label('Early research access').check()
    expect(page.locator('.node[aria-pressed=true] .price')).to_contain_text('82.25 Sugar')

    stale = context.new_page()
    stale.goto(origin)
    expect(stale.get_by_label('Sugar', exact=True)).to_have_value('75')

    page.get_by_role('button', name='Save changes').click()
    expect(page.get_by_role('status')).to_contain_text('Saved to src/main/balance/upgrades.json')
    catalog = json.loads(tuning.read_text())
    assert len(catalog) == first_count
    assert catalog['StandardWorkers']['cost']['sugar'] == 822500
    assert catalog['StandardWorkers']['requirements']['minimumSmallWorkers'] == 6
    assert catalog['StandardWorkers']['requirements']['earlyResearch'] is True
    stale.get_by_label('Sugar', exact=True).fill('84')
    stale.get_by_role('button', name='Save changes').click()
    expect(stale.get_by_role('status')).to_contain_text('changed on disk')
    assert json.loads(tuning.read_text())['StandardWorkers']['cost']['sugar'] == 822500

    page.reload()
    expect(page.get_by_label('Sugar', exact=True)).to_have_value('82.25')
    expect(page.get_by_label('Early research access')).to_be_checked()
    page.get_by_label('Discovery', exact=True).select_option('resin-seep')
    standard = page.locator('[data-id="StandardWorkers"]')
    original_top = standard.evaluate('(node) => node.offsetTop')
    page.get_by_label('Add prerequisite upgrade').select_option('UnlockQueensChamber')
    assert standard.evaluate('(node) => node.offsetTop') == original_top
    page.get_by_role('button', name='Rearrange tree').click()
    assert standard.evaluate('(node) => node.offsetTop') > original_top
    expect(page.get_by_role('button', name="Remove prerequisite Unlock Queen's Chamber")).to_be_visible()
    page.get_by_role('button', name='Save changes').click()
    expect(page.get_by_role('status')).to_contain_text('Saved to src/main/balance/upgrades.json')
    assert json.loads(tuning.read_text())['StandardWorkers']['requirements']['discovery'] == 'resin-seep'
    assert json.loads(tuning.read_text())['StandardWorkers']['requirements']['upgrades'] == ['UnlockQueensChamber']
    page.get_by_label('Discovery', exact=True).select_option('')
    page.get_by_role('button', name='Revert edits').click()
    expect(page.get_by_label('Discovery', exact=True)).to_have_value('resin-seep')
    page.get_by_role('button', name='Restore defaults').click()
    page.get_by_role('button', name='Save changes').click()
    expect(page.get_by_role('status')).to_contain_text('Saved to src/main/balance/upgrades.json')
    assert json.loads(tuning.read_text())['StandardWorkers']['cost']['sugar'] == 750000

    page.get_by_label('Find an upgrade').fill('Foraging Techniques 1')
    page.get_by_label('Find an upgrade').press('Enter')
    expect(page.get_by_label('Bonus (%)')).to_have_value('15')
    page.get_by_label('Production source').select_option('scrapsGatherers')
    page.get_by_label('Bonus (%)').fill('5')
    page.get_by_role('button', name='Save changes').click()
    expect(page.get_by_role('status')).to_contain_text('Saved to src/main/balance/upgrades.json')
    assert json.loads(tuning.read_text())['ImproveSugarCollectorTask1']['bonuses'] == [
        {'type': 'productionPercent', 'source': 'scrapsGatherers', 'percent': 5}
    ]
    page.get_by_label('Add bonus', exact=True).select_option('productionPercent')
    expect(page.get_by_label('Production source')).to_have_count(2)
    page.get_by_label('Bonus (%)').nth(1).fill('10')
    page.get_by_role('button', name='Save changes').click()
    expect(page.get_by_role('status')).to_contain_text('Saved to src/main/balance/upgrades.json')
    assert len(json.loads(tuning.read_text())['ImproveSugarCollectorTask1']['bonuses']) == 2
    if output:
        page.locator('.inspector').screenshot(path=str(output / 'colony-workshop-bonus.png'))
    page.get_by_role('button', name='Remove bonus 2').click()
    expect(page.get_by_label('Production source')).to_have_count(1)
    page.get_by_role('button', name='Remove bonus 1').click()
    page.get_by_role('button', name='Save changes').click()
    expect(page.get_by_role('status')).to_contain_text('Saved to src/main/balance/upgrades.json')
    assert 'bonuses' not in json.loads(tuning.read_text())['ImproveSugarCollectorTask1']
    page.get_by_label('Add bonus', exact=True).select_option('slotCapacity')
    page.get_by_label('Slots added').fill('3')
    page.get_by_role('button', name='Save changes').click()
    expect(page.get_by_role('status')).to_contain_text('Saved to src/main/balance/upgrades.json')
    assert json.loads(tuning.read_text())['ImproveSugarCollectorTask1']['bonuses'] == [
        {'type': 'slotCapacity', 'slot': 'expedition', 'count': 3}
    ]
    page.reload()
    page.get_by_label('Find an upgrade').fill('Foraging Techniques 1')
    page.get_by_label('Find an upgrade').press('Enter')
    expect(page.get_by_label('Slots added')).to_have_value('3')
    assert not errors, errors
    assert not unexpected_requests, unexpected_requests
    context.close()

    phone = browser.new_page(viewport={'width': 390, 'height': 844}, is_mobile=True, has_touch=True)
    phone.goto(origin)
    expect(phone.get_by_label('Sugar', exact=True)).to_have_value('75')
    assert phone.evaluate('document.documentElement.scrollWidth') == 390
    phone.get_by_label('Upgrades per row').select_option('2')
    row_counts = phone.locator('.node').evaluate_all(
        '(nodes) => Object.values(nodes.reduce((rows, node) => { rows[node.offsetTop] = (rows[node.offsetTop] || 0) + 1; return rows; }, {}))'
    )
    assert max(row_counts) == 2
    phone.get_by_role('button', name='Fit', exact=True).click()
    if output:
        phone.locator('.workspace').screenshot(path=str(output / 'upgrade-layout-mobile.png'))
    phone.get_by_label('Find an upgrade').fill('Brood Care')
    phone.get_by_label('Find an upgrade').press('Enter')
    expect(phone.locator('#record-title')).to_have_text('Brood Care')
    expect(phone.get_by_role('button', name='Local Exploration', exact=True)).to_be_visible()
    expect(phone.get_by_label('Explore tab unlocked')).to_have_count(0)
    expect(phone.get_by_label('Local Exploration available')).to_have_count(0)
    if output:
        phone.locator('.inspector').screenshot(path=str(output / 'upgrade-inspector-mobile.png'))
        phone.get_by_label('Find an upgrade').fill('Foraging Techniques 1')
        phone.get_by_label('Find an upgrade').press('Enter')
        phone.locator('.bonus-list').screenshot(path=str(output / 'upgrade-bonuses-mobile.png'))
    phone.close()


def main():
    output = Path(sys.argv[1]) if len(sys.argv) == 2 else None
    if output:
        output.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory() as directory:
        tuning = Path(directory) / 'upgrades.json'
        chambers = Path(directory) / 'chambers.json'
        balance_dir = Path(__file__).resolve().parents[1] / 'src/main/balance'
        shutil.copyfile(balance_dir / 'upgrades.json', tuning)
        shutil.copyfile(balance_dir / 'chambers.json', chambers)
        with EditorServer(0, balance_bundle(), tuning, chambers) as server:
            Thread(target=server.serve_forever, daemon=True).start()
            try:
                with sync_playwright() as playwright:
                    chromium = shutil.which('chromium')
                    launch = {'executable_path': chromium} if chromium else {}
                    browser = playwright.chromium.launch(**launch)
                    try:
                        check(browser, server.origin, tuning, output)
                        page = browser.new_page(viewport={'width': 390, 'height': 844}, is_mobile=True, has_touch=True)
                        page.goto(server.origin + '/chambers')
                        page.locator('#chamber').select_option('Nursery')
                        protein = page.get_by_role('group', name='Protein', exact=True)
                        expect(protein.get_by_label('Base', exact=True)).to_have_value('3')
                        protein.get_by_label('Base', exact=True).fill('4.5')
                        page.get_by_role('button', name='Save changes').click()
                        expect(page.get_by_role('status')).to_contain_text('Saved chamber settings')
                        rule = json.loads(chambers.read_text())['Nursery']['costs']['protein']
                        assert rule == {'base': 45000, 'perLevel': 30000}
                        page.reload()
                        page.locator('#chamber').select_option('Nursery')
                        expect(protein.get_by_label('Base', exact=True)).to_have_value('4.5')
                        assert page.evaluate('document.documentElement.scrollWidth') == 390
                        page.locator('#chamber').select_option('FoodStorage')
                        capacity = page.locator('#capacity').get_by_label('Sugar', exact=True)
                        expect(capacity).to_have_value('1000')
                        capacity.fill('750')
                        page.get_by_role('button', name='Save changes').click()
                        expect(page.get_by_role('status')).to_contain_text('Saved chamber settings')
                        assert json.loads(chambers.read_text())['FoodStorage']['bonuses'] == [
                            {'type': 'storageCapacity', 'resource': 'sugar', 'amount': 7500000},
                            {'type': 'storageCapacity', 'resource': 'protein', 'amount': 1000000}
                        ]
                        page.reload()
                        page.locator('#chamber').select_option('FoodStorage')
                        expect(capacity).to_have_value('750')
                        if output:
                            page.locator('#capacity').screenshot(path=str(output / 'chamber-capacity.png'))
                        page.close()
                    finally:
                        browser.close()
            finally:
                server.shutdown()
    print('PASS offline upgrade editor: tree width and rearrangement, typed bonuses, pricing, gates, stale tabs and reload')


if __name__ == '__main__':
    main()
