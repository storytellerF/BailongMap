"""Generate synthetic schedules near test coordinates; never use for real travel."""
import argparse
import csv
from datetime import datetime, timedelta
import io
from pathlib import Path
import tempfile
import zipfile
from zoneinfo import ZoneInfo
from build_transit import compile_feed


def make_demo(output, day, complex_data=False):
    rows = {
        'agency.txt': [['agency_id','agency_name','agency_url','agency_timezone'], ['test','SYNTHETIC TEST ONLY','https://example.org','Asia/Shanghai']],
        'stops.txt': [['stop_id','stop_name','stop_lat','stop_lon'], ['a','测试专用起点',39.9042,116.4074], ['b','测试专用换乘站',39.9242,116.4074], ['c','测试专用终点',39.9442,116.4074]],
        'routes.txt': [['route_id','route_short_name','route_type'], ['metro','测试地铁线',1], ['bus','测试公交线',3]],
        'trips.txt': [['route_id','service_id','trip_id']],
        'calendar.txt': [['service_id','monday','tuesday','wednesday','thursday','friday','saturday','sunday','start_date','end_date'], ['s',1,1,1,1,1,1,1,(day-timedelta(days=1)).strftime('%Y%m%d'),(day+timedelta(days=7)).strftime('%Y%m%d')]],
        'stop_times.txt': [['trip_id','stop_id','stop_sequence','arrival_time','departure_time']],
    }
    def time(seconds):
        return f'{seconds//3600:02}:{seconds//60%60:02}:{seconds%60:02}'
    for minute in range(0, 24*60, 5):
        for route, origin, destination, depart, arrive in [('metro','a','b',minute*60,minute*60+180), ('bus','b','c',minute*60+360,minute*60+600)]:
            trip = f'{route}-{minute}'
            rows['trips.txt'].append([route,'s',trip])
            rows['stop_times.txt'].extend([[trip,origin,1,time(depart),time(depart)], [trip,destination,2,time(arrive),time(arrive)]])
    if complex_data:
        rows['stops.txt'][0] += ['location_type','parent_station']
        for row in rows['stops.txt'][1:]: row += [0,'']
        rows['stops.txt'][2][-1]='station'
        rows['stops.txt'] += [
            ['station','测试换乘站建筑',39.9242,116.4074,1,''],
            ['d','测试公交站台',39.9250,116.4074,0,'station'],
            ['e','测试换乘站入口',39.9240,116.4080,2,'station'],
        ]
        rows['trips.txt']=[rows['trips.txt'][0],['metro','s','metro-frequency'],['bus','s','bus-frequency']]
        rows['stop_times.txt']=[rows['stop_times.txt'][0],
            ['metro-frequency','a',1,'00:00:00','00:00:00'],['metro-frequency','b',2,'00:03:00','00:03:00'],
            ['bus-frequency','d',1,'00:00:00','00:00:00'],['bus-frequency','c',2,'00:04:00','00:04:00']]
        rows['frequencies.txt']=[['trip_id','start_time','end_time','headway_secs','exact_times'],
            ['metro-frequency','00:00:00','24:00:00',300,0],['bus-frequency','00:00:00','24:00:00',300,1]]
        rows['pathways.txt']=[['pathway_id','from_stop_id','to_stop_id','pathway_mode','is_bidirectional','traversal_time','signposted_as'],
            ['transfer','b','d',1,1,150,'测试站内换乘通道'],['entry','e','b',1,1,90,'测试进出站通道']]
        rows['transfers.txt']=[['from_stop_id','to_stop_id','transfer_type','min_transfer_time'],['b','d',2,240]]
    with tempfile.TemporaryDirectory() as directory:
        feed = Path(directory)/'synthetic.zip'
        with zipfile.ZipFile(feed,'w') as archive:
            for name, table in rows.items():
                text=io.StringIO(newline='')
                csv.writer(text).writerows(table)
                archive.writestr(name,text.getvalue())
        return compile_feed(feed, output, '测试专用复杂换乘（虚构）' if complex_data else '测试专用公交地铁（虚构）', 'SYNTHETIC TEST DATA — NOT FOR REAL NAVIGATION')


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output',type=Path)
    parser.add_argument('--complex',action='store_true',help='Include headways, station hierarchy, pathways and minimum transfers')
    parser.add_argument('--date',help='YYYYMMDD in Asia/Shanghai; defaults to today')
    args=parser.parse_args()
    day=datetime.strptime(args.date,'%Y%m%d').date() if args.date else datetime.now(ZoneInfo('Asia/Shanghai')).date()
    print(make_demo(args.output,day,args.complex))
