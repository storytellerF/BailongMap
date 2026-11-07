"""Run external delivery cases through Android host tests, without a device or server."""
import argparse
import json
import os
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('spec', type=Path, help='External acceptance JSON; relative data paths resolve beside it')
    args = parser.parse_args()
    spec = args.spec.resolve(strict=True)
    result = spec.with_suffix('.result.json')
    content = json.loads(spec.read_text(encoding='utf-8-sig'))
    inputs = [spec] + [Path(name) if Path(name).is_absolute() else spec.parent / name
                       for name in [content['package']] + content.get('roads', [])]
    require_outputs = [result.resolve(), spec.with_suffix('.log').resolve()]
    if any(output == item.resolve() for output in require_outputs for item in inputs):
        parser.exit(1, 'Acceptance outputs must not overwrite input files\n')
    result.unlink(missing_ok=True)
    root = Path(__file__).resolve().parents[2]
    environment = dict(os.environ, BAILONGMAP_TRANSIT_ACCEPTANCE=str(spec))
    gradle = root / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    log = spec.with_suffix('.log')
    with log.open('w', encoding='utf-8') as output:
        completed = subprocess.run([str(gradle), ':app:shared:testAndroidHostTest', '--tests',
                                    '*TransitDeliveryAcceptanceTest', '--rerun', '--no-daemon'],
                                   cwd=root, env=environment, stdout=output, stderr=subprocess.STDOUT)
    if completed.returncode or not result.exists():
        parser.exit(1, f'Application acceptance failed; see {log}\n')
    report = json.loads(result.read_text(encoding='utf-8'))
    if report.get('passed') is not True or report.get('case_count', 0) < 1:
        parser.exit(1, f'Application acceptance has no passing evidence; see {log}\n')
    print(json.dumps({'status': 'passed', 'cases': report['case_count'], 'result': str(result)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
