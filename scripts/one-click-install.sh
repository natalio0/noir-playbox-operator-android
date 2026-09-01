#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f app/google-services.json ]]; then
  echo "ERROR: app/google-services.json belum ada."
  echo "Copy file Firebase Android Anda ke app/google-services.json lalu jalankan lagi."
  exit 1
fi

chmod +x ./gradlew
./gradlew :app:assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "Build selesai: $APK"
if command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1; then
  adb install -r "$APK"
  echo "Noir Operator sudah terpasang di device Android."
else
  echo "ADB/device tidak terdeteksi. Install APK secara manual dari: $APK"
fi
