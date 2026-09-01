#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/android-env.sh

if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  echo "Gradle wrapper belum siap. Menjalankan setup..."
  ./scripts/setup-vscode.sh
fi

echo "Building Noir Playbox Operator debug APK..."
./gradlew :app:assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$APK" ]]; then
  echo "ERROR: APK tidak ditemukan: $APK"
  exit 1
fi

echo
echo "BUILD SUCCESS"
echo "APK:"
echo "$(pwd)/$APK"
