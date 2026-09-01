#!/usr/bin/env bash
set -e
./gradlew assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
echo "Build selesai: $APK"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [ -x "$ADB" ]; then
  "$ADB" devices || true
  "$ADB" install -r "$APK"
fi
