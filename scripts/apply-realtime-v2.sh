#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Applying Noir Playbox Android Realtime Overview V2..."

rm -rf app/build build .gradle/kotlin 2>/dev/null || true

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

echo
echo "Patch applied."
echo "Run:"
echo "  ./scripts/dev-run.sh"
