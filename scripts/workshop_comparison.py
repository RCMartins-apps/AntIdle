"""Run comparable saved and draft catalogs in isolated engine processes."""
import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
from time import monotonic

from balance import ROOT


def compare(bundle, saved, route_index, draft, cancelled):
    config = json.loads((ROOT / 'tools/balance/default.json').read_text())
    config['policies'] = [p for p in config['policies'] if p['name'] == 'balanced']
    config.update(seeds=[42], foundings=1, maxTicks=72000)
    with TemporaryDirectory(prefix='antidle-comparison-') as directory:
        root = Path(directory)
        scenario = root / 'scenario.json'
        scenario.write_text(json.dumps(config), encoding='utf-8')
        variants = {'saved': list(saved), 'draft': list(saved)}
        variants['draft'][route_index] = draft
        reports = {}
        for name, catalogs in variants.items():
            if cancelled.is_set():
                return {'cancelled': True}
            out = root / name
            out.mkdir()
            inputs = [out / 'upgrades.json', out / 'chambers.json']
            for path, contents in zip(inputs, catalogs):
                path.write_bytes(contents)
            error_log = out / 'error.log'
            with error_log.open('wb') as stderr, subprocess.Popen(
                ['node', str(bundle), str(scenario), str(out), *(str(p) for p in inputs)],
                stdout=subprocess.DEVNULL, stderr=stderr,
            ) as process:
                deadline = monotonic() + 120
                while process.poll() is None:
                    if cancelled.wait(0.15):
                        process.kill()
                        return {'cancelled': True}
                    if monotonic() >= deadline:
                        process.kill()
                        raise ValueError('Simulation exceeded two minutes.')
                if process.returncode:
                    errors = error_log.read_text(encoding='utf-8', errors='replace')
                    raise ValueError('Simulation failed. ' + errors.splitlines()[0] if errors else 'Simulation failed.')
            report = json.loads((out / 'report.json').read_text(encoding='utf-8'))
            run = report['runs'][0]
            rate = report['ticksPerSecond']
            sugar = next((r for r in run['resourceTimes'] if r['name'].lower() == 'sugar'), {})
            reports[name] = {
                'milestones': {m['name']: m['tick'] / rate for m in run['milestones']},
                'sugarEmpty': sugar.get('emptyTicks', 0) / rate,
                'starvationDeaths': run['starvationDeaths'],
            }
        return reports
