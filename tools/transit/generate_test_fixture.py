"""Regenerate the synthetic cross-language importer fixture from compiler test inputs."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent / 'tests'))
from test_build_transit import fixture, compiler
import tempfile

if __name__ == '__main__':
    target = Path(__file__).resolve().parents[2] / 'app/shared/src/androidHostTest/resources/transit/example.bltransit'
    with tempfile.TemporaryDirectory() as temporary:
        source = Path(temporary) / 'gtfs.zip'
        source.write_bytes(fixture())
        compiler.compile_feed(source, target, 'Synthetic example', 'Synthetic test data; not for navigation')
        source.write_bytes(fixture({'trips.txt': 'route_id,service_id,trip_id,bikes_allowed\nr1,s,t1,1\nr2,s,t2,1\n'}))
        compiler.compile_feed(source,target.with_name('bicycle.bltransit'),'Synthetic bicycle','Synthetic test data; not for navigation')
    from datetime import date
    from generate_demo import make_demo
    make_demo(target.with_name('complex.bltransit'), date(2026,11,2), True)
    print(target)
