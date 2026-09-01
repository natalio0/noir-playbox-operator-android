#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f "scripts/android-env.sh" ]]; then
  source scripts/android-env.sh
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  if [[ -d "$HOME/Library/Android/sdk" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
  fi
fi

if ! command -v emulator >/dev/null 2>&1; then
  echo "ERROR: Android emulator CLI tidak ditemukan."
  echo "Expected: $HOME/Library/Android/sdk/emulator/emulator"
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: adb tidak ditemukan."
  exit 1
fi

# Kalau emulator sudah hidup, jangan buka duplikat.
if adb devices | awk 'NR>1 && $1 ~ /^emulator-/ && $2=="device" {found=1} END {exit found?0:1}'; then
  echo "Android emulator sudah berjalan:"
  adb devices
  exit 0
fi

mapfile_compat() {
  local __resultvar="$1"
  shift
  local output
  output="$("$@")"
  eval "$__resultvar=()"
  while IFS= read -r line; do
    [[ -n "$line" ]] && eval "$__resultvar+=(\"\$line\")"
  done <<< "$output"
}

mapfile_compat AVDS emulator -list-avds

if [[ "${#AVDS[@]}" -eq 0 ]]; then
  echo "ERROR: Belum ada Android Virtual Device."
  echo "Buat sekali dari Android Studio > Device Manager."
  exit 1
fi

SELECTED=""

# Prioritaskan nama yang terlihat seperti tablet.
for avd in "${AVDS[@]}"; do
  lower="$(printf '%s' "$avd" | tr '[:upper:]' '[:lower:]')"
  if [[ "$lower" == *tablet* ]]; then
    SELECTED="$avd"
    break
  fi
done

# Lalu Pixel kalau tablet tidak ketemu.
if [[ -z "$SELECTED" ]]; then
  for avd in "${AVDS[@]}"; do
    lower="$(printf '%s' "$avd" | tr '[:upper:]' '[:lower:]')"
    if [[ "$lower" == *pixel* ]]; then
      SELECTED="$avd"
      break
    fi
  done
fi

# Fallback ke AVD pertama.
if [[ -z "$SELECTED" ]]; then
  SELECTED="${AVDS[0]}"
fi

echo "Starting emulator: $SELECTED"

nohup emulator \
  -avd "$SELECTED" \
  -no-snapshot-save \
  > /tmp/noir-playbox-emulator.log 2>&1 &

echo "Waiting for emulator..."

adb wait-for-device

# Tunggu Android boot selesai.
for i in $(seq 1 120); do
  BOOTED="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$BOOTED" == "1" ]]; then
    echo "Emulator ready ✅"
    adb devices
    exit 0
  fi
  sleep 2
done

echo "ERROR: Emulator tidak selesai boot dalam 4 menit."
echo "Log: /tmp/noir-playbox-emulator.log"
exit 1
