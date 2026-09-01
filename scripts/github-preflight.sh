#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== Noir Playbox GitHub Preflight ==="

touch .gitignore
if ! grep -q "# === TinyTuya provisioning / secrets ===" .gitignore 2>/dev/null; then
  printf '\n' >> .gitignore
  cat .gitignore.noir-handoff >> .gitignore
fi

SENSITIVE_FILES=(
  "devices.json"
  "snapshot.json"
  "tuya-raw.json"
  "tinytuya.json"
  "app/google-services.json"
  ".env"
)

for f in "${SENSITIVE_FILES[@]}"; do
  if [[ -e "$f" ]] && ! git check-ignore -q "$f" 2>/dev/null; then
    echo "ERROR: sensitive file is not ignored: $f"
    exit 2
  fi
done

if [[ -d .git ]]; then
  for f in "${SENSITIVE_FILES[@]}"; do
    if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      echo "ERROR: sensitive file is already tracked: $f"
      echo "Run: git rm --cached '$f'"
      exit 3
    fi
  done
fi

rm -f .gitignore.noir-handoff

echo "Preflight OK ✅"
echo "Now run:"
echo "  git status"
echo "  git add ."
echo "  git status"
