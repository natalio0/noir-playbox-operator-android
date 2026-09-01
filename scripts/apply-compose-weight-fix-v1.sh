#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

FILE="app/src/main/java/com/noirplaybox/operator/ui/screens/DeviceDetailScreen.kt"

if [[ ! -f "$FILE" ]]; then
  echo "ERROR: File tidak ditemukan:"
  echo "  $FILE"
  exit 1
fi

echo "Fixing Compose weight import..."

python3 - <<'PY'
from pathlib import Path

p = Path("app/src/main/java/com/noirplaybox/operator/ui/screens/DeviceDetailScreen.kt")
text = p.read_text()

bad_imports = [
    "import androidx.compose.foundation.layout.weight\n",
    "import androidx.compose.foundation.layout.RowColumnParentData\n",
]

changed = False
for bad in bad_imports:
    if bad in text:
        text = text.replace(bad, "")
        changed = True

p.write_text(text)

if changed:
    print("Removed problematic Compose weight import.")
else:
    print("Problematic import was already absent; no source change needed.")
PY

rm -rf app/build build .gradle/kotlin 2>/dev/null || true

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

echo
echo "Fix applied ✅"
echo "Run:"
echo "  ./scripts/dev-run.sh"
