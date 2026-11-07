"""Generate a wholly synthetic road network and compile it using BRouter 1.7.10.

Usage: python tools/roads/generate_test_fixture.py UNPACKED_BROUTER_BUILD_DIR
The directory must contain the upstream all.jar, both release dependency jars,
and lookups.dat/all.brf/trekking.brf/softaccess.brf from tag v1.7.10.
The artificial square has a walking-only diagonal; it contains no real OSM data.
"""
from pathlib import Path
import struct
import subprocess
import sys


def varint(n):
    result = bytearray()
    while n > 127:
        result.append((n & 127) | 128)
        n >>= 7
    result.append(n)
    return bytes(result)


def integer(field, value):
    return varint(field << 3) + varint(value)


def signed(field, value):
    return integer(field, (value << 1) ^ (value >> 63))


def blob(field, value):
    return varint((field << 3) | 2) + varint(len(value)) + value


def block(kind, payload):
    body = blob(1, payload)
    header = blob(1, kind.encode()) + integer(3, len(body))
    return struct.pack('>I', len(header)) + header + body


def main(root):
    root = Path(root).resolve()
    work = root / 'synthetic'
    work.mkdir(exist_ok=True)
    for name in ['nodes','ways','nodes55','ways55','unodes55','segments']:
        (work/name).mkdir(exist_ok=True)
    strings = ['', 'highway', 'residential', 'footway', 'bicycle', 'no', 'motor_vehicle', 'name', 'Synthetic street']
    table = b''.join(blob(1, s.encode()) for s in strings)
    nodes = b''
    for i, (lat, lon) in enumerate([(1,103),(1,103.01),(1.01,103.01),(1.01,103),
                                   (1.0001,103.0001),(1.00015,103.0001)], 1):
        nodes += blob(1, signed(1,i)+signed(8,round(lat*1e7))+signed(9,round(lon*1e7)))
    def way(i, refs, keys, values):
        deltas = [refs[0]] + [b-a for a,b in zip(refs,refs[1:])]
        encoded = b''.join(varint((n<<1)^(n>>63)) for n in deltas)
        return blob(3, integer(1,i)+blob(2,bytes(keys))+blob(3,bytes(values))+blob(8,encoded))
    ways = way(10,[1,2,3,4,1],[1,7],[2,8])+way(11,[1,3],[1,4,6],[3,5,5])
    # A disconnected short road forces upstream waypoint-island recovery.
    ways += way(12,[5,6],[1,7],[2,8])
    data = block('OSMHeader',blob(4,b'OsmSchema-V0.6'))+block('OSMData',blob(1,table)+blob(2,nodes+ways))
    (work/'synthetic.pbf').write_bytes(data)
    source = '''import java.io.File;
import btools.mapcreator.*;
class CompileSynthetic {
 public static void main(String[] args) throws Exception {
  System.setProperty("avoidMapPolling","true");
  File r=new File(args[0]), w=new File(r,"synthetic");
  OsmFastCutter.doCut(new File(r,"lookups.dat"),new File(w,"nodes"),new File(w,"ways"),new File(w,"nodes55"),new File(w,"ways55"),new File(w,"border.dat"),new File(w,"relations.dat"),new File(w,"restrictions.dat"),new File(r,"all.brf"),new File(r,"trekking.brf"),new File(r,"softaccess.brf"),new File(w,"synthetic.pbf"),null);
  new PosUnifier().process(new File(w,"nodes55"),new File(w,"unodes55"),new File(w,"border.dat"),new File(w,"bordernodes.dat"),w.getAbsolutePath(),null);
  new WayLinker().process(new File(w,"unodes55"),new File(w,"ways55"),new File(w,"bordernodes.dat"),new File(w,"restrictions.dat"),new File(r,"lookups.dat"),new File(r,"all.brf"),new File(w,"segments"),"rd5");
 }
}'''
    (root/'CompileSynthetic.java').write_text(source)
    subprocess.run(['java','-cp',str(root/'*'),str(root/'CompileSynthetic.java'),str(root)],check=True)
    output = Path(__file__).resolve().parents[2]/'app/shared/src/androidHostTest/resources/roads'
    output.mkdir(parents=True,exist_ok=True)
    for file in (work/'segments').glob('*.rd5'):
        (output/file.name).write_bytes(file.read_bytes())
        print(output/file.name)


if __name__ == '__main__':
    main(sys.argv[1])
