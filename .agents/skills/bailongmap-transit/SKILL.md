---
name: bailongmap-transit
description: Prepare and verify user-importable BailongMap .bltransit packages from verified bus/subway material or GTFS, including compatible BRouter roads for complete offline journeys and application routing acceptance. Use inside a BailongMap checkout when preparing or updating transit deliveries.
---

# Prepare offline transit data

Deliver a verified `.bltransit` file for Settings → 离线公共交通. For a complete offline journey also deliver compatible BRouter `.rd5` road segments for Settings → 离线道路. Use the project's compiler and application acceptance runner; no server is required. A successful compile alone does not establish usability or source accuracy.

## Source preparation

Use an existing licensed GTFS ZIP when available. Otherwise convert verified material into UTF-8 GTFS CSV files at the ZIP root. Obtain missing coordinates, operating dates, timezone, stop order and stop times or verified headway windows; never fabricate them. First/last trains alone do not define an all-day timetable. Preserve the original source and document transformations, source URLs/documents, publication/retrieval dates, license, intended use dates and known omissions in a provenance file beside the delivery. Keep real user data and acceptance files outside the checkout unless the user explicitly requests repository inclusion.

Confirm the intended coverage: region, lines/directions, operating days/time windows, and requested modes. Compare these to the available source before claiming complete coverage. Keep verified partial data usable, but label it partial and list missing lines/windows. Do not quietly shorten dates, drop unsupported services, synthesize missing departures, or convert unknown bicycle permission into permission.

The current v3 compiler accepts bus (`route_type=3`) and subway (`1`), a single
agency timezone, calendar exceptions, explicit stop times below `48:00:00`, and
pickup/drop-off prohibitions. Coverage is at most 366 days. It supports:

- Scheduled headways (`exact_times=1`), expanded into distinct departures.
- Unscheduled headways (`exact_times=0`), retained as windows. The app budgets one
  whole headway and labels the journey as estimated; never describe it as an exact
  departure schedule. Ask for headway windows and template stop-to-stop times.
- Station/platform/entrance/boarding-area hierarchy; directed station pathways
  with traversal times. Walkway/gate length may be converted at 1.2 m/s; obtain
  explicit traversal times for other modes. Preserve `stop_access` semantics.
- Transfer types 0–3, minimum times and forbidden transfers, including route/trip
  selectors. Preserve the original IDs and conditions; ambiguous rules fail.

Preserve trip `bikes_allowed`: 0/blank is unknown, 1 permits carrying a bicycle,
2 forbids it. Never infer permission from the presence of bicycle roads.

The app still accepts v1/v2 packages. Do not strip unsupported fields to bypass an
error: in-seat interlining (transfer types 4/5), on-demand windows, continuous
boarding and multiple timezones remain unsupported. Shapes may be present but are
not rendered. Fare calculation, real-time arrivals, accessibility guarantees and
street-level walking remain outside this package's scope.

For explicitly requested tests, synthetic data is allowed: use
`tools/transit/generate_demo.py OUTPUT.bltransit --complex` and preserve all
SYNTHETIC / test-only labels. Never mix synthetic schedules into real user data.

## Compile and verify every delivery

From the checkout root, use Python 3.10+ and install `tools/transit/requirements.txt` (prefer a virtual environment), then run:

```text
python tools/transit/build_transit.py INPUT.zip OUTPUT.bltransit --name "Region name" --source "Source and license attribution" --start YYYYMMDD --end YYYYMMDD
```

Select dates covering the user's actual intended use, not a convenient past date that passes tests. Omit `--start/--end` only if the feed's complete coverage is already <=366 days. On Windows install `tzdata` when needed. The compiler validates references/calendar/times, remaps IDs, builds SQLite indexes and packages a SHA-256 manifest.

For production data, run the [MobilityData Canonical GTFS Validator](https://github.com/MobilityData/gtfs-validator) locally using an official release. Resolve ERROR notices and record reviewed warnings; retain its reports beside the delivery. If the validator or source evidence cannot be checked, report that limitation and do not describe the result as production-verified. The project compiler checks its supported subset; it is not a complete GTFS conformance validator.

```text
java -jar /path/to/gtfs-validator-cli.jar -i INPUT.zip -o /path/to/delivery/gtfs-validation
```

Run the source/package audit:

```text
python tools/transit/verify_package.py OUTPUT.bltransit --source INPUT.zip --as-of YYYYMMDD --report delivery.report.json
```

Review `omitted_data` and every warning. This detects expired coverage, source/output mismatches and omitted populated fields/tables, including shapes, fares, estimated times and vehicle blocks. It intentionally reports `artifact_verified_only` until application routing evidence is supplied. Never change the report by hand.

For complete offline use, obtain the necessary standard `.rd5` files from the [official BRouter segment directory](https://brouter.de/brouter/segments4/) or a compatible upstream BRouter build. Cover the full journey region, stop entrances, connecting streets and any neighboring regions required by detours. Test the exact supplied files against the bundled engine/profiles. Do not use map tiles, arbitrary OSM ZIPs, a renamed PBF, or straight-line connectors as road data. Deliver the files together; importing a new road selection replaces the existing road region.

Read [delivery acceptance](references/delivery-acceptance.md), prepare source-backed journey expectations, then run:

```text
python tools/transit/run_acceptance.py acceptance.json
python tools/transit/verify_package.py OUTPUT.bltransit --source INPUT.zip --as-of YYYYMMDD --acceptance acceptance.json --require-routing --require-offline --report delivery.report.json
```

The runner imports the exact package and roads and uses the app's Android SQLite importer, BRouter engine and local transit planner, without online fallbacks. For a timetable-only request, test `TIMETABLE` cases and omit `--require-offline`; describe that narrower scope. Do not omit a failed requirement merely to obtain a passing report. Missing Android build prerequisites or failed acceptance means the delivery is unverified: identify the failed stage and retain partial results with that label.

If a command fails, correct verified input or explain the unsupported feature; never hand-edit the database or claim success. Existing output is preserved on a failed build. Use `python -m unittest discover -s tools/transit/tests` when changing these tools. Re-run acceptance and verification after any input, date-window, package or road change; evidence is bound to those exact file hashes.

Deliver clickable links to the package, required road files, provenance and validation report. State the coverage dates, lines/directions and time-window gaps, source/license, stop/route counts, tested modes/journeys, and whether verification used Android host tests or a device. A production-ready claim requires source verification, reviewed GTFS validation, and successful application cases for the requested scope. A finite sample does not prove every possible journey; do not promise universal routing or factual accuracy based on file validation alone.

Explain the remaining model limits: default transfer allowance is 120 seconds; the app checks up to two nearby accessible stops at each end (2 km walking / 5 km cycling), so it can miss feasible routes. Transit geometry and station links without shapes remain schematic. Bicycle + transit + bicycle means carrying an own bicycle and requires explicit permission on every ridden trip; parked/shared-bike handoffs are not implemented. Importing replaces the current transit/road selection; map tiles remain separate.

For the maintained schema and package contract, read `tools/transit/schema.sql` and the transit section of `DEVELOPMENT.md` when debugging compatibility. For standard field meanings, use the official [GTFS Schedule reference](https://gtfs.org/documentation/schedule/reference/).
