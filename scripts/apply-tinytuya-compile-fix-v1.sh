#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Applying TinyTuya Kotlin compile fix..."

python3 - <<'PY'
from pathlib import Path

base = Path("app/src/main")

files_to_clean = [
    base / "java/com/noirplaybox/operator/hardware/TinyTuyaBridge.kt",
    base / "java/com/noirplaybox/operator/hardware/TinyTuyaSecureStore.kt",
    base / "java/com/noirplaybox/operator/ui/screens/TinyTuyaPilotScreen.kt",
    base / "python/noir_tinytuya_bridge.py",
]

for path in files_to_clean:
    if not path.exists():
        raise SystemExit(f"ERROR: Missing file: {path}")

    lines = path.read_text().splitlines()
    while lines and lines[0].strip() == "\\":
        lines.pop(0)

    path.write_text("\n".join(lines).rstrip() + "\n")
    print(f"Cleaned first line: {path}")

detail = base / "java/com/noirplaybox/operator/ui/screens/DeviceDetailScreen.kt"
if not detail.exists():
    raise SystemExit(f"ERROR: Missing file: {detail}")

text = detail.read_text()

old_lifecycle = """private fun LifecycleCard(
    device: PlayboxDevice,
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    onPrepare: () -> Unit,
    onCancelPreparing: () -> Unit,
    onStartRental: (RentalPackage) -> Unit,
    onAddTime: (RentalPackage) -> Unit,
    onStopRental: () -> Unit,
    onStartShutdown: () -> Unit,
    onRetryShutdownMonitor: () -> Unit,
    onFinishShutdown: () -> Unit,
    onOpenLocalPilot: () -> Unit
) {"""

new_lifecycle = """private fun LifecycleCard(
    device: PlayboxDevice,
    packages: List<RentalPackage>,
    actionLoading: Boolean,
    onPrepare: () -> Unit,
    onCancelPreparing: () -> Unit,
    onStartRental: (RentalPackage) -> Unit,
    onAddTime: (RentalPackage) -> Unit,
    onStopRental: () -> Unit,
    onStartShutdown: () -> Unit,
    onRetryShutdownMonitor: () -> Unit,
    onFinishShutdown: () -> Unit
) {"""

old_shutdown = """private fun ShutdownActions(
    device: PlayboxDevice,
    actionLoading: Boolean,
    onStartShutdown: () -> Unit,
    onRetryShutdownMonitor: () -> Unit,
    onFinishShutdown: () -> Unit,
    onOpenLocalPilot: () -> Unit
) {"""

new_shutdown = """private fun ShutdownActions(
    device: PlayboxDevice,
    actionLoading: Boolean,
    onStartShutdown: () -> Unit,
    onRetryShutdownMonitor: () -> Unit,
    onFinishShutdown: () -> Unit
) {"""

if old_lifecycle in text:
    text = text.replace(old_lifecycle, new_lifecycle, 1)
    print("Fixed LifecycleCard signature.")
else:
    print("LifecycleCard signature already fixed or differs.")

if old_shutdown in text:
    text = text.replace(old_shutdown, new_shutdown, 1)
    print("Fixed ShutdownActions signature.")
else:
    print("ShutdownActions signature already fixed or differs.")

detail.write_text(text)

controller = base / "java/com/noirplaybox/operator/hardware/LocalTinyTuyaHardwareController.kt"
if not controller.exists():
    raise SystemExit(f"ERROR: Missing file: {controller}")

text = controller.read_text()

old_when = """status = when (status.switchOn) {
                        true -> HardwareStatus.ON
                        false -> HardwareStatus.OFF
                        null -> HardwareStatus.UNKNOWN
                    },"""

new_when = """status = when (status.switchOn) {
                        true -> HardwareStatus.ON
                        false -> HardwareStatus.OFF
                        null -> HardwareStatus.UNKNOWN
                        else -> HardwareStatus.UNKNOWN
                    },"""

if old_when in text:
    text = text.replace(old_when, new_when, 1)
    print("Added defensive else branch.")
else:
    print("Switch when-expression already fixed or differs.")

controller.write_text(text)

# Validation
for path in files_to_clean:
    first = path.read_text().splitlines()[0]
    if first.strip() == "\\":
        raise SystemExit(f"ERROR: Leading backslash still present: {path}")

detail_text = detail.read_text()

li = detail_text.index("private fun LifecycleCard")
le = detail_text.index(") {", li) + 3
if "onOpenLocalPilot" in detail_text[li:le]:
    raise SystemExit("ERROR: onOpenLocalPilot still present in LifecycleCard.")

si = detail_text.index("private fun ShutdownActions")
se = detail_text.index(") {", si) + 3
if "onOpenLocalPilot" in detail_text[si:se]:
    raise SystemExit("ERROR: onOpenLocalPilot still present in ShutdownActions.")

print()
print("Static source validation: OK")
PY

rm -rf app/build build .gradle/kotlin 2>/dev/null || true

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

echo
echo "Compile fix applied ✅"
echo
echo "Run:"
echo "  ./scripts/dev-run.sh"
