#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "== Noir Playbox VS Code Setup =="

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java tidak ditemukan."
  echo "Android Studio biasanya sudah membawa JDK."
  echo "Set JAVA_HOME ke Android Studio JBR jika perlu."
  exit 1
fi

mkdir -p gradle/wrapper

if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  echo "gradle-wrapper.jar belum ada. Download official Gradle wrapper 8.9..."

  WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar"

  if command -v curl >/dev/null 2>&1; then
    curl -fL "$WRAPPER_URL" -o gradle/wrapper/gradle-wrapper.jar
  elif command -v wget >/dev/null 2>&1; then
    wget -O gradle/wrapper/gradle-wrapper.jar "$WRAPPER_URL"
  else
    echo "ERROR: curl/wget tidak tersedia."
    exit 1
  fi
fi

chmod +x gradlew
chmod +x scripts/*.sh

if [[ ! -f gradle/wrapper/gradle-wrapper.properties ]]; then
  cat > gradle/wrapper/gradle-wrapper.properties <<'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
fi

echo
echo "Gradle wrapper:"
./gradlew --version

echo
echo "Setup selesai."
echo "Selanjutnya:"
echo "  ./scripts/android-doctor.sh"
echo "  ./scripts/build-debug.sh"
