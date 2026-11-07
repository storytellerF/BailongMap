# Application delivery acceptance

Keep the specification, source evidence, real data and generated reports together outside the checkout. Relative paths resolve beside the JSON file. Prerequisites are the project's normal Android host-test build prerequisites; a phone, emulator and routing server are unnecessary.

Use input expectations verified from the source, not values copied from a failed planner to make it pass. Include a successful journey on every delivered line/direction where practical, weekday/weekend and calendar exceptions present in the delivery, overnight service if supplied, a transfer with its minimum time, and negative cases for known forbidden transfers or closed service. Test first/last service boundaries where those are part of the advertised coverage. If coverage is too large for one run, divide the acceptance specifications into groups and retain each run's evidence; report exactly which routes/windows were tested.

For full offline delivery include successful `TRANSIT_WALK` cases from street points to street points away from the stop coordinates. Include `CAR`, `BICYCLE` and `WALK` cases when those road modes are requested. Include `TRANSIT_BICYCLE` only with verified permission, plus an unavailable case where carrying is forbidden if supplied by the source. Street cases must supply actual `.rd5` files; there is no online fallback in these tests. `TIMETABLE` intentionally tests only the timetable API with labelled straight-line stop access, and cannot establish full offline street routing.

Example shape only; replace every location, date, route and expectation with verified data. The placeholder file below is not runnable navigation data:

```json
{
  "package": "region.bltransit",
  "roads": ["roads/E100_N0.rd5"],
  "cases": [
    {
      "mode": "TRANSIT_WALK",
      "departure": "2026-11-07T00:00:00Z",
      "origin": [1.0, 103.001],
      "destination": [1.01, 103.009],
      "expect": "route",
      "modes": ["WALK", "TRANSIT", "WALK"],
      "routes": ["REPLACE_WITH_VERIFIED_ROUTE"],
      "min_seconds": 120,
      "max_seconds": 3600
    }
  ]
}
```

`departure` is an ISO-8601 instant with UTC offset (for example `Z` or `+08:00`); origin/destination arrays are `[latitude, longitude]`. Modes are `TIMETABLE`, `TRANSIT_WALK`, `TRANSIT_BICYCLE`, `CAR`, `BICYCLE`, `WALK`. For positive cases, `modes` is the exact expected leg-mode sequence, using `SUBWAY` for a subway ride and `TRANSIT` for a bus. Station pathways create extra `WALK` legs. `routes` lists required ride-name prefixes; frequency labels can append a suffix. Duration bounds include waiting and transfers and must come from verified schedules/headways and realistic access expectations. `max_seconds` is required and finite. Negative cases use `"expect": "unavailable"`, omit `modes/routes`, and still supply a valid departure and duration bound.

Run from the project root:

```text
python tools/transit/run_acceptance.py /path/to/delivery/acceptance.json
```

The runner writes `acceptance.log` and, only after every case succeeds and at least one journey is successful, `acceptance.result.json`. It reruns the filtered host-test task instead of reusing an old test result. A failed run removes old acceptance evidence. Cases use the real importer, fixed departure clock, BRouter profiles and transit planner. Package/road/specification hashes bind the result to these inputs. The result is Android host verification, not a physical-device import test.

Finish with `verify_package.py --acceptance ... --require-routing`; add `--require-offline` for full offline transit. It checks the source/package match, intended-use date, omitted data, and acceptance hashes. Read the report before delivery. `artifact_verified_only` means routing was not verified; `declared_journeys_verified` proves the declared cases only. Neither status proves source facts or every journey in the region.
