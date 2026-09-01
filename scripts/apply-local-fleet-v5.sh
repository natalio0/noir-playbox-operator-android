#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Applying Noir Playbox Local Fleet V5..."

python3 - <<'PY'
from pathlib import Path

app = Path("app/src/main/java/com/noirplaybox/operator/ui/NoirPlayboxApp.kt")
detail = Path("app/src/main/java/com/noirplaybox/operator/ui/screens/DeviceDetailScreen.kt")
pilot = Path("app/src/main/java/com/noirplaybox/operator/ui/screens/TinyTuyaPilotScreen.kt")

for p in (app, detail, pilot):
    if not p.exists():
        raise SystemExit(f"ERROR: Missing {p}")

# ---- NoirPlayboxApp ----
text = app.read_text()

if "import com.noirplaybox.operator.util.NoirServerClock" not in text:
    marker = "import com.noirplaybox.operator.ui.screens.LoginScreen\n"
    if marker not in text:
        raise SystemExit("ERROR: LoginScreen import marker not found.")
    text = text.replace(
        marker,
        marker + "import com.noirplaybox.operator.util.NoirServerClock\n",
        1
    )

old_v4 = '''    // Migration V4:
    // PS01 = TinyTuya LAN only.
    // PS02-PS05 = transitional Tuya Cloud until each local config is validated.
    val hardwareController = remember(context, api) {
        RoutedHardwareController(
            localController = LocalTinyTuyaHardwareController(
                context.applicationContext
            ),
            cloudController = TransitionalCloudHardwareController(api),
            localDeviceIds = setOf("PS01")
        )
    }
'''

new_v5 = '''    // V5 Local Fleet:
    // device dengan encrypted TinyTuya config -> LAN local
    // device tanpa config -> transitional cloud selama migrasi
    val hardwareController = remember(context, api) {
        RoutedHardwareController(
            context = context.applicationContext,
            localController = LocalTinyTuyaHardwareController(
                context.applicationContext
            ),
            cloudController = TransitionalCloudHardwareController(api)
        )
    }
'''

if old_v4 in text:
    text = text.replace(old_v4, new_v5, 1)
elif "context = context.applicationContext" in text and "RoutedHardwareController(" in text:
    print("Dynamic router already present.")
else:
    old_v3 = (
        "    val api = remember { NoirApiClient() }\n"
        "    val hardwareController = remember { TransitionalCloudHardwareController(api) }\n"
    )
    if old_v3 in text:
        text = text.replace(
            old_v3,
            "    val api = remember { NoirApiClient() }\n\n" + new_v5,
            1
        )
    else:
        raise SystemExit("ERROR: hardware controller block not found.")

text = text.replace(
    "val now = System.currentTimeMillis()",
    "val now = NoirServerClock.nowEpochMs()"
)

text = text.replace(
    "// Hybrid migration: PS01 local TinyTuya; remaining devices cloud during transition.",
    "// V5: each device becomes local automatically after TinyTuya config is saved."
)

app.write_text(text)

# ---- DeviceDetailScreen: Local Setup available for every device ----
dtext = detail.read_text()

ps01_only = '''            if (device.id.equals("PS01", ignoreCase = true)) {
                Spacer(Modifier.height(14.dp))
                LocalPilotCard(onOpenLocalPilot)
            }
'''

all_devices = '''            Spacer(Modifier.height(14.dp))
            LocalPilotCard(
                deviceId = device.id,
                onOpenLocalPilot = onOpenLocalPilot
            )
'''

if ps01_only in dtext:
    dtext = dtext.replace(ps01_only, all_devices, 1)

dtext = dtext.replace(
    '''private fun LocalPilotCard(
    onOpenLocalPilot: () -> Unit
) {''',
    '''private fun LocalPilotCard(
    deviceId: String,
    onOpenLocalPilot: () -> Unit
) {'''
)

dtext = dtext.replace('"TinyTuya Local Pilot"', '"TinyTuya Local Setup"')
dtext = dtext.replace(
    '"Test PS01 langsung lewat LAN. Pilot ini terpisah dari lifecycle rental dan tidak mengubah billing Firebase."',
    '"Setup ${deviceId.uppercase()} untuk kontrol langsung lewat LAN. Setelah config disimpan, device ini otomatis memakai TinyTuya lokal."'
)
dtext = dtext.replace(
    'Text("BUKA LOCAL PILOT PS01")',
    'Text("BUKA LOCAL SETUP ${deviceId.uppercase()}")'
)

detail.write_text(dtext)

# ---- Pilot screen: generic wording ----
ptext = pilot.read_text()
ptext = ptext.replace(
    '"Masukkan data PS01 langsung di perangkat. Jangan kirim local_key ke chat."',
    '"Masukkan data ${logicalDeviceId.uppercase()} langsung di perangkat. Jangan kirim local_key ke chat."'
)
ptext = ptext.replace(
    '"Saya paham tombol di bawah mengontrol monitor PS01 langsung tanpa Tuya Cloud."',
    '"Saya paham tombol di bawah mengontrol monitor ${logicalDeviceId.uppercase()} langsung tanpa Tuya Cloud."'
)
ptext = ptext.replace(
    '"PS01 merespons lewat LAN."',
    '"${logicalDeviceId.uppercase()} merespons lewat LAN."'
)
pilot.write_text(ptext)

# Validation
final_app = app.read_text()
if "localDeviceIds = setOf" in final_app:
    raise SystemExit("ERROR: V4 hard-coded router still present.")
if "NoirServerClock.nowEpochMs()" not in final_app:
    raise SystemExit("ERROR: Server clock not wired.")
if 'device.id.equals("PS01"' in detail.read_text():
    raise SystemExit("ERROR: Local setup still PS01-only.")

print("Dynamic per-device local routing: OK")
print("Server-synced countdown: OK")
print("Local Setup all devices: OK")
PY

rm -rf app/build build .gradle/kotlin 2>/dev/null || true

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

echo
echo "Local Fleet V5 applied ✅"
echo
echo "Run:"
echo "  ./scripts/dev-run.sh"
