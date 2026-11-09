#!/usr/bin/env python3
"""Run the real Scala.js engine and package portable balance reports/checkpoints."""
import argparse
import base64
import csv
import hashlib
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]


def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()


def package_report(output, baseline=None):
    path = output / 'report.json'
    report = json.loads(path.read_text())
    previous = json.loads(baseline.read_text()) if baseline else None
    if previous and previous['schemaVersion'] != report['schemaVersion']:
        raise ValueError('Baseline schema differs')
    if previous:
        previous.pop('baseline', None)
    report['baseline'] = previous
    # Stamp only exported saves: simulation data remains deterministic.
    for checkpoint in (output / 'checkpoints').rglob('*.json'):
        data = json.loads(checkpoint.read_text())
        data['world']['lastUpdateTime'] = int(time.time() * 1000)
        data['world']['targetTick'] = data['world']['currentTick']
        text = json.dumps(data, separators=(',', ':'))
        checkpoint.write_text(text)
        checkpoint.with_suffix('.save.txt').write_text(base64.b64encode(text.encode()).decode())
    path.write_text(json.dumps(report, indent=2) + '\n')
    with (output / 'milestones.csv').open('w', newline='') as stream:
        writer = csv.writer(stream)
        writer.writerow(['policy', 'seed', 'colony', 'milestone', 'total_seconds', 'colony_seconds'])
        for run in report['runs']:
            for milestone in run['milestones']:
                writer.writerow([run['policy']['name'], run['seed'], milestone['colony'], milestone['name'],
                                 milestone['tick'] / report['ticksPerSecond'], milestone['colonyTicks'] / report['ticksPerSecond']])
    # Escape embedded script terminators; report labels are rendered with textContent.
    payload = json.dumps(report, separators=(',', ':')).replace('<', '\\u003c').replace('\u2028', '\\u2028').replace('\u2029', '\\u2029')
    template = (ROOT / 'tools/balance/report.html').read_text()
    (output / 'index.html').write_text(template.replace('/*REPORT_DATA*/null', payload))
    return report


def source_hash():
    """Include uncommitted/new implementation files without scratch artifacts."""
    digest = hashlib.sha256()
    for folder in ['src', 'project', 'tools/balance', 'scripts']:
        for path in sorted((ROOT / folder).rglob('*')):
            if path.is_file() and not set(path.relative_to(ROOT).parts) & {'target', '__pycache__'}:
                digest.update(str(path.relative_to(ROOT)).encode())
                digest.update(path.read_bytes())
    digest.update((ROOT / 'build.sbt').read_bytes())
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, default=ROOT / 'tools/balance/default.json')
    parser.add_argument('--out', type=Path, required=True, help='New output directory (never overwritten)')
    parser.add_argument('--baseline', type=Path, help='Previous report.json for informational deltas')
    parser.add_argument('--hours', type=float, help='Override simulation time budget per run')
    parser.add_argument('--policy', action='append', help='Select policy name (repeatable)')
    parser.add_argument('--seed', type=int, action='append', help='Override seeds (repeatable)')
    parser.add_argument('--skip-build', action='store_true', help='Reuse existing bundle; rebuild after source changes')
    parser.add_argument('--sbt', default='sbt')
    args = parser.parse_args()
    config = json.loads(args.config.read_text())
    if args.hours is not None:
        if not 0 < args.hours <= 2000:
            parser.error('--hours must be in (0, 2000]')
        config['maxTicks'] = max(1, int(args.hours * 3600 * 5))
    if args.policy:
        known = {p['name'] for p in config['policies']}
        if set(args.policy) - known:
            parser.error('Unknown policy')
        config['policies'] = [p for p in config['policies'] if p['name'] in args.policy]
    if args.seed:
        config['seeds'] = args.seed
    if any(abs(seed) > 2**53 - 1 for seed in config['seeds']):
        parser.error('Seeds must fit exactly in JSON/JavaScript integers')
    output = args.out.resolve()
    if output.exists():
        parser.error('Output directory already exists; choose a fresh name')
    baseline = args.baseline.resolve() if args.baseline else None
    if baseline:
        json.loads(baseline.read_text())
    if not args.skip_build:
        subprocess.run([args.sbt, 'balance/fastOptJS'], cwd=ROOT, check=True)
    bundles = [path for path in (ROOT / 'tools/balance/target/scala-2.13').glob('*-fastopt/main.js')
               if not path.parent.name.endswith('-test-fastopt')]
    if len(bundles) != 1:
        parser.error('Build balance/fastOptJS first')
    output.mkdir(parents=True)
    (output / 'config.json').write_text(json.dumps(config, indent=2))
    started = time.monotonic()
    subprocess.run(['node', str(bundles[0]), str(output / 'config.json'), str(output)], cwd=ROOT, check=True)
    report = json.loads((output / 'report.json').read_text())
    report['provenance'] = dict(commit=command('git', 'rev-parse', 'HEAD'),
        sourceHash=source_hash(),
        configHash=hashlib.sha256(json.dumps(config, sort_keys=True).encode()).hexdigest(),
        node=command('node', '--version'), wallSeconds=round(time.monotonic() - started, 2), reusedBuild=args.skip_build)
    (output / 'report.json').write_text(json.dumps(report))
    report = package_report(output, baseline)
    for run in report['runs']:
        print(f"{run['policy']['name']}/{run['seed']}: {run['status']}, {run['ticks']/5/60:.1f} simulated min, {run['completedFoundings']} foundings, {run['actions']} decisions")
    print(f"Open {output / 'index.html'} in a browser. Checkpoints/*.save.txt import via the game's Load control.")


if __name__ == '__main__':
    main()
