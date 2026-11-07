# Development

Use JDK 21 and Android SDK Platform 37.1 to build the project. The updated Compose
dependencies require compile SDK 37.1; the minimum and target SDK versions remain
33 and 37 respectively.

Run `./gradlew check --no-daemon` for the checks used by CI. On Windows, use
`gradlew.bat` instead of `./gradlew`.

Material3 migration regression tests run without a device using Robolectric:
`./gradlew :app:shared:testAndroidHostTest --tests '*MaterialMigrationTest'`.
They cover independent initially hidden map sheet states, full expansion,
close/reopen, search result selection, and clearing the search query. The three
map sheets share `rememberMapSheetState`, which permits only Hidden and Expanded.

To investigate Gradle compatibility warnings, run
`./gradlew help --no-daemon --no-configuration-cache --warning-mode all -Dorg.gradle.deprecation.trace=true`
(quote the entire `-D...` argument in PowerShell). With Gradle 9.8.1, AGP 9.3.1
calls deprecated `Configuration.setVisible` internally, and Kotlin 2.4.21's
Native toolchain initializes the deprecated `KotlinNativeBundleArtifactsTypes`
attribute in `KotlinNativeBundleArtifactFormat`. These require upstream plugin
fixes before Gradle 11 and 10 respectively; keep warnings visible and recheck
when updating those plugins. The generic warning's recommendation to upgrade
Kotlin to 2.1 does not resolve this project, which already uses a newer version.

To verify Appium test compilation without a device or Appium server, run
`./gradlew :appiumTests:compileTestKotlin --no-daemon`. Running device tests requires
Appium server 3 or newer for Appium Java Client 11.

With `adb` on `PATH`, a connected Android device, and an Appium server running,
run `./scripts/appium-test.sh -PappiumTags=navigation` for navigation end-to-end
tests. The script uses `ANDROID_UDID` when set, otherwise the first connected
device reported by `adb devices`. It installs the debug APK before running the
tests and forwards command-line arguments to both Gradle invocations. An install
failure stops the script; otherwise it returns the test command's exit code.
The script requires no AI plugin or device-lock helper. AI tooling manages any
shared-device locking externally.

Run `./scripts/appium-test.sh -PappiumTags=map-ui` for native-map startup and
bottom-sheet regression tests. They verify initially hidden sheets, settings and
offline sheet dismissal by Back and dragging, and reopening place details.
`APP_PACKAGE` and `APP_ACTIVITY` can target an independently installed test build;
use the fully qualified activity name when its namespace differs from the package.
For an already installed build, run `:appiumTests:test -PappiumTags=map-ui`
with `-x :app:androidApp:installDebug` to skip replacing it.

The navigation test injects mock locations through ADB, selects a three-stage
walking–subway–bicycle option, advances at transfer points, and restores real
location afterward. It uses `adb reverse` with local map-style and OpenTripPlanner
fixtures, so the test does not depend on internet access.

