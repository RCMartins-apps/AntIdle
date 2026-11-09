"""Portable report packaging: valid imports, safe labels, baseline preservation."""
import base64
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('balance', ROOT / 'scripts/balance.py')
balance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(balance)


class BalanceReportTests(unittest.TestCase):
    def test_packaging_preserves_exact_seed_and_generates_import_and_safe_html(self):
        with tempfile.TemporaryDirectory() as temp:
            out = Path(temp)
            save = out / 'checkpoints' / 'demo' / 'final.json'
            save.parent.mkdir(parents=True)
            original = dict(world=dict(currentTick=123, targetTick=999, saveSeed=9223372036854775806, lastUpdateTime=0))
            save.write_text(json.dumps(original))
            report = dict(schemaVersion=1, ticksPerSecond=5, runs=[], label='</script><script>alert(1)</script>')
            (out / 'report.json').write_text(json.dumps(report))
            baseline = out / 'baseline.json'
            baseline.write_text(json.dumps(report))
            packaged = balance.package_report(out, baseline)
            loaded = json.loads(base64.b64decode(save.with_suffix('.save.txt').read_text()))
            self.assertEqual(loaded['world']['saveSeed'], original['world']['saveSeed'])
            self.assertEqual(loaded['world']['targetTick'], 123)
            self.assertGreater(loaded['world']['lastUpdateTime'], 0)
            self.assertEqual(packaged['baseline'], report)
            html = (out / 'index.html').read_text()
            self.assertNotIn('</script><script>alert', html)
            self.assertNotIn('/*REPORT_DATA*/null', html)
            self.assertTrue((out / 'milestones.csv').exists())


if __name__ == '__main__':
    unittest.main()
