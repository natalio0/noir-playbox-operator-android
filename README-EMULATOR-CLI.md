# Noir Playbox — Emulator CLI Patch V1

Patch ini menambahkan kontrol emulator dari Terminal/VS Code.

## Apply

Dari root project:

```bash
unzip -o ~/Downloads/noir-playbox-emulator-cli-patch-v1.zip -d .
chmod +x scripts/*.sh
```

## Start emulator

```bash
./scripts/start-emulator.sh
```

Script:
- mencari AVD yang sudah dibuat;
- memprioritaskan AVD bernama `*Tablet*`;
- fallback ke Pixel;
- fallback ke AVD pertama;
- menunggu Android selesai boot.

## Build + install + run

```bash
./scripts/dev-run.sh
```

Ini sama dengan:

```text
start emulator
→ wait boot
→ build APK
→ install
→ launch Noir Playbox Operator
```

## Stop emulator

```bash
./scripts/stop-emulator.sh
```

## Cek nama AVD

```bash
emulator -list-avds
```

Jika output misalnya:

```text
Pixel_Tablet_API_35
```

script akan otomatis memilihnya.

## VS Code tasks

File `.vscode/tasks-emulator-snippet.json` berisi 3 task tambahan.
Karena project kamu sudah punya `.vscode/tasks.json`, snippet sengaja tidak overwrite file existing.

Kamu bisa tetap memakai command Terminal langsung:

```bash
./scripts/dev-run.sh
```

Itu paling simpel.
