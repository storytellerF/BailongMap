"""Compile the supported fixed-stop GTFS subset to a versioned BailongMap package."""
import argparse
from compile_stations import compile_stations
from contextlib import closing
import csv
from datetime import datetime, timedelta
import hashlib
import io
import json
import math
from pathlib import Path
import re
import sqlite3
import tempfile
import zipfile
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

LIMIT = 128 * 1024 * 1024
MAX_CONNECTIONS = 250_000


def require(condition, message):
    if not condition:
        raise ValueError(message)


def date(value):
    require(bool(re.fullmatch(r"\d{8}", value)), "Date must be YYYYMMDD")
    return datetime.strptime(value, "%Y%m%d").date()


def seconds(value):
    require(bool(re.fullmatch(r"\d{2}:[0-5]\d:[0-5]\d", value)), "Time must be HH:MM:SS")
    h, m, s = map(int, value.split(":"))
    require(h < 48, "Only service times below 48:00:00 are supported")
    return h * 3600 + m * 60 + s


def read_tables(path):
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist()
        require(len(entries) <= 100 and len({e.filename for e in entries}) == len(entries), "Too many or duplicate ZIP entries")
        require(all('/' not in e.filename and '\\' not in e.filename for e in entries), "GTFS files must be at ZIP root")
        require(not any(e.filename in ['locations.geojson'] for e in entries), 'On-demand locations unsupported')
        require(sum(e.file_size for e in entries) <= LIMIT, "GTFS exceeds 128 MiB expanded")
        tables = {}
        for entry in entries:
            if not entry.filename.endswith('.txt'):
                continue
            content = archive.read(entry).decode('utf-8-sig')
            reader = csv.DictReader(io.StringIO(content, newline=''))
            require(reader.fieldnames and len(set(reader.fieldnames)) == len(reader.fieldnames), f"Invalid header: {entry.filename}")
            rows = list(reader)
            require(all(None not in row and None not in row.values() for row in rows), f"Invalid CSV width: {entry.filename}")
            tables[entry.filename] = rows
        return tables


def keyed(tables, name, key, columns):
    rows = tables.get(name, [])
    require(rows and all(c in rows[0] for c in [key] + columns), f"Missing table/columns: {name}")
    result = {r[key]: r for r in rows}
    require('' not in result and len(result) == len(rows), f"Empty or duplicate {key}")
    return result


