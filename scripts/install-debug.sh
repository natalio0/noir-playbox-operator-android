#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/android-env.sh

APK="app/build/outputs/apk/debug/app-debug.apk"

if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: adb tidak ditemukan."
  echo "Pastikan Android SDK terinstall."
  exit 1
fi

if [[ ! -f "$APK" ]]; then
  echo "APK belum ada. Build dulu..."
  ./scripts/build-debug.sh
fi

DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"

if [[ "$DEVICE_COUNT" -eq 0 ]]; then
  echo "ERROR: Tidak ada emulator/device Android yang terhubung."
  echo "Nyalakan Pixel Tablet emulator atau hubungkan tablet via USB debugging."
  exit 1
fi

if [[ "$DEVICE_COUNT" -gt 1 ]]; then
  echo "ERROR: Ada lebih dari satu Android device."
  echo "Gunakan adb -s <serial> install -r $APK untuk memilih device."
  adb devices
  exit 1
fi

echo "Installing APK..."
adb install -r "$APK"

echo "Install selesai."
