#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Locking Noir Playbox Gradle to JDK 21..."

JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)

if [[ -z "$JAVA21_HOME" || ! -x "$JAVA21_HOME/bin/java" ]]; then
  echo "ERROR: JDK 21 tidak ditemukan."
  echo "Cek dengan:"
  echo "  /usr/libexec/java_home -V"
  exit 1
fi

echo "JDK 21: $JAVA21_HOME"
"$JAVA21_HOME/bin/java" -version

GRADLE_PROPS="gradle.properties"

touch "$GRADLE_PROPS"

# Hapus konfigurasi org.gradle.java.home lama jika ada.
TMP_FILE="$(mktemp)"
grep -v '^org\.gradle\.java\.home=' "$GRADLE_PROPS" > "$TMP_FILE" || true
cat "$TMP_FILE" > "$GRADLE_PROPS"
rm -f "$TMP_FILE"

# Paksa Gradle selalu memakai JDK 21.
printf '\norg.gradle.java.home=%s\n' "$JAVA21_HOME" >> "$GRADLE_PROPS"

# Perkuat environment script juga, kalau file-nya ada.
if [[ -f "scripts/android-env.sh" ]]; then
  cat > "scripts/android-env.sh" <<'EOF'
#!/usr/bin/env bash

set -e

JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)

if [[ -z "$JAVA21_HOME" ]]; then
  echo "ERROR: JDK 21 tidak ditemukan."
  exit 1
fi

export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

if [[ -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
fi
EOF
  chmod +x scripts/android-env.sh
fi

# Hentikan daemon yang mungkin masih hidup dengan Java 25.
if [[ -x ./gradlew ]]; then
  JAVA_HOME="$JAVA21_HOME" PATH="$JAVA21_HOME/bin:$PATH" ./gradlew --stop || true
fi

rm -rf .gradle/kotlin app/build build 2>/dev/null || true

echo
echo "=== VERIFY ==="
echo "gradle.properties:"
grep '^org\.gradle\.java\.home=' "$GRADLE_PROPS" || true
echo
echo "Shell Java:"
JAVA_HOME="$JAVA21_HOME" PATH="$JAVA21_HOME/bin:$PATH" java -version
echo
echo "Fix applied ✅"
echo
echo "Next:"
echo "  ./scripts/android-doctor.sh"
echo "  ./scripts/dev-run.sh"
