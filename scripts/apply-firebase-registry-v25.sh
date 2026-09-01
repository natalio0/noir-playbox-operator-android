#!/usr/bin/env bash
set -e
./gradlew assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
echo "Build selesai: $APK"
if [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
  "$HOME/Library/Android/sdk/platform-tools/adb" devices || true
  "$HOME/Library/Android/sdk/platform-tools/adb" install -r "$APK" || true
fi
