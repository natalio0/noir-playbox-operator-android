#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Applying Noir Playbox Multi-Device Local V5..."
python3 ./scripts/apply-multidevice-local-v5.py
rm -rf app/build build .gradle/kotlin 2>/dev/null || true
if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi
echo
echo "Multi-Device Local V5 applied ✅"
echo "PS01-PS05 now use TinyTuya LAN only."
echo "No automatic Tuya Cloud hardware fallback."
echo
echo "Run:"
echo "  ./scripts/multidevice-local-v5-doctor.sh"
echo "  ./scripts/dev-run.sh"
