#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/android-env.sh

if ! command -v adb >/dev/null 2>&1; then
  echo "adb tidak ditemukan."
  exit 1
fi

PID="$(adb shell pidof com.noirplaybox.operator 2>/dev/null | tr -d '\r' || true)"

if [[ -n "$PID" ]]; then
  echo "Showing logcat for PID $PID..."
  adb logcat --pid="$PID"
else
  echo "App belum running. Menampilkan logcat umum."
  adb logcat
fi
