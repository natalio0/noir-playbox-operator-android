# Noir Playbox — TinyTuya Compile Fix V1

Fix untuk error Kotlin setelah TinyTuya PS01 Pilot V1.

Yang diperbaiki:
- karakter `\` nyasar di baris pertama TinyTuyaBridge.kt
- karakter `\` nyasar di baris pertama TinyTuyaSecureStore.kt
- karakter `\` nyasar di baris pertama TinyTuyaPilotScreen.kt
- karakter `\` nyasar di Python bridge
- parameter `onOpenLocalPilot` yang tidak sengaja ikut masuk ke LifecycleCard
- parameter `onOpenLocalPilot` yang tidak sengaja ikut masuk ke ShutdownActions
- defensive `else` pada mapping status switch TinyTuya

Apply:

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
unzip -o ~/Downloads/noir-playbox-tinytuya-compile-fix-v1.zip -d .
./scripts/apply-tinytuya-compile-fix-v1.sh
./scripts/dev-run.sh
```

Patch tidak mengubah Firebase data, website, rental record, Device ID, maupun local_key.
