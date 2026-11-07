This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM), Server.

See [Development](DEVELOPMENT.md) for build prerequisites and CI checks.

### Features

- Search for places with OpenStreetMap Nominatim and show results on the map.
- View place details, save favorites, and share place deep links.
- Compare multiple journey options and navigate single- or multi-stage trips combining walking,
  subway, bicycle, public transit, or driving.
- Switch map styles and download the visible region for offline viewing.

To plan a journey, open a search result, tap **导航**, grant location permission when prompted, and
select one of the returned options. A multi-stage option shows the active stage, the next stage,
transfer points, and a differently colored line for each travel mode.

### Offline bus and subway data

Public transport can run locally on Android without a server:

1. Ask an AI assistant to use the project skill
   [bailongmap-transit](.agents/skills/bailongmap-transit/SKILL.md) with your verified
   transit source material or GTFS ZIP.
2. The assistant organizes GTFS and runs the project compiler to produce a
   `.bltransit` package. Missing coordinates or schedules must be supplied, not invented.
3. In the app, open **设置 → 离线公共交通 → 导入公共交通包**, select the package,
   review its source, dates and counts, then tap **确认导入**.
4. Navigation uses the installed timetable locally, including waiting time and
   same-stop transfers. Importing replaces the active transit package. It persists
   across app restarts; **移除公共交通包** returns planning to the existing online provider.

To build without an AI assistant, use Python 3.10+ (a virtual environment is recommended):

```shell
python -m pip install -r tools/transit/requirements.txt
python tools/transit/build_transit.py region-gtfs.zip region.bltransit --name "Region" --source "Data provider and license" --start 20261101 --end 20261130
```

Use dates matching your actual source. The compiler checks references, service
calendars, coordinates and times; remaps IDs; and builds a versioned SQLite database
with indexes and a SHA-256 manifest. The app checks compatibility and integrity
before allowing installation. Failed or cancelled imports preserve installed data.
No OTP binary, OSM PBF file or server is needed for this workflow.

The skill now distinguishes compiling a file from verifying a usable delivery. It
requires source/license evidence and reviewed GTFS validation, reports populated
fields that the app omits, and runs declared journeys through the actual Android
importer and offline planners. Full offline transit delivery also includes tested
BRouter road segments. Packages with missing verification are labelled unverified;
passing samples establish those journeys, not every possible trip or source fact.
See [delivery verification](DEVELOPMENT.md#transit-delivery-verification) for the
normal tools, usable without an AI assistant.

The current compiler produces version 3 packages; the app also accepts versions 1 and 2.
It supports bus/subway, one timezone, up to 366 days of coverage, 50,000
stop/location records and 250,000 consecutive-stop connections. Every stop needs explicit
arrival/departure times (overnight times below 48:00:00 are supported). Calendar
exceptions and pickup/drop-off prohibitions are preserved. Station, platform,
entrance and boarding-area hierarchies are retained. Directed pathways use their
traversal times (or walkway/gate length at 1.2 m/s); other pathway modes require
explicit times. A station with a pathway graph is accessed through its entrances
or explicitly accessible stops, so direct approach lines cannot bypass its graph.
Without a graph, platform transfers within one station use estimated distances.

`frequencies.txt` with `exact_times=1` is expanded into distinct scheduled trips.
For `exact_times=0`, the app budgets a full headway after reaching a boarding stop,
checks the service window, and labels the result **间隔估算**. No fixed departure
phase is invented. Transfer types 0–3, including forbidden/minimum-time rules and
route/trip selectors, follow GTFS specificity. Ambiguous equal-priority rules are
rejected. Types 4/5 (in-seat interlining), on-demand windows, continuous boarding,
and multiple agency timezones remain unsupported and fail explicitly.
The package limit is 32 MiB compressed and 128 MiB for the SQLite database.

The local planner searches boardings (including transfers) in the next 24 hours
from the query time, including across midnight. Once boarded, the same vehicle may
continue beyond that window; it is not a total journey-duration limit. It returns an earliest
arrival option for scheduled services, or a conservative estimate for unscheduled
headway services. Transfer minimum times are applied from the original alighting
time, including walking through intermediate nodes; unspecified transfers use
120 seconds. Staying aboard one trip needs no transfer allowance. Door-to-door
planning routes approaches/exits along the street network, checking the two nearest
accessible stops within 2 km for walking or 5 km for cycling at each end. This
bounded search can miss a better or more distant boarding stop. Station paths use
supplied traversal times; paths without geometry and transit lines remain schematic.
A bicycle + transit + bicycle option means carrying your own bicycle, and requires
GTFS `bikes_allowed=1` on every ridden trip. Unknown permission is not permission.
Parking and shared-bike handoffs are not implemented.
There are no live delays, fares or accessibility guarantees. Shapes are not used.
Offline map tiles and online place search remain separate from timetable routing.

Without an installed transit package, the existing optional OpenTripPlanner GTFS
GraphQL endpoint can still be configured at build time:

```shell
./gradlew :app:androidApp:assembleDebug \
  -Pbailongmap.otpGraphQlUrl=https://your-otp.example/otp/gtfs/v1
```

Driving, cycling, walking and public transit are independent choices. Available
routes appear while other modes are still being calculated; a transit coverage or
date miss is shown within the transit category. Choose a route explicitly to start.
The optional OTP endpoint is used only for public transport when local planning
finds no route.

### Offline roads

In **设置 → 离线道路 → 导入离线道路**, select all required standard BRouter
`.rd5` segments together. Obtain them from the linked
[BRouter segment directory](https://brouter.de/brouter/segments4/). Importing replaces
the installed road region. The embedded BRouter engine calculates driving, cycling,
and walking, including transit approaches, on the device. Downloaded map tiles are
for display and do not replace routing data. Full offline planning needs both the
road segments and a transit package covering the journey.

When an offline road route is unavailable, the same mode can use an online OSRM
service; results are marked **在线道路规划**. Driving uses the public OSRM demo;
walking/cycling use [FOSSGIS routing](https://routing.openstreetmap.de/about.html),
with shared request spacing of at least one second. These public services have
usage limits and no availability guarantee. Credits: © OpenStreetMap contributors,
OSRM, FOSSGIS and BRouter. Search and map downloads remain separate network services.

* [/app/iosApp](./app/iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/app/shared](./app/shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./app/shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./app/shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./app/shared/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/core](./core/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./core/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app (requires Android 13 / API 33 or newer):
  `./gradlew :app:androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :app:desktopApp:hotRun --auto`
  - Standard run: `./gradlew :app:desktopApp:run`
- Server: `./gradlew :server:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :app:webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/app/iosApp](./app/iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:shared:testAndroidHostTest`
- Server tests: `./gradlew :server:test`

See [Development](DEVELOPMENT.md) for navigation end-to-end test setup and commands.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
