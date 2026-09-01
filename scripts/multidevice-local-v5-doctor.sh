#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f scripts/android-env.sh ]]; then source scripts/android-env.sh; fi
APP="app/src/main/java/com/noirplaybox/operator/ui/NoirPlayboxApp.kt"
echo "=== NOIR MULTI-DEVICE LOCAL V5 DOCTOR ==="
grep -q "LocalTinyTuyaHardwareController" "$APP" || { echo "FAIL: local controller missing"; exit 2; }
if grep -q "TransitionalCloudHardwareController" "$APP"; then echo "FAIL: cloud controller still active"; exit 3; fi
if grep -q "RoutedHardwareController" "$APP"; then echo "FAIL: cloud fallback router still active"; exit 4; fi
grep -q "LOCAL_HARDWARE_REFRESH_MS = 10_000L" "$APP" || { echo "FAIL: local refresh missing"; exit 5; }
test -f app/src/main/python/noir_tinytuya_bridge.py
test -f app/src/main/java/com/noirplaybox/operator/hardware/LocalTinyTuyaHardwareController.kt
echo "Controller : TinyTuya LAN ONLY"
echo "Polling    : 10 seconds"
echo "Cloud      : no hardware fallback"
echo
echo "Connected Android devices:"
adb devices || true
echo
echo "Doctor OK ✅"
