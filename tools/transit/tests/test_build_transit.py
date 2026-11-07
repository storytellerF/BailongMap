from contextlib import closing
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).parents[1]))
spec = importlib.util.spec_from_file_location('build_transit', Path(__file__).parents[1] / 'build_transit.py')
compiler = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compiler)


def fixture(changes=None):
    tables = {
        'agency.txt': 'agency_id,agency_name,agency_url,agency_timezone\na,Example,https://example.org,Asia/Singapore\n',
        'stops.txt': 'stop_id,stop_name,stop_lat,stop_lon\na,"First, stop",1,103\nb,Transfer,1.01,103\nc,Last,1.02,103\n',
        'routes.txt': 'route_id,route_short_name,route_type\nr1,Metro 1,1\nr2,Bus 2,3\n',
        'trips.txt': 'route_id,service_id,trip_id\nr1,s,t1\nr2,s,t2\n',
        'calendar_dates.txt': 'service_id,date,exception_type\ns,20261102,1\n',
        'stop_times.txt': 'trip_id,stop_id,stop_sequence,arrival_time,departure_time\nt1,a,1,08:00:00,08:00:00\nt1,b,2,08:10:00,08:10:00\nt2,b,1,08:12:00,08:12:00\nt2,c,2,08:20:00,08:20:00\n'
    }
    tables.update(changes or {})
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as archive:
        for name, text in tables.items():
            archive.writestr(name, text)
    return output.getvalue()


class CompilerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'gtfs.zip'
        self.output = self.root / 'data.bltransit'

    def build(self, changes=None):
        self.source.write_bytes(fixture(changes))
        return compiler.compile_feed(self.source, self.output, 'Example region', 'Synthetic test data')

    def test_compiles_indexed_package_with_valid_digest(self):
        summary = self.build()
        with zipfile.ZipFile(self.output) as archive:
            raw = archive.read('network.sqlite')
            manifest = json.loads(archive.read('manifest.json'))
        self.assertEqual(manifest['sha256'], hashlib.sha256(raw).hexdigest())
        self.assertEqual(summary['stops'], 3)
        dbfile = self.root / 'db.sqlite'
        dbfile.write_bytes(raw)
        with closing(sqlite3.connect(dbfile)) as db:
            self.assertEqual(db.execute('PRAGMA integrity_check').fetchone()[0], 'ok')
            self.assertEqual(db.execute('PRAGMA foreign_key_check').fetchall(), [])
            self.assertEqual(db.execute('SELECT COUNT(*) FROM connections').fetchone()[0], 2)
            self.assertEqual(db.execute('SELECT departure FROM connections ORDER BY departure').fetchall(), [(28800,), (29520,)])
            self.assertIn('connections_departure', {r[1] for r in db.execute("PRAGMA index_list('connections')")})

    def test_bicycle_permission_is_preserved_and_unknown_stays_unknown(self):
        self.build({'trips.txt': 'route_id,service_id,trip_id,bikes_allowed\nr1,s,t1,1\nr2,s,t2,\n'})
        with self.database() as db:
            self.assertEqual(db.execute('SELECT bikes_allowed FROM trips ORDER BY id').fetchall(), [(1,), (0,)])
        with self.assertRaises(ValueError):
            self.build({'trips.txt': 'route_id,service_id,trip_id,bikes_allowed\nr1,s,t1,3\nr2,s,t2,2\n'})

    def test_rejects_unknown_stop_without_overwriting_previous_package(self):
        self.output.write_bytes(b'previous valid package')
        with self.assertRaises(ValueError):
            self.build({'stop_times.txt': fixture_text('stop_times.txt').replace('t1,b,', 't1,missing,')})
        self.assertEqual(self.output.read_bytes(), b'previous valid package')

    def test_rejects_archive_path_traversal(self):
        with self.assertRaises(ValueError):
            self.build({'../outside.txt': 'x'})

    def test_rejects_unknown_route_agency(self):
        with self.assertRaisesRegex(ValueError, 'agency_id'):
            self.build({'routes.txt': 'route_id,route_short_name,route_type,agency_id\nr1,Metro 1,1,missing\nr2,Bus 2,3,missing\n'})

    def test_rejects_window_where_only_unreferenced_services_operate(self):
        with self.assertRaisesRegex(ValueError, 'No trip operates'):
            self.build({'calendar_dates.txt': 'service_id,date,exception_type\ns,20261102,2\nunused,20261102,1\n'})

    def test_does_not_discard_booking_constraints_on_fixed_stops(self):
        text = fixture_text('stop_times.txt').splitlines()
        text[0] += ',pickup_booking_rule_id'
        text[1:] = [row + ',booking' for row in text[1:]]
        with self.assertRaisesRegex(ValueError, 'booking rules'):
            self.build({'stop_times.txt': '\n'.join(text) + '\n'})

    def database(self):
        with zipfile.ZipFile(self.output) as archive:
            path = self.root / 'db.sqlite'
            path.write_bytes(archive.read('network.sqlite'))
        return closing(sqlite3.connect(path))

    def test_expands_exact_headways_into_distinct_departures(self):
        self.build({'frequencies.txt': 'trip_id,start_time,end_time,headway_secs,exact_times\nt1,08:00:00,08:30:00,600,1\n'})
        with self.database() as db:
            self.assertEqual(db.execute('SELECT departure FROM connections WHERE origin=0 ORDER BY departure').fetchall(), [(28800,), (29400,), (30000,)])
            self.assertEqual(db.execute('SELECT COUNT(*) FROM frequencies').fetchone()[0], 0)

    def test_preserves_unscheduled_frequency_windows_without_fake_departures(self):
        self.build({'frequencies.txt': 'trip_id,start_time,end_time,headway_secs,exact_times\nt1,08:00:00,09:00:00,600,0\n'})
        with self.database() as db:
            self.assertEqual(db.execute('SELECT start,end,headway FROM frequencies').fetchall(), [(28800,32400,600)])
            self.assertEqual(db.execute('SELECT COUNT(*) FROM connections').fetchone()[0], 2)

    def test_rejects_overlapping_frequency_windows(self):
        with self.assertRaisesRegex(ValueError,'Overlapping'):
            self.build({'frequencies.txt': 'trip_id,start_time,end_time,headway_secs\nt1,08:00:00,09:00:00,600\nt1,08:30:00,09:30:00,600\n'})

    def test_inherits_station_coordinates_and_compiles_directed_pathways(self):
        self.build({'stops.txt': 'stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station\ns,Station,1.01,103,1,\na,First,1,103,0,\nb,Platform,,,0,s\nc,Last,1.02,103,0,\ne,Entrance,1.011,103,2,s\n',
                    'pathways.txt': 'pathway_id,from_stop_id,to_stop_id,pathway_mode,is_bidirectional,traversal_time\np,e,b,1,0,90\n'})
        with self.database() as db:
            self.assertEqual(db.execute("SELECT lat,lon,access FROM stops WHERE name='Platform'").fetchone(), (1.01,103.0,0))
            self.assertEqual(db.execute('SELECT seconds FROM footpaths').fetchall(), [(90,)])

    def test_station_transfer_rule_expands_to_platforms(self):
        self.build({'stops.txt': 'stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station\ns,Station,1.01,103,1,\na,First,1,103,0,\nb,Platform,1.01,103,0,s\nc,Last,1.02,103,0,\n',
                    'transfers.txt': 'from_stop_id,to_stop_id,transfer_type,min_transfer_time,from_trip_id,to_route_id\ns,s,2,300,t1,r2\n'})
        with self.database() as db:
            self.assertEqual(db.execute('SELECT from_trip,to_route,type,seconds FROM transfer_rules').fetchall(), [(0,1,2,300)])

    def test_forbidden_transfer_does_not_create_walk_edge(self):
        self.build({'transfers.txt': 'from_stop_id,to_stop_id,transfer_type\na,c,3\n'})
        with self.database() as db:
            self.assertEqual(db.execute('SELECT type FROM transfer_rules').fetchall(), [(3,)])
            self.assertEqual(db.execute('SELECT COUNT(*) FROM footpaths').fetchone()[0], 0)

    def test_rejects_ambiguous_transfer_rules(self):
        with self.assertRaisesRegex(ValueError,'Ambiguous'):
            self.build({'transfers.txt':'from_stop_id,to_stop_id,transfer_type\nb,b,0\nb,b,3\n'})

    def test_preserves_overnight_times(self):
        text = fixture_text('stop_times.txt').replace('08:', '24:')
        self.build({'stop_times.txt': text})
        with zipfile.ZipFile(self.output) as archive:
            (self.root / 'db.sqlite').write_bytes(archive.read('network.sqlite'))
        with closing(sqlite3.connect(self.root / 'db.sqlite')) as db:
            self.assertEqual(db.execute('SELECT MIN(departure) FROM connections').fetchone()[0], 86400)

    def test_service_removal_exception_excludes_date(self):
        self.build({'calendar.txt': 'service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\ns,1,1,1,1,1,1,1,20261101,20261103\n',
                    'calendar_dates.txt': 'service_id,date,exception_type\ns,20261102,2\n'})
        with zipfile.ZipFile(self.output) as archive:
            (self.root / 'db.sqlite').write_bytes(archive.read('network.sqlite'))
        with closing(sqlite3.connect(self.root / 'db.sqlite')) as db:
            self.assertEqual(db.execute('SELECT date FROM service_dates ORDER BY date').fetchall(), [('20261101',), ('20261103',)])


def fixture_text(name):
    with zipfile.ZipFile(io.BytesIO(fixture())) as archive:
        return archive.read(name).decode()


if __name__ == '__main__':
    unittest.main()
