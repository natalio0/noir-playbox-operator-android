#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Applying TinyTuya PS01 Android pilot..."

if ! command -v python3.13 >/dev/null 2>&1; then
  echo
  echo "Python 3.13 belum ada."
  echo "Chaquopy pilot ini membutuhkan Python build-machine dengan major/minor yang sama."
  echo
  echo "Install:"
  echo "  brew install python@3.13"
  echo
  echo "Lalu jalankan kembali:"
  echo "  ./scripts/apply-tinytuya-ps01-pilot-v1.sh"
  exit 2
fi

if [[ -f scripts/android-env.sh ]]; then
  source scripts/android-env.sh
fi

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

rm -rf app/build build .gradle/kotlin 2>/dev/null || true

echo
echo "TinyTuya pilot files ready ✅"
echo
echo "Run doctor:"
echo "  ./scripts/tinytuya-doctor.sh"
echo
echo "Then build:"
echo "  ./scripts/dev-run.sh"
