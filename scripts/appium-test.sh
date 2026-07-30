#!/bin/sh
set -e

findAndroidSerial() {
    adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }'
}

if ! command -v adb >/dev/null 2>&1; then
    echo "ERROR: adb is not available." >&2
    exit 1
fi

DEVICE_SERIAL="${ANDROID_UDID:-$(findAndroidSerial)}"
if [ -z "$DEVICE_SERIAL" ]; then
    echo "ERROR: No connected Android device/emulator found." >&2
    exit 1
fi

echo "Using Android device: $DEVICE_SERIAL"

if [ "${APPIUM_DEVICE_LOCK_HELD:-0}" != "1" ]; then
    CODEX_SKILLS_DIR="${CODEX_HOME:-${HOME}/.codex}/skills"
    DEVICE_LOCK_SCRIPT="${ADB_DEVICE_LOCK_SCRIPT:-$CODEX_SKILLS_DIR/android-appium-device-lock/scripts/adb-device-lock.sh}"
    if [ ! -x "$DEVICE_LOCK_SCRIPT" ]; then
        echo "ERROR: Android device lock script not found: $DEVICE_LOCK_SCRIPT" >&2
        echo "Set ADB_DEVICE_LOCK_SCRIPT to the adb-device-lock.sh path." >&2
        exit 1
    fi

    export APPIUM_DEVICE_LOCK_HELD=1
    exec "$DEVICE_LOCK_SCRIPT" run \
        --serial "$DEVICE_SERIAL" \
        --project-dir "$PWD" \
        --test-name "${APPIUM_TEST_NAME:-appium-suite}" \
        --max-timeout-seconds 1800 \
        --wait-timeout-seconds 3600 \
        -- "$0" "$@"
fi

case " ${JAVA_TOOL_OPTIONS:-} " in
    *" -Dapi.version="*) ;;
    *) JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Dapi.version=${DOCKER_API_VERSION:-1.40}" ;;
esac
export JAVA_TOOL_OPTIONS

echo "Installing debug APK..."
ANDROID_SERIAL="$DEVICE_SERIAL" ./gradlew :app:androidApp:installDebug "$@"

echo "Running Appium tests..."
ANDROID_UDID="$DEVICE_SERIAL" ./gradlew :appiumTests:test -PrunAppium=true "$@"
