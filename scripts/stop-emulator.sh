#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f "scripts/android-env.sh" ]]; then
  source scripts/android-env.sh
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb tidak ditemukan."
  exit 1
fi

EMULATORS="$(adb devices | awk 'NR>1 && $1 ~ /^emulator-/ && $2=="device" {print $1}')"

if [[ -z "$EMULATORS" ]]; then
  echo "Tidak ada emulator yang sedang berjalan."
  exit 0
fi

while IFS= read -r serial; do
  [[ -z "$serial" ]] && continue
  echo "Stopping $serial..."
  adb -s "$serial" emu kill || true
done <<< "$EMULATORS"

echo "Emulator dihentikan."
