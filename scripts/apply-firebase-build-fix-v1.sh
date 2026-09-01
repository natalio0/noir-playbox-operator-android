#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Applying Noir Playbox Firebase build fix..."

OLD_REPO="app/src/main/java/com/noirplaybox/operator/data/FakeOperationalRepository.kt"

if [[ -f "$OLD_REPO" ]]; then
  rm "$OLD_REPO"
  echo "Removed: $OLD_REPO"
fi

# Bersihkan cache build project, tidak menyentuh ~/.gradle global cache.
rm -rf app/build build .gradle/kotlin 2>/dev/null || true

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

echo
echo "Fix applied."
echo "Next:"
echo "  ./scripts/dev-run.sh"
