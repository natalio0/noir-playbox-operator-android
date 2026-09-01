#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Applying Noir Playbox Android Rental Lifecycle V3..."

rm -rf app/build build .gradle/kotlin 2>/dev/null || true

# Penting: jalankan Gradle stop melalui environment project supaya JDK 21 tetap dipakai.
if [[ -f scripts/android-env.sh ]]; then
  source scripts/android-env.sh
fi

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

echo
echo "V3 applied."
echo "Flow aktif:"
echo "  READY -> PREPARING -> ACTIVE -> SHUTDOWN_PENDING -> SHUTDOWN_ACTIVE -> READY"
echo
echo "Run:"
echo "  ./scripts/dev-run.sh"
