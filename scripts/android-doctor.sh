#!/usr/bin/env bash
set -u
cd "$(dirname "$0")/.."
source scripts/android-env.sh

echo "=== NOIR ANDROID DOCTOR ==="
echo

printf "Project : "
pwd

printf "Java    : "
if command -v java >/dev/null 2>&1; then
  java -version 2>&1 | head -n 1
else
  echo "NOT FOUND"
fi

printf "JAVA_HOME: "
echo "${JAVA_HOME:-NOT SET}"

printf "ANDROID_HOME: "
echo "${ANDROID_HOME:-NOT SET}"

printf "adb     : "
if command -v adb >/dev/null 2>&1; then
  command -v adb
else
  echo "NOT FOUND"
fi

printf "Gradle  : "
if [[ -x ./gradlew && -f gradle/wrapper/gradle-wrapper.jar ]]; then
  ./gradlew --version | awk '/Gradle / {print; exit}'
else
  echo "wrapper belum siap — jalankan ./scripts/setup-vscode.sh"
fi

echo
echo "=== CONNECTED DEVICES ==="
if command -v adb >/dev/null 2>&1; then
  adb devices
else
  echo "adb belum tersedia di PATH."
fi

echo
echo "Jika Java, ANDROID_HOME, adb dan Gradle terlihat, environment siap."
