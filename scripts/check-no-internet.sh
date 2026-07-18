#!/usr/bin/env bash
# PERF-04: verify the app never carries android.permission.INTERNET.
# Level 1: grep the source manifest (instant signal).
# Level 2: aapt2 dump permissions on the built APK (authoritative — sees merged manifest).
# Usage: check-no-internet.sh [path/to.apk]  (default: debug APK)
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
MANIFEST="app/src/main/AndroidManifest.xml"

# Level 1: source manifest
if grep -qF "android.permission.INTERNET" "$MANIFEST"; then
    echo "ERROR: android.permission.INTERNET found in $MANIFEST" >&2
    exit 1
fi
echo "Level 1 OK: no INTERNET in source manifest"

# Level 2: built APK (merged manifest)
if [ -f "$APK" ]; then
    SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [ -z "$SDK_ROOT" ] || [ ! -d "$SDK_ROOT/build-tools" ]; then
        echo "ERROR: Android SDK build-tools not found (set ANDROID_HOME or ANDROID_SDK_ROOT)" >&2
        exit 1
    fi
    AAPT2=$(find "$SDK_ROOT/build-tools" -name aapt2 | sort -V | tail -1)
    if [ -z "$AAPT2" ]; then
        echo "ERROR: aapt2 not found under $SDK_ROOT/build-tools" >&2
        exit 1
    fi
    PERMS=$("$AAPT2" dump permissions "$APK")
    echo "$PERMS"
    if grep -qF "android.permission.INTERNET" <<< "$PERMS"; then
        echo "ERROR: android.permission.INTERNET found in built APK $APK" >&2
        exit 1
    fi
    echo "Level 2 OK: no INTERNET in built APK"
else
    echo "WARNING: level 2 skipped (no APK at $APK)"
fi

echo "no INTERNET permission"
