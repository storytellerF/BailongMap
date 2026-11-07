import json
from pathlib import Path
import tempfile
import unittest

from test_build_transit import fixture, compiler
from verify_package import verify, digest


class DeliveryVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / 'source.zip'
        self.package = self.root / 'package.bltransit'

    def build(self, changes=None):
        self.source.write_bytes(fixture(changes))
        compiler.compile_feed(self.source, self.package, 'Synthetic delivery', 'Synthetic test data')

    def test_reports_omitted_data_without_claiming_route_verification(self):
        self.build({'shapes.txt': 'shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\ns,1,103,0\ns,1.01,103,1\n'})
        report = verify(self.package, self.source, '20261102')
        self.assertEqual(report['status'], 'artifact_verified_only')
        self.assertEqual(report['routing']['status'], 'not_run')
        self.assertEqual(report['omitted_data']['shapes.txt']['rows'], 2)
        self.assertEqual(report['active_trips_on_use_date'], 2)

    def test_expired_package_is_not_ready_for_intended_use_date(self):
        self.build()
        with self.assertRaisesRegex(ValueError, 'requested use date'):
            verify(self.package, self.source, '20261107')

    def test_rejects_a_package_not_reproduced_from_its_claimed_source(self):
        self.build()
        self.source.write_bytes(fixture({'trips.txt': 'route_id,service_id,trip_id,bikes_allowed\nr1,s,t1,1\nr2,s,t2,1\n'}))
        with self.assertRaisesRegex(ValueError, 'does not match'):
            verify(self.package, self.source, '20261102')

    def test_routing_claim_needs_application_evidence(self):
        self.build()
        with self.assertRaisesRegex(ValueError, 'acceptance specification'):
            verify(self.package, self.source, '20261102', require_routing=True)

    def evidence(self):
        spec = self.root / 'acceptance.json'
        spec.write_text(json.dumps({'package': 'package.bltransit', 'cases': [{}]}), encoding='utf-8')
        spec.with_suffix('.result.json').write_text(json.dumps({
            'passed': True, 'engine': 'android-host-offline', 'case_count': 1,
            'package_sha256': digest(self.package), 'spec_sha256': digest(spec),
            'road_sha256': [], 'successful_modes': ['TIMETABLE'],
        }), encoding='utf-8')
        return spec

    def test_timetable_acceptance_does_not_establish_door_to_door_offline_support(self):
        self.build()
        spec = self.evidence()
        self.assertEqual(verify(self.package, self.source, '20261102', spec)['status'], 'declared_journeys_verified')
        with self.assertRaisesRegex(ValueError, 'TRANSIT_WALK'):
            verify(self.package, self.source, '20261102', spec, require_offline=True)

    def test_changed_acceptance_spec_cannot_reuse_an_old_pass(self):
        self.build()
        spec = self.evidence()
        spec.write_text(spec.read_text() + '\n', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'stale'):
            verify(self.package, self.source, '20261102', spec)


if __name__ == '__main__':
    unittest.main()
