This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM), Server.

### Features

- Search for places with OpenStreetMap Nominatim and show results on the map.
- View place details, save favorites, and share place deep links.
- Compare multiple journey options and navigate single- or multi-stage trips combining walking,
  subway, bicycle, public transit, or driving.
- Switch map styles and download the visible region for offline viewing.

To plan a journey, open a search result, tap **导航**, grant location permission when prompted, and
select one of the returned options. A multi-stage option shows the active stage, the next stage,
transfer points, and a differently colored line for each travel mode.

Multimodal planning uses an OpenTripPlanner GTFS GraphQL endpoint. Configure it at build time:

```shell
./gradlew :app:androidApp:assembleDebug \
  -Pbailongmap.otpGraphQlUrl=https://your-otp.example/otp/gtfs/v1
```

The OpenTripPlanner instance needs OSM street data and GTFS data for the supported transit region.
When the property is omitted, the app falls back to a single driving option from the public OSRM
demo service.

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

Navigation end-to-end testing requires a connected Android device, a running Appium server, and the
Android device-lock helper. The test injects mock locations through ADB, selects a three-stage
walking–subway–bicycle option, automatically advances at transfer points, and restores real
location afterward. It uses `adb reverse` with local map-style and OpenTripPlanner fixtures, so the
test does not depend on internet access:

```shell
appium
APPIUM_TEST_NAME=navigation ./scripts/appium-test.sh -PappiumTags=navigation
```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
