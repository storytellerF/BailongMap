#!/bin/sh
set -e

findEmulatorSerial() {
    adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'
}

if ! command -v adb >/dev/null 2>&1; then
    echo "ERROR: adb is not available." >&2
    exit 1
fi

EMULATOR_SERIAL="${ANDROID_UDID:-$(findEmulatorSerial)}"
if [ -z "$EMULATOR_SERIAL" ]; then
    echo "ERROR: No connected Android device/emulator found." >&2
    exit 1
fi

echo "Using Android device: $EMULATOR_SERIAL"

case " ${JAVA_TOOL_OPTIONS:-} " in
    *" -Dapi.version="*) ;;
    *) JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Dapi.version=${DOCKER_API_VERSION:-1.40}" ;;
esac
export JAVA_TOOL_OPTIONS

echo "Installing debug APK..."
ANDROID_SERIAL="$EMULATOR_SERIAL" ./gradlew :app:androidApp:installDebug "$@"

echo "Running Appium tests..."
ANDROID_UDID="$EMULATOR_SERIAL" ./gradlew :appiumTests:test -PrunAppium=true "$@"
