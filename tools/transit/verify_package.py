"""Verify a delivery against its GTFS source and optional application acceptance run."""
import argparse
from contextlib import closing
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sqlite3
import tempfile
import zipfile

from build_transit import compile_feed, date, read_tables, require, LIMIT
from feed_audit import audit


def digest(path):
    with path.open('rb') as stream:
        value = hashlib.sha256()
        for chunk in iter(lambda: stream.read(65536), b''):
            value.update(chunk)
        return value.hexdigest()


def relative_file(spec, value):
    path = Path(value)
    return path if path.is_absolute() else spec.parent / path


def verify(package, source, as_of, acceptance=None, require_routing=False, require_offline=False):
    """Recompile with the current compiler and bind acceptance evidence to exact bytes."""
    day = date(as_of)
    require(package.stat().st_size <= 32 * 1024 * 1024, 'Package exceeds app import limit')
    with zipfile.ZipFile(package) as archive:
        entries = archive.infolist()
        require(len(entries) == 2 and {e.filename for e in entries} == {'manifest.json', 'network.sqlite'}, 'Invalid package contents')
        require(archive.getinfo('manifest.json').file_size <= 16384, 'Manifest exceeds app import limit')
        require(archive.getinfo('network.sqlite').file_size <= LIMIT, 'Database exceeds app import limit')
        manifest = json.loads(archive.read('manifest.json'))
        raw = archive.read('network.sqlite')
    require(manifest.get('format') == 'bailongmap-transit' and manifest.get('version') == 3, 'Delivery verification requires current v3 compiler output')
    require(hashlib.sha256(raw).hexdigest() == manifest.get('sha256'), 'Database digest mismatch')
    require(date(manifest['start']) <= day <= date(manifest['end']), 'Package does not cover the requested use date')
    tables = read_tables(source)
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        expected = compile_feed(source, root / 'rebuilt.bltransit', manifest['name'], manifest['source'], manifest['start'], manifest['end'])
        require(expected == manifest, 'Package does not match its source/current compiler; rebuild in this environment')
        database = root / 'network.sqlite'
        database.write_bytes(raw)
        with closing(sqlite3.connect(database)) as db:
            require(db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok', 'Database integrity failure')
            require(not db.execute('PRAGMA foreign_key_check').fetchall(), 'Database references are invalid')
            active = db.execute('SELECT COUNT(*) FROM trips t JOIN service_dates s ON s.service=t.service WHERE s.date=?', (as_of,)).fetchone()[0]
            accessible = db.execute('SELECT COUNT(*) FROM stops WHERE access=1').fetchone()[0]
            require(accessible > 0, 'No street-accessible stops; package cannot serve door-to-door journeys')
    report = {
        'status': 'artifact_verified_only',
        'checked_at': datetime.now(timezone.utc).isoformat(),
        'as_of': as_of,
        'source_sha256': digest(source),
        'package_sha256': digest(package),
        'manifest': manifest,
        'active_trips_on_use_date': active,
        **audit(tables),
        'routing': {'status': 'not_run'},
        'source_truth': 'Requires human verification against operator sources; successful validation does not establish factual accuracy.',
    }
    if not active:
        report['warnings'].append('No trips operate on the requested use date; check the operating calendar before delivery.')
    require(not (require_routing or require_offline) or acceptance is not None, 'Routing verification needs an acceptance specification')
    if acceptance:
        spec = json.loads(acceptance.read_text(encoding='utf-8-sig'))
        require(digest(relative_file(acceptance, spec['package'])) == report['package_sha256'], 'Acceptance specification targets a different package')
        result_file = acceptance.with_suffix('.result.json')
        result = json.loads(result_file.read_text(encoding='utf-8'))
        require(result.get('passed') is True and result.get('case_count', 0) > 0, 'Application acceptance did not pass any cases')
        require(result.get('engine') == 'android-host-offline' and result['case_count'] == len(spec['cases']), 'Invalid application acceptance evidence')
        require(result.get('successful_modes'), 'Acceptance needs a successful journey')
        require(result.get('spec_sha256') == digest(acceptance) and result.get('package_sha256') == report['package_sha256'], 'Acceptance evidence is stale or belongs to different inputs')
        roads = sorted(digest(relative_file(acceptance, name)) for name in spec.get('roads', []))
        require(roads == sorted(result.get('road_sha256', [])), 'Acceptance road data has changed')
        report['routing'] = result
        report['routing']['scope'] = 'door_to_door_offline' if 'TRANSIT_WALK' in result['successful_modes'] else 'declared_modes_only'
        report['status'] = 'declared_journeys_verified'
        require(not require_offline or 'TRANSIT_WALK' in result['successful_modes'], 'Full offline delivery needs a successful TRANSIT_WALK case and matching roads')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('package', type=Path)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--as-of', required=True, help='Intended use date, YYYYMMDD')
    parser.add_argument('--acceptance', type=Path)
    parser.add_argument('--require-routing', action='store_true')
    parser.add_argument('--require-offline', action='store_true', help='Require actual offline walking + transit + walking acceptance')
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    protected = [args.package, args.source] + ([args.acceptance, args.acceptance.with_suffix('.result.json')] if args.acceptance else [])
    require(all(args.report.resolve() != item.resolve() for item in protected), 'Report must not overwrite any input')
    try:
        result = verify(args.package, args.source, args.as_of, args.acceptance, args.require_routing, args.require_offline)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile, sqlite3.Error) as error:
        result = {'status': 'failed', 'error': str(error)}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': result['status'], 'report': str(args.report)}, ensure_ascii=False))
    if result['status'] == 'failed':
        parser.exit(1, 'Delivery verification failed; see report\n')


if __name__ == '__main__':
    main()
