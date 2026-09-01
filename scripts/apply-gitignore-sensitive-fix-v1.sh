#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Protecting TinyTuya sensitive files from Git..."

touch .gitignore

python3 - <<'PY'
from pathlib import Path

p = Path(".gitignore")
text = p.read_text() if p.exists() else ""

begin = "# === NOIR PLAYBOX LOCAL/SECRET FILES ==="
end = "# === END NOIR PLAYBOX LOCAL/SECRET FILES ==="

if begin in text and end in text:
    before, rest = text.split(begin, 1)
    _, after = rest.split(end, 1)
    text = before.rstrip() + "\n" + after.lstrip("\n")

block = """
# === NOIR PLAYBOX LOCAL/SECRET FILES ===
# TinyTuya Wizard / LAN provisioning data.
# These may contain local_key, Tuya API credentials, device metadata, or LAN snapshots.
devices.json
snapshot.json
tuya-raw.json
tinytuya.json

# Common local secret/config variants.
devices.local.json
tinytuya.local.json
*.local-key.json
# === END NOIR PLAYBOX LOCAL/SECRET FILES ===
"""

p.write_text(text.rstrip() + "\n\n" + block.lstrip())
PY

git rm --cached --ignore-unmatch \
  devices.json \
  snapshot.json \
  tuya-raw.json \
  tinytuya.json \
  devices.local.json \
  tinytuya.local.json \
  >/dev/null 2>&1 || true

echo
echo "Verification:"
for f in devices.json snapshot.json tuya-raw.json tinytuya.json; do
  if git check-ignore -q "$f"; then
    echo "  ✅ ignored: $f"
  else
    echo "  ⚠️ not ignored: $f"
  fi
done

echo
echo "Sensitive Git ignore fix applied ✅"
echo
echo "Now run:"
echo "  ./scripts/github-preflight.sh"
echo
echo "Then inspect:"
echo "  git status"
