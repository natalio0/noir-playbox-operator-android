# Noir Playbox — Git Ignore Sensitive Fix V1

The GitHub preflight correctly stopped because `devices.json` is sensitive.

TinyTuya-generated files may contain:
- local_key
- Tuya API credentials
- Device IDs / metadata
- LAN snapshots

This patch adds these to `.gitignore`:

```text
devices.json
snapshot.json
tuya-raw.json
tinytuya.json
devices.local.json
tinytuya.local.json
*.local-key.json
```

If any were previously staged or tracked, the script runs `git rm --cached`
so Git forgets them while leaving the local files untouched.

Apply:

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1

unzip -o ~/Downloads/noir-playbox-gitignore-sensitive-fix-v1.zip -d .

./scripts/apply-gitignore-sensitive-fix-v1.sh

./scripts/github-preflight.sh

git status
```

Do not delete your local `devices.json` yet: it contains the TinyTuya
provisioning data needed for local-device setup.
