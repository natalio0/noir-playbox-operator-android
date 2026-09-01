#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Applying TinyTuya v3.5 bridge fix..."

FILE="app/src/main/python/noir_tinytuya_bridge.py"

if [[ ! -f "$FILE" ]]; then
  echo "ERROR: $FILE tidak ditemukan."
  exit 1
fi

python3 -m py_compile "$FILE"

rm -rf app/build build .gradle/kotlin 2>/dev/null || true

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

echo
echo "TinyTuya v3.5 bridge fix applied ✅"
echo
echo "Run:"
echo "  ./scripts/dev-run.sh"
