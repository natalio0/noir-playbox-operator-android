#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

OLD="app/src/main/java/com/noirplaybox/operator/data/FakeOperationalRepository.kt"

if [[ -f "$OLD" ]]; then
  rm "$OLD"
  echo "Removed old demo repository: $OLD"
fi

echo "Firebase Operational V1 patch applied."
echo
echo "Next:"
echo "1. Put google-services.json in app/google-services.json"
echo "2. ./scripts/dev-run.sh"
