from pathlib import Path
import re

app = Path("app/src/main/java/com/noirplaybox/operator/ui/NoirPlayboxApp.kt")
detail = Path("app/src/main/java/com/noirplaybox/operator/ui/screens/DeviceDetailScreen.kt")
pilot = Path("app/src/main/java/com/noirplaybox/operator/ui/screens/TinyTuyaPilotScreen.kt")

for path in (app, detail, pilot):
    if not path.exists():
        raise SystemExit(f"ERROR: Missing {path}")

# 1) ALL hardware -> LocalTinyTuyaHardwareController, no cloud fallback.
text = app.read_text()

if "import com.noirplaybox.operator.hardware.LocalTinyTuyaHardwareController" not in text:
    anchor = "import com.noirplaybox.operator.data.RentalLifecycleRepository\n"
    if anchor not in text:
        raise SystemExit("ERROR: import anchor not found")
    text = text.replace(anchor, anchor + "import com.noirplaybox.operator.hardware.LocalTinyTuyaHardwareController\n", 1)

text = text.replace("import com.noirplaybox.operator.hardware.RoutedHardwareController\n", "")
text = text.replace("import com.noirplaybox.operator.hardware.TransitionalCloudHardwareController\n", "")

v4_pattern = re.compile(
    r"    val api = remember \{ NoirApiClient\(\) \}\n\n"
    r"    // Migration V4:.*?"
    r"    val hardwareController = remember\(context, api\) \{\n"
    r"        RoutedHardwareController\(.*?"
    r"    \}\n",
    re.DOTALL,
)

replacement = '''    val api = remember { NoirApiClient() }

    // V5: ALL operational hardware uses TinyTuya LAN.
    // No automatic Tuya Cloud fallback.
    val hardwareController = remember(context) {
        LocalTinyTuyaHardwareController(
            context.applicationContext
        )
    }
'''

if v4_pattern.search(text):
    text = v4_pattern.sub(replacement, text, count=1)
else:
    cloud_old = (
        "    val api = remember { NoirApiClient() }\n"
        "    val hardwareController = remember { TransitionalCloudHardwareController(api) }\n"
    )
    if cloud_old in text:
        text = text.replace(cloud_old, replacement, 1)
    elif "ALL operational hardware uses TinyTuya LAN" not in text:
        raise SystemExit("ERROR: hardwareController initialization not recognized")

text = text.replace(
    "private const val TRANSITIONAL_CLOUD_HARDWARE_REFRESH_MS = 15 * 60_000L",
    "private const val LOCAL_HARDWARE_REFRESH_MS = 10_000L",
)
text = text.replace("TRANSITIONAL_CLOUD_HARDWARE_REFRESH_MS", "LOCAL_HARDWARE_REFRESH_MS")
text = text.replace(
    "// Cloud hardware hanya selama transisi. TinyTuya nanti mengganti controller ini.",
    "// TinyTuya LAN refresh. No Tuya Cloud hardware polling.",
)
text = text.replace(
    "// Hybrid migration: PS01 local TinyTuya; remaining devices cloud during transition.",
    "// TinyTuya LAN refresh. No Tuya Cloud hardware polling.",
)
app.write_text(text)

# 2) Local setup available for every device.
text = detail.read_text()
old = '''            if (device.id.equals("PS01", ignoreCase = true)) {
                Spacer(Modifier.height(14.dp))
                LocalPilotCard(onOpenLocalPilot)
            }
'''
new = '''            Spacer(Modifier.height(14.dp))
            LocalPilotCard(
                deviceId = device.id,
                onOpenLocalPilot = onOpenLocalPilot
            )
'''
if old in text:
    text = text.replace(old, new, 1)
elif 'LocalPilotCard(\n                deviceId = device.id' not in text:
    raise SystemExit("ERROR: PS01-only LocalPilotCard block not found")

text = text.replace(
    '''private fun LocalPilotCard(
    onOpenLocalPilot: () -> Unit
) {''',
    '''private fun LocalPilotCard(
    deviceId: String,
    onOpenLocalPilot: () -> Unit
) {''',
)
text = text.replace(
    '"Test PS01 langsung lewat LAN. Pilot ini terpisah dari lifecycle rental dan tidak mengubah billing Firebase.",',
    '"Setup dan test $deviceId langsung lewat LAN. Local key tersimpan terenkripsi di perangkat.",',
)
text = text.replace('Text("BUKA LOCAL PILOT PS01")', 'Text("BUKA LOCAL SETUP $deviceId")')
detail.write_text(text)

# 3) TinyTuya setup screen is generic, not PS01-only copy.
text = pilot.read_text()
text = text.replace('"PS01 merespons lewat LAN."', '"${logicalDeviceId.uppercase()} merespons lewat LAN."')
text = text.replace(
    '"Masukkan data PS01 langsung di perangkat. Jangan kirim local_key ke chat.",',
    '"Masukkan data ${logicalDeviceId.uppercase()} langsung di perangkat. Jangan kirim local_key ke chat.",',
)
text = text.replace(
    '"Saya paham tombol di bawah mengontrol monitor PS01 langsung tanpa Tuya Cloud."',
    '"Saya paham tombol di bawah mengontrol monitor ${logicalDeviceId.uppercase()} langsung tanpa Tuya Cloud."',
)
pilot.write_text(text)

# Validation.
app_text = app.read_text()
detail_text = detail.read_text()
pilot_text = pilot.read_text()

if "TransitionalCloudHardwareController" in app_text:
    raise SystemExit("ERROR: cloud controller still referenced")
if "RoutedHardwareController" in app_text:
    raise SystemExit("ERROR: routed cloud fallback still referenced")
if "LocalTinyTuyaHardwareController" not in app_text:
    raise SystemExit("ERROR: local controller missing")
if "LOCAL_HARDWARE_REFRESH_MS = 10_000L" not in app_text:
    raise SystemExit("ERROR: local refresh interval missing")
if 'device.id.equals("PS01"' in detail_text:
    raise SystemExit("ERROR: setup is still PS01-only")
if "BUKA LOCAL SETUP $deviceId" not in detail_text:
    raise SystemExit("ERROR: generic setup button missing")
if '"PS01 merespons lewat LAN."' in pilot_text:
    raise SystemExit("ERROR: pilot screen still PS01-only")

print("All-local controller: OK")
print("No cloud fallback: OK")
print("Setup available on all devices: OK")
print("10s local hardware polling: OK")
