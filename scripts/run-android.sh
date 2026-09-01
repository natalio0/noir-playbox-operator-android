#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/android-env.sh

./scripts/build-debug.sh
./scripts/install-debug.sh

PACKAGE="com.noirplaybox.operator"
ACTIVITY=".MainActivity"

echo "Launching Noir Playbox Operator..."
adb shell am force-stop "$PACKAGE" || true
adb shell am start -n "$PACKAGE/$ACTIVITY"

echo
echo "DONE — aplikasi dijalankan di emulator/device."