MapLibre Compose uses 0.20.0. The Android app packages the matching OpenGL runtime via
`maplibre-compose-runtime-opengl-android`. The shared API dependency alone only
provides the JNI bridge; omitting the runtime causes a startup
`UnsatisfiedLinkError` for `libmaplibre-native-c.so`. Keep the API and runtime on
the same version as described in the [MapLibre setup guide](https://maplibre.org/maplibre-compose/getting-started/).
Camera commands use `CameraUpdate` and `CameraFit`,
and the location marker uses `LocationIndicatorLayer`. Offline UI observes
`OfflineStorage.state` and distinguishes loading, ready, and failed initialization.
`OfflineHost` owns offline actions on an injected serial background dispatcher;
the screen closes it on disposal and collects its state and effects with its lifecycle.
Offline storage is a native-only API; the shared UI currently builds for Android.

JS, Wasm, and Kotlin web tooling dependency installation share a Gradle build
service with one concurrent user. Yarn Classic's network mutex can return success
without producing `build/wasm/yarn.lock` when installations overlap on Windows.
The service serializes only dependency installation; compilation and tests can
still run in parallel, and lockfile validation remains enabled. Remove it once
the bundled package manager reliably handles concurrent installations.

## Offline transit packages

The optional project skill lives in `.agents/skills/bailongmap-transit`; normal
compiler and app workflows work without AI tooling. The maintained schema is
`tools/transit/schema.sql`, consumed by `build_transit.py`. Version 1, 2 and 3 packages are
ZIP files with exactly `manifest.json` and `network.sqlite`. SQLite application ID
is 1112298578; the current schema user version is 3. The manifest contains format
`bailongmap-transit`, version, database SHA-256, name, source, timezone, start/end
YYYYMMDD, and stop/route/connection counts. Dates are materialized per service;
connections store integer IDs and seconds since service-day origin. Route mode
is GTFS 1 (subway) or 3 (bus); pickup/dropoff 0 permits and 1 forbids.

The Android importer bounds compressed/expanded bytes, verifies the digest and
metadata, checks SQLite integrity, validates the supported schema/data and stages
a private database. Confirmation atomically replaces the active file. The previous
file survives a failed import or publication. The app restores the active package
on startup and deletes abandoned import files; no external-storage permission is
needed because the picker uses Android's document provider.

`TransitHost` owns import UI state and effects on an injected serial background
dispatcher. Repository file/SQLite work uses Dispatchers.IO. The time-dependent earliest-arrival search
runs on Dispatchers.Default, with cancellation checks and a bounded work budget. Local service
day origin is timezone-local noon minus 12 hours, respecting GTFS DST semantics.
Connections from up to two previous service days, today and tomorrow are merged
for a 24-hour departure window, covering extended overnight trips near DST changes. Ride expansion follows a vehicle through prohibited pickup/dropoff stops. Search
labels retain the source trip and original alighting node/time across walking,
so route/trip-scoped transfer rules cannot be bypassed via an intermediate node.
Pareto labels preserve arrival-time and transfer-deadline dominance. See README for supported input limits.

Verification (from the checkout root, using the transit tools' virtual environment):

```shell
python -m venv tools/transit/.venv
tools/transit/.venv/bin/python -m pip install -r tools/transit/requirements.txt
tools/transit/.venv/bin/python -m unittest discover -s tools/transit/tests
./gradlew :app:shared:testAndroidHostTest :app:androidApp:assembleDebug
```

Create the environment only if it does not already exist. On Windows use
`tools/transit/.venv/Scripts/python.exe` in place of `tools/transit/.venv/bin/python`,
and `gradlew.bat` in place of `./gradlew`. Use that environment's Python for the
transit tools below as well; activation is optional when using its explicit path.
The importer integration fixture is synthetic, not real navigation data.
Regenerate its maintained binary after changing the compiler or test inputs with
`python tools/transit/generate_test_fixture.py`. Its canonical input is `fixture()`
in `tools/transit/tests/test_build_transit.py`. Host tests cover review/confirmation
and concurrent/cancelled imports; router tests cover transfer times, same-vehicle
continuation and boarding restrictions. Android tests consume the compiler output
and verify import, persistence, integrity rejection and local transfer planning.

For manual device verification, build a current licensed regional package with the
skill, open Settings → 离线公共交通, select it in the document picker, confirm it,
and plan between stops during operating hours. Restart and repeat with networking
disabled using a previously available destination. No device or server is needed
for the host tests above.

Version 2 adds stop `kind/parent/access`, trip `source_trip`, and indexed frequency
windows, directed footpaths and transfer-rule tables. Exact headways are expanded
by the compiler; unscheduled headways remain windows and template trips, excluded
from scheduled departure queries. The runtime budgets one whole headway after
arrival (or window start), preserving template running/dwell offsets and checking
that the implied first-stop departure is before window end. It may reject a
late-window boarding when no conservative estimate fits; it does not promise live
arrivals. Selector specificity and ambiguous-rule rejection follow the GTFS
Schedule reference. The frozen `legacy-v1.bltransit` fixture preserves the previous
compiler's output for compatibility; it must not be regenerated with the current
compiler. The same applies to `legacy-v2.bltransit`.

Synthetic device packages (not real navigation data):

```shell
python tools/transit/generate_demo.py build/transit-demo.bltransit --date 20261102
python tools/transit/generate_demo.py build/transit-complex-demo.bltransit --date 20261102 --complex
```

Use the device's current Asia/Shanghai date, or omit `--date` for today. Both
packages carry explicit test-only attribution; coverage is yesterday through
seven days after that date. The complex variant includes an unscheduled metro,
a scheduled headway bus, a station/entrance/platform hierarchy, bidirectional
pathways and a 240-second minimum platform transfer. The known test route is
39.9030,116.4074 → 39.9454,116.4074. Use mock location on a test device and select
the `.bltransit` file in Android's document picker. Keep real application data
separate when testing. `generate_test_fixture.py` regenerates the current basic
and complex host-test fixtures; their fixed date is intentional.

Version 3 adds GTFS trip `bikes_allowed` (0 unknown, 1 allowed, 2 forbidden),
preserved through frequency expansion. Older packages have unknown permission.
Production door-to-door queries supply street connector legs to `LocalTransitRouter`;
the direct timetable API retains labelled straight-line estimates for diagnostics.
Connector durations determine whether a trip is catchable and participate in
arrival optimization. Station transfers use the package's GTFS pathway graph.

## Road routing and journey options

`JourneyOptionsPlanner` runs independent providers with cancellation and individual
timeouts. Results update each category immediately. UI selection is explicit,
including when only one result is available. New requests cancel the old request
and check a generation before publishing. `RoadJourneyPlanner` prefers local
BRouter then the same mode's online OSRM provider. FOSSGIS foot/bike requests share
a minimum one-second start interval. `TransitJourneyPlanner` preserves walking
results when an optional own-bicycle search fails or times out. The optional search
runs alongside walking with a 12-second budget from request start, including the
bicycle-permission check; it cannot add that wait after a slow walking result.
Carrying a bicycle
requires explicit permission on every trip; parking and rental are not inferred.

The Android dependency `app/shared/libs/brouter-1.7.10-ro.jar` is the upstream
routing-only Java 11 binary from
[BRouter v1.7.10](https://github.com/abrensch/brouter/releases/tag/v1.7.10).
SHA-256: `598b647f2384a22a43495de7aee44f31e037062ec9c317e3bcc58cf799dbd972`.
The adjacent MIT license and bundled `assets/brouter` lookups/profiles come from
that tag. Update these together and repeat import/routing tests. Profiles are
`car-fast`, `trekking` and `shortest` (foot). `doRun` executes on an IO coroutine
worker without starting BRouter's Thread subclass. Cancellation requests engine
termination and joins the worker before releasing the data lock. Road operations
are serialized. Imports validate integrity and atomically publish an immutable
generation; failed imports preserve active data and successful replacement removes
the previous generation.

`tools/roads/generate_test_fixture.py` is the canonical source of the tiny road
fixture: a wholly synthetic square with a foot-only diagonal and a nearby disconnected
road to exercise waypoint rematching. It uses the upstream
map-creator pipeline. Regeneration requires Java 11+ and an external directory with
v1.7.10 all.jar, release osmosis/protobuf jars, and upstream
`misc/profiles2/{lookups.dat,all.brf,trekking.brf,softaccess.brf}`:

```shell
python tools/roads/generate_test_fixture.py /path/to/upstream-build
```

The generated `E100_N0.rd5` is artificial test data. `OfflineRoadsTest` verifies
import/persistence and three profiles with the embedded engine, including exclusion
of the foot-only shortcut for cars and bicycles. Options tests cover progressive
results, provider isolation and cancellation; transit tests cover connector costs
and bicycle permission. Frozen legacy transit fixtures must not be regenerated.

## Transit delivery verification

`tools/transit/verify_package.py` recompiles the claimed GTFS source with the current
compiler and checks it against the delivered v3 manifest/database, SQLite integrity,
intended-use dates and street-accessible stops. Run build and verification with the
same Python/SQLite environment; different compiler/database versions may require
rebuilding the package. `feed_audit.py` reports populated omitted columns/tables,
including estimated source times and vehicle-block semantics. This is a supported
subset audit, not complete GTFS conformance validation or verification of source
facts. Use the local MobilityData Canonical GTFS Validator and review source
evidence for production deliveries.

```shell
python tools/transit/verify_package.py region.bltransit --source region.zip --as-of YYYYMMDD --report delivery.report.json
python tools/transit/run_acceptance.py /path/to/delivery/acceptance.json
python tools/transit/verify_package.py region.bltransit --source region.zip --as-of YYYYMMDD --acceptance /path/to/delivery/acceptance.json --require-routing --require-offline --report delivery.report.json
```

The first command reports `artifact_verified_only`. The final command requires
fresh, matching acceptance evidence; `--require-offline` additionally requires a
successful `TRANSIT_WALK` case using supplied road data. Timetable-only verification
uses `TIMETABLE` cases and omits that flag. Reports list the scope and modes actually
checked. A passing report establishes declared samples only.

The external acceptance JSON has `package`, optional `roads`, and nonempty `cases`.
Paths resolve relative to that file. Each case supplies `mode`, ISO-8601 `departure`,
`origin`/`destination` as latitude/longitude arrays, `expect` (`route` or
`unavailable`) and `max_seconds`; positive cases also declare the exact leg `modes`
and optional required route-name prefixes in `routes` and `min_seconds`. Modes are
`TIMETABLE`, `TRANSIT_WALK`, `TRANSIT_BICYCLE`, `CAR`, `BICYCLE`, `WALK`. Street modes
require `.rd5` files. Use source-backed cases for advertised lines, directions and
calendar/time-window boundaries; include the corresponding transfer and bicycle
constraints when present.

`run_acceptance.py` reruns only `TransitDeliveryAcceptanceTest` using
`BAILONGMAP_TRANSIT_ACCEPTANCE` in the child test environment. This is an ordinary
project test interface, independent of AI tooling. The test imports the real file,
restores repositories with a fixed departure clock, and invokes Android SQLite,
BRouter and the local transit router. It uses no online fallback. Successful runs
write `<spec-stem>.result.json` beside the specification, with input hashes, modes
and case counts. Failed runs remove stale evidence; logs stay beside the external
specification. Without the environment variable the test is skipped in the normal
suite. Reports are host-test evidence, not device-import evidence. Do not run
multiple Gradle builds against this checkout concurrently.
