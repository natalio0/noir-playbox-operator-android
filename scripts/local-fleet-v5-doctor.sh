#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f scripts/android-env.sh ]]; then
  source scripts/android-env.sh
fi

echo "=== NOIR LOCAL FLEET V5 DOCTOR ==="
echo

grep -q 'NoirServerClock.nowEpochMs()'   app/src/main/java/com/noirplaybox/operator/ui/NoirPlayboxApp.kt
echo "Server-synced countdown: OK"

grep -q 'context = context.applicationContext'   app/src/main/java/com/noirplaybox/operator/ui/NoirPlayboxApp.kt
echo "Dynamic local routing: OK"

if grep -q 'localDeviceIds = setOf'   app/src/main/java/com/noirplaybox/operator/ui/NoirPlayboxApp.kt; then
  echo "ERROR: V4 hard-coded routing still present."
  exit 2
fi

echo
adb devices || true
echo
echo "Doctor OK ✅"
