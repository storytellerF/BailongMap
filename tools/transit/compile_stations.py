"""GTFS hierarchy, footpath and transfer-rule compilation for package v2."""
import math


def compile_stations(tables, stops, stop_ids, routes, route_ids, trips, trip_ids, require):
    kind = {s: int(r.get('location_type') or 0) for s, r in stops.items()}
    parent = {s: r.get('parent_station') or None for s, r in stops.items()}
    def station(s):
        visited = set()
        while parent[s]:
            require(s not in visited and parent[s] in stops, 'Invalid/cyclic station hierarchy')
            visited.add(s)
            s = parent[s]
        return s if kind[s] == 1 else None
    for sid, row in stops.items():
        require(kind[sid] in range(5), 'Unknown location_type')
        require(row.get('stop_access','') in ('','0','1'), 'Invalid stop_access')
        require(kind[sid] != 1 or parent[sid] is None, 'Station cannot have parent')
        if parent[sid]:
            require(parent[sid] in stops and kind[parent[sid]] == (0 if kind[sid] == 4 else 1), 'Invalid parent station type')
        if kind[sid] in (2,3,4):
            require(parent[sid], 'Entrance/node/boarding area requires a parent')
        station(sid)
        for field in ['stop_lat','stop_lon','stop_name']:
            cursor = sid
            while not row.get(field) and parent[cursor]:
                cursor = parent[cursor]
                if stops[cursor].get(field): row[field] = stops[cursor][field]
        require(row.get('stop_lat') and row.get('stop_lon') and row.get('stop_name'), 'Stop needs coordinates and name, directly or inherited')
    def meters(a,b):
        x,y=stops[a],stops[b]
        la,lb=math.radians(float(x['stop_lat'])),math.radians(float(y['stop_lat']))
        dl=math.radians(float(y['stop_lon'])-float(x['stop_lon']))
        h=math.sin((lb-la)/2)**2+math.cos(la)*math.cos(lb)*math.sin(dl/2)**2
        return 12742000*math.asin(math.sqrt(min(1,max(0,h))))
    footpaths={}
    path_stations=set()
    def edge(a,b,seconds,name):
        require(a in stops and b in stops and a!=b and 0<seconds<=172800, 'Invalid footpath')
        key=(stop_ids[a],stop_ids[b])
        if key not in footpaths or seconds<footpaths[key][0]: footpaths[key]=(seconds,name)
        require(len(footpaths)<=100000, 'Too many footpaths; split region')
    path_ids=set()
    for row in tables.get('pathways.txt',[]):
        a,b=row['from_stop_id'],row['to_stop_id']
        require(row['pathway_id'] not in path_ids, 'Duplicate pathway_id'); path_ids.add(row['pathway_id'])
        require(a in stops and b in stops and kind[a]!=1 and kind[b]!=1 and stops[a].get('stop_access')!='1' and stops[b].get('stop_access')!='1' and station(a)==station(b) and station(a) is not None, 'Pathways must connect nodes within one station')
        mode=int(row['pathway_mode']); bidirectional=row['is_bidirectional']
        require(mode in range(1,8) and bidirectional in ('0','1') and not(mode==7 and bidirectional=='1'), 'Invalid pathway mode/direction')
        if row.get('traversal_time'): duration=int(row['traversal_time'])
        else:
            require(mode in (1,6,7) and row.get('length'), 'Pathway requires traversal_time (or walkway/gate length)')
            length=float(row['length']); require(math.isfinite(length) and length>=0,'Invalid pathway length')
            duration=max(1,math.ceil(length/1.2))
        path_stations.add(station(a))
        edge(a,b,duration,row.get('signposted_as') or '站内通道')
        if bidirectional=='1': edge(b,a,duration,row.get('reversed_signposted_as') or row.get('signposted_as') or '站内通道')
    # Only infer platform transfers where the feed supplies no pathway graph for that station.
    groups={}
    for s in stops:
        if kind[s]==0 and station(s) and station(s) not in path_stations: groups.setdefault(station(s),[]).append(s)
    for children in groups.values():
        require(len(children)<=100, 'Station too large for inferred platform transfers; supply pathways')
        for a in children:
            for b in children:
                if a!=b: edge(a,b,max(1,math.ceil(meters(a,b)/1.2)),'站内换乘（估算）')
    rules=[]
    def endpoints(s):
        require(s in stops and kind[s] in (0,1), 'Transfer must reference a stop or station')
        return [c for c in stops if kind[c]==0 and station(c)==s] if kind[s]==1 else [s]
    for row in tables.get('transfers.txt',[]):
        rule_type=int(row.get('transfer_type') or 0)
        require(rule_type in (0,1,2,3), 'In-seat transfers (4/5) not yet supported')
        duration=int(row.get('min_transfer_time') or (120 if rule_type==0 else 0))
        require(0<=duration<=172800 and (rule_type!=2 or row.get('min_transfer_time')), 'Missing/invalid min_transfer_time')
        selectors=[]
        for side in ['from','to']:
            route=row.get(side+'_route_id'); trip=row.get(side+'_trip_id')
            require(not route or route in routes, 'Unknown transfer route')
            require(not trip or trip in trips, 'Unknown transfer trip')
            require(not(route and trip) or trips[trip]['route_id']==route, 'Transfer trip/route mismatch')
            selectors += [route_ids[route] if route and not trip else -1, trip_ids[trip] if trip else -1]
        fr,ft,tr,tt=selectors
        for a in endpoints(row['from_stop_id']):
            for b in endpoints(row['to_stop_id']):
                rules.append((stop_ids[a],stop_ids[b],fr,ft,tr,tt,rule_type,duration))
                require(len(rules)<=100000,'Too many expanded transfer rules')
                if a!=b and rule_type!=3 and not(station(a)==station(b) and station(a) in path_stations):
                    edge(a,b,max(1,math.ceil(meters(a,b)/1.2)),'换乘步行（估算）')
    # Reject ambiguous overlapping selectors at equal GTFS specificity.
    def rank(rule):
        fr,ft,tr,tt=rule[2:6]
        return 5 if ft>=0 and tt>=0 else 4 if (ft>=0 and tr>=0) or (fr>=0 and tt>=0) else 3 if ft>=0 or tt>=0 else 2 if fr>=0 and tr>=0 else 1 if fr>=0 or tr>=0 else 0
    template_routes={trip_ids[t]:route_ids[r['route_id']] for t,r in trips.items()}
    def side_overlap(ar,at,br,bt):
        if ar>=0 and br>=0 and ar!=br: return False
        if at>=0 and bt>=0 and at!=bt: return False
        trip=at if at>=0 else bt
        route=ar if ar>=0 else br
        return trip<0 or route<0 or template_routes[trip]==route
    grouped={}
    for rule in rules:
        key=(rule[0],rule[1],rank(rule))
        for other in grouped.get(key,[]):
            require(not(side_overlap(rule[2],rule[3],other[2],other[3]) and side_overlap(rule[4],rule[5],other[4],other[5])), 'Ambiguous transfer rules of equal specificity')
        grouped.setdefault(key,[]).append(rule)
    compiled_stops=[(stop_ids[s],r['stop_name'],float(r['stop_lat']),float(r['stop_lon']),kind[s],stop_ids[parent[s]] if parent[s] else None,
                     int((kind[s]==0 and (station(s) not in path_stations or r.get('stop_access')=='1')) or kind[s]==2)) for s,r in stops.items()]
    return compiled_stops,[(a,b,*v) for (a,b),v in footpaths.items()],rules
