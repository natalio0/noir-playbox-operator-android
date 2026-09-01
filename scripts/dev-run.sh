#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

./scripts/start-emulator.sh
./scripts/run-android.sh
