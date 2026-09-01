#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Applying Kotlin JVM target DSL fix..."

rm -rf app/build build .gradle/kotlin 2>/dev/null || true

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

echo
echo "Fix applied."
echo "Run:"
echo "  ./scripts/dev-run.sh"
