"""Describe source information not represented by the offline timetable schema."""

USED_COLUMNS = {
    'agency.txt': {'agency_id', 'agency_name', 'agency_url', 'agency_timezone'},
    'stops.txt': {'stop_id', 'stop_name', 'stop_lat', 'stop_lon', 'location_type', 'parent_station', 'stop_access'},
    'routes.txt': {'route_id', 'agency_id', 'route_type', 'route_short_name', 'route_long_name', 'continuous_pickup', 'continuous_drop_off'},
    'trips.txt': {'trip_id', 'route_id', 'service_id', 'bikes_allowed'},
    'stop_times.txt': {'trip_id', 'stop_id', 'stop_sequence', 'arrival_time', 'departure_time', 'pickup_type', 'drop_off_type', 'continuous_pickup', 'continuous_drop_off'},
    'calendar.txt': {'service_id', 'start_date', 'end_date', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'},
    'calendar_dates.txt': {'service_id', 'date', 'exception_type'},
    'frequencies.txt': {'trip_id', 'start_time', 'end_time', 'headway_secs', 'exact_times'},
    'transfers.txt': {'from_stop_id', 'to_stop_id', 'from_route_id', 'to_route_id', 'from_trip_id', 'to_trip_id', 'transfer_type', 'min_transfer_time'},
    'pathways.txt': {'pathway_id', 'from_stop_id', 'to_stop_id', 'pathway_mode', 'is_bidirectional', 'traversal_time', 'length', 'signposted_as', 'reversed_signposted_as'},
}


def audit(tables):
    """Report populated omitted columns/tables; no row IDs or coordinates are logged."""
    ignored = {}
    for table, rows in sorted(tables.items()):
        populated = {key for row in rows for key, value in row.items() if value}
        columns = sorted(populated - USED_COLUMNS.get(table, set()))
        if columns:
            ignored[table] = {'rows': len(rows), 'columns': columns}
    warnings = []
    if any(row.get('timepoint') == '0' for row in tables.get('stop_times.txt', [])):
        warnings.append('Source contains estimated stop times (timepoint=0); do not describe them as exact departures.')
    if any(row.get('block_id') for row in tables.get('trips.txt', [])):
        warnings.append('Vehicle blocks are not used for in-seat interlining; staying aboard across trip IDs is not planned.')
    if tables.get('shapes.txt'):
        warnings.append('Transit shapes are omitted; transit line geometry remains schematic.')
    if any(name.startswith('fare_') and rows for name, rows in tables.items()):
        warnings.append('Fare data is omitted; the package cannot quote or optimize fares.')
    if ignored:
        warnings.append('Populated source fields are omitted; review omitted_data before describing package capabilities.')
    return {'omitted_data': ignored, 'warnings': warnings}
