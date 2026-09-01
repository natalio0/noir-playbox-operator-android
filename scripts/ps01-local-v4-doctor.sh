#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f scripts/android-env.sh ]]; then
  source scripts/android-env.sh
fi

echo "=== NOIR PS01 LOCAL V4 DOCTOR ==="
echo

echo "Java:"
java -version 2>&1 | head -n 1

echo
echo "Routing source:"
grep -n 'localDeviceIds = setOf("PS01")' app/src/main/java/com/noirplaybox/operator/ui/NoirPlayboxApp.kt || {
  echo "PS01 local routing not found."
  exit 2
}

echo
echo "TinyTuya source:"
test -f app/src/main/python/noir_tinytuya_bridge.py
test -f app/src/main/java/com/noirplaybox/operator/hardware/LocalTinyTuyaHardwareController.kt
test -f app/src/main/java/com/noirplaybox/operator/hardware/RoutedHardwareController.kt
echo "OK"

echo
echo "Connected Android devices:"
adb devices || true

echo
echo "Doctor OK ✅"
echo "The encrypted PS01 local config remains inside Android app data."
