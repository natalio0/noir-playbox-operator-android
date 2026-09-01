#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
echo "Noir Operator UI v0.8 sudah berada di project."
echo "Building debug APK..."
./gradlew assembleDebug
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [ -x "$ADB" ] && "$ADB" get-state >/dev/null 2>&1; then
  "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
  echo "Noir Operator v0.8 terpasang ke device."
else
  echo "Device ADB tidak terdeteksi. Install APK manual bila perlu."
fi