def compile_feed(source, output, name, attribution, start=None, end=None):
    require(source.resolve() != output.resolve(), "Output must differ from input")
    require(name.strip() and attribution.strip(), "Name and source attribution are required")
    require(len(name) <= 200 and len(attribution) <= 2000, "Name/source metadata is too long")
    tables = read_tables(source)
    # Fail explicitly rather than silently dropping timetable or transfer semantics.
    for unsupported in ['locations.txt', 'locations.geojson', 'location_groups.txt', 'location_group_stops.txt', 'booking_rules.txt']:
        require(not tables.get(unsupported), f"Current compiler does not support {unsupported}; provide explicit schedules / use a compatible future compiler")
    agencies = tables.get('agency.txt', [])
    require(agencies and all(r.get('agency_name') and r.get('agency_url') and r.get('agency_timezone') for r in agencies), "Missing agency details")
    zones = {r['agency_timezone'] for r in agencies}
    require(len(zones) == 1, "Current compiler requires one agency timezone")
    zone = next(iter(zones))
    try:
        ZoneInfo(zone)
    except ZoneInfoNotFoundError as error:
        raise ValueError("Unknown timezone or missing Python tzdata; install tzdata on Windows") from error
    require(bool(re.fullmatch(r'[A-Za-z_+-]+(?:/[A-Za-z0-9_+.-]+)*', zone)), "Invalid timezone")
    stops = keyed(tables, 'stops.txt', 'stop_id', ['stop_name', 'stop_lat', 'stop_lon'])
    require(len(stops) <= 50000, 'Limit: 50000 stops; split the region')
    routes = keyed(tables, 'routes.txt', 'route_id', ['route_type'])
    trips = keyed(tables, 'trips.txt', 'trip_id', ['route_id', 'service_id'])
    agency_ids = [row.get('agency_id', '') for row in agencies]
    require(len(agencies) == 1 or (all(agency_ids) and len(set(agency_ids)) == len(agency_ids)), 'Multiple agencies require distinct agency_id values')
    # Coordinates can be inherited through verified station hierarchies.
    stop_ids = {key: i for i, key in enumerate(stops)}
    route_ids = {key: i for i, key in enumerate(routes)}
    trip_ids = {key: i for i, key in enumerate(trips)}
    station_rows, footpaths, transfer_rules = compile_stations(tables, stops, stop_ids, routes, route_ids, trips, trip_ids, require)
    for row in stops.values():
        lat, lon = float(row['stop_lat']), float(row['stop_lon'])
        require(0 < len(row['stop_name']) <= 500 and math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180, 'Invalid stop')
    for row in routes.values():
        require(not row.get('agency_id') or row['agency_id'] in agency_ids, 'Unknown route agency_id')
        require(len(agencies) == 1 or row.get('agency_id'), 'Route agency_id required with multiple agencies')
        require(all(row.get(k, '') in ['', '1'] for k in ['continuous_pickup', 'continuous_drop_off']), 'Continuous boarding unsupported')
        require(row['route_type'] in ['1', '3'], "Current compiler supports subway (1) and bus (3) only")
        require(0 < len(row.get('route_short_name') or row.get('route_long_name') or '') <= 200, "Route name required, up to 200 characters")
    calendars = tables.get('calendar.txt', [])
    exceptions = tables.get('calendar_dates.txt', [])
    require(calendars or exceptions, "Service calendar required")
    active = {}
    weekdays = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']
    for row in calendars:
        sid = row['service_id']
        require(sid and sid not in active, "Invalid / duplicate calendar service_id")
        first, last = date(row['start_date']), date(row['end_date'])
        require(0 <= (last-first).days <= 3660, "Invalid or excessive calendar range")
        require(all(row[w] in ['0', '1'] for w in weekdays), "Invalid weekday")
        active[sid] = {first+timedelta(days=i) for i in range((last-first).days+1) if row[weekdays[(first+timedelta(days=i)).weekday()]] == '1'}
    seen = set()
    for row in exceptions:
        sid, day = row['service_id'], date(row['date'])
        require(sid and (sid, day) not in seen and row['exception_type'] in ['1', '2'], "Invalid calendar exception")
        seen.add((sid, day))
        days = active.setdefault(sid, set())
        (days.add if row['exception_type'] == '1' else days.discard)(day)
    all_dates = sorted(set().union(*active.values()))
    require(all_dates, "No operating dates")
    first = date(start) if start else all_dates[0]
    last = date(end) if end else all_dates[-1]
    require(0 <= (last-first).days <= 365, "Select an explicit coverage window of at most 366 days with --start/--end")
    active = {sid: sorted(d for d in days if first <= d <= last) for sid, days in active.items()}
    require(any(active.values()), "No service in selected window")
    require(any(active.get(trip['service_id']) for trip in trips.values()), 'No trip operates in selected window')
    require(sum(map(len, active.values())) <= 1000000, 'Limit: 1000000 service dates; shorten coverage')
    stop_ids = {s: i for i, s in enumerate(stops)}
    route_ids = {r: i for i, r in enumerate(routes)}
    service_ids = {s: i for i, s in enumerate(active)}
    times = {t: [] for t in trips}
    for row in tables.get('stop_times.txt', []):
        require(row.get('trip_id') in trips and row.get('stop_id') in stops, "Unknown trip or stop reference")
        require(not row.get('start_pickup_drop_off_window') and not row.get('end_pickup_drop_off_window'), "On-demand windows unsupported")
        require(not any(row.get(field) for field in ['location_id', 'location_group_id', 'pickup_booking_rule_id', 'drop_off_booking_rule_id']), 'On-demand locations/booking rules unsupported')
        require(row.get('timepoint', '') in ('', '0', '1'), 'Invalid timepoint')
        for field in ['pickup_type', 'drop_off_type', 'continuous_pickup', 'continuous_drop_off']:
            require(row.get(field, '') in ['', '0', '1'], f"Unsupported {field}")
        require(row.get('continuous_pickup', '1') in ['', '1'] and row.get('continuous_drop_off', '1') in ['', '1'], "Continuous boarding unsupported")
        require(int(stops[row['stop_id']].get('location_type') or 0) == 0, 'Trips must reference boarding stops')
        sequence = int(row['stop_sequence'])
        arrival, departure = seconds(row['arrival_time']), seconds(row['departure_time'])
        require(sequence >= 0 and departure >= arrival, "Invalid stop order/time")
        times[row['trip_id']].append((sequence, row['stop_id'], arrival, departure, int(row.get('pickup_type') or 0), int(row.get('drop_off_type') or 0)))
    intervals = {}
    for row in tables.get('frequencies.txt', []):
        require(row['trip_id'] in trips, 'Unknown frequency trip')
        begin, finish, headway = seconds(row['start_time']), seconds(row['end_time']), int(row['headway_secs'])
        exact = int(row.get('exact_times') or 0)
        require(begin < finish and 0 < headway <= 172800 and exact in (0,1), 'Invalid frequency interval')
        intervals.setdefault(row['trip_id'], []).append((begin, finish, headway, exact))
    for values in intervals.values():
        values.sort()
        require(all(a[1] <= b[0] for a,b in zip(values, values[1:])), 'Overlapping frequency intervals')
    connections, trip_rows, frequency_rows = [], [], []
    for tid, trip in trips.items():
        require(trip['route_id'] in routes and trip['service_id'] in active, 'Unknown route or service reference')
        require(trip.get('bikes_allowed','') in ('','0','1','2'), 'Invalid bikes_allowed')
        rows = sorted(times[tid])
        require(len(rows) >= 2 and len({r[0] for r in rows}) == len(rows), 'Each trip requires >=2 distinct stop sequences')
        for previous, current in zip(rows, rows[1:]):
            require(current[2] > previous[3], 'Connections require positive travel times')
        def emit(shift):
            runtime_id = len(trip_rows)
            trip_rows.append((runtime_id, route_ids[trip['route_id']], service_ids[trip['service_id']], trip_ids[tid], int(trip.get('bikes_allowed') or 0)))
            for previous,current in zip(rows, rows[1:]):
                departure, arrival = previous[3]+shift, current[2]+shift
                require(0 <= departure < arrival < 172800, 'Expanded frequency exceeds supported service-day times')
                connections.append((runtime_id, previous[0], stop_ids[previous[1]], stop_ids[current[1]], departure, arrival, previous[4], current[5]))
                require(len(connections)<=MAX_CONNECTIONS, 'Limit: 250000 connections; split region')
            return runtime_id
        if tid not in intervals: emit(0)
        else:
            estimated_id = None
            for begin,finish,headway,exact in intervals[tid]:
                if exact:
                    for departure in range(begin,finish,headway): emit(departure-rows[0][3])
                else:
                    if estimated_id is None: estimated_id = emit(0)
                    require(finish-1 + rows[-1][2]-rows[0][3] < 172800, 'Frequency service exceeds 48-hour limit')
                    frequency_rows.append((len(frequency_rows), estimated_id, begin, finish, headway))
    require(len(frequency_rows)<=100000, 'Too many frequency intervals')
    metadata = dict(name=name, source=attribution, timezone=zone, start=first.strftime('%Y%m%d'), end=last.strftime('%Y%m%d'))
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as temporary:
        database = Path(temporary) / 'network.sqlite'
        with closing(sqlite3.connect(database)) as db:
            db.executescript(Path(__file__).with_name('schema.sql').read_text(encoding='utf-8-sig'))
            db.executemany('INSERT INTO metadata VALUES (?,?)', metadata.items())
            db.executemany('INSERT INTO stops VALUES (?,?,?,?,?,?,?)', station_rows)
            db.executemany('INSERT INTO routes VALUES (?,?,?)', [(route_ids[r], v.get('route_short_name') or v['route_long_name'], int(v['route_type'])) for r, v in routes.items()])
            db.executemany('INSERT INTO services VALUES (?)', [(i,) for i in service_ids.values()])
            db.executemany('INSERT INTO service_dates VALUES (?,?)', [(service_ids[s], d.strftime('%Y%m%d')) for s, days in active.items() for d in days])
            db.executemany('INSERT INTO trips VALUES (?,?,?,?,?)', trip_rows)
            db.executemany('INSERT INTO frequencies VALUES (?,?,?,?,?)', frequency_rows)
            db.executemany('INSERT INTO footpaths VALUES (?,?,?,?)', footpaths)
            db.executemany('INSERT INTO transfer_rules VALUES (?,?,?,?,?,?,?,?)', transfer_rules)
            db.executemany('INSERT INTO connections VALUES (?,?,?,?,?,?,?,?)', connections)
            db.execute('ANALYZE')
            db.commit()
        raw = database.read_bytes()
        require(len(raw) <= LIMIT, "Compiled database exceeds 128 MiB")
        manifest = dict(format='bailongmap-transit', version=3, sha256=hashlib.sha256(raw).hexdigest(), **metadata,
                        stops=len(stops), routes=len(routes), connections=len(connections))
        staged = Path(temporary) / 'package.bltransit'
        with zipfile.ZipFile(staged, 'w', zipfile.ZIP_DEFLATED) as archive:
            for filename, content in [('manifest.json', json.dumps(manifest, ensure_ascii=False).encode('utf-8')), ('network.sqlite', raw)]:
                entry = zipfile.ZipInfo(filename, (2000, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, content)
        require(staged.stat().st_size <= 32*1024*1024, "Compiled package exceeds 32 MiB")
        staged.replace(output)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('gtfs', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--name', required=True)
    parser.add_argument('--source', required=True, help='Source attribution / license information')
    parser.add_argument('--start', help='YYYYMMDD')
    parser.add_argument('--end', help='YYYYMMDD')
    args = parser.parse_args()
    try:
        summary = compile_feed(args.gtfs, args.output, args.name, args.source, args.start, args.end)
        print(json.dumps(summary, ensure_ascii=False, indent=2))
    except (ValueError, KeyError, OSError, zipfile.BadZipFile, sqlite3.Error) as error:
        parser.exit(1, f'Build failed: {error}\n')


if __name__ == '__main__':
    main()
