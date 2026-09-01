#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f scripts/android-env.sh ]]; then
  source scripts/android-env.sh
fi

echo "=== NOIR TINYTUYA PILOT DOCTOR ==="
echo

if command -v python3.13 >/dev/null 2>&1; then
  echo "Python 3.13 : $(python3.13 --version 2>&1)"
  echo "Path        : $(command -v python3.13)"
else
  echo "Python 3.13 : MISSING"
  echo
  echo "Install di macOS:"
  echo "  brew install python@3.13"
  echo
  exit 2
fi

echo "Java        : $(java -version 2>&1 | head -n 1)"
echo "JAVA_HOME   : ${JAVA_HOME:-unset}"
echo "ANDROID_HOME: ${ANDROID_HOME:-unset}"
echo

if [[ -f app/src/main/python/noir_tinytuya_bridge.py ]]; then
  echo "Python bridge: OK"
else
  echo "Python bridge: MISSING"
  exit 3
fi

if grep -q 'com.chaquo.python' app/build.gradle.kts; then
  echo "Chaquopy app plugin: OK"
else
  echo "Chaquopy app plugin: MISSING"
  exit 4
fi

if grep -q 'tinytuya==1.20.0' app/build.gradle.kts; then
  echo "TinyTuya dependency: OK"
else
  echo "TinyTuya dependency: MISSING"
  exit 5
fi

echo
echo "Connected Android devices:"
adb devices || true

echo
echo "Doctor OK ✅"
echo
echo "Next:"
echo "  ./scripts/dev-run.sh"
