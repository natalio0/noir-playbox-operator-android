#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Applying PS01 Local Lifecycle V4..."

python3 - <<'PY'
from pathlib import Path

path = Path('app/src/main/java/com/noirplaybox/operator/ui/NoirPlayboxApp.kt')
if not path.exists():
    raise SystemExit(f'ERROR: Missing {path}')

text = path.read_text()

anchor = 'import com.noirplaybox.operator.hardware.TransitionalCloudHardwareController\n'
imports = (
    'import com.noirplaybox.operator.hardware.LocalTinyTuyaHardwareController\n'
    'import com.noirplaybox.operator.hardware.RoutedHardwareController\n'
    'import com.noirplaybox.operator.hardware.TransitionalCloudHardwareController\n'
)

if 'import com.noirplaybox.operator.hardware.RoutedHardwareController' not in text:
    if anchor not in text:
        raise SystemExit('ERROR: TransitionalCloudHardwareController import not found.')
    text = text.replace(anchor, imports, 1)

old = '    val api = remember { NoirApiClient() }\n    val hardwareController = remember { TransitionalCloudHardwareController(api) }\n'
new = '''    val api = remember { NoirApiClient() }

    // Migration V4:
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

if old in text:
    text = text.replace(old, new, 1)
elif 'localDeviceIds = setOf("PS01")' in text:
    print('NoirPlayboxApp already routed PS01 locally.')
else:
    raise SystemExit('ERROR: hardwareController initialization pattern not found.')

text = text.replace(
    '// Cloud hardware hanya selama transisi. TinyTuya nanti mengganti controller ini.',
    '// Hybrid migration: PS01 local TinyTuya; remaining devices cloud during transition.'
)

path.write_text(text)

check = path.read_text()
assert 'RoutedHardwareController' in check
assert 'LocalTinyTuyaHardwareController' in check
assert 'localDeviceIds = setOf("PS01")' in check
print('NoirPlayboxApp routing: OK')
PY

python3 - <<'PY'
from pathlib import Path
files = [
    Path('app/src/main/java/com/noirplaybox/operator/hardware/TinyTuyaBridge.kt'),
    Path('app/src/main/java/com/noirplaybox/operator/hardware/LocalTinyTuyaHardwareController.kt'),
    Path('app/src/main/java/com/noirplaybox/operator/hardware/RoutedHardwareController.kt'),
]
for path in files:
    text = path.read_text()
    if text.splitlines()[0].strip() == '\\':
        raise SystemExit(f'ERROR: leading backslash detected in {path}')
    print(f'Source OK: {path}')
PY

rm -rf app/build build .gradle/kotlin 2>/dev/null || true

if [[ -x ./gradlew ]]; then
  ./gradlew --stop || true
fi

echo
echo "PS01 Local Lifecycle V4 applied ✅"
echo
echo "Important:"
echo "  PS01 commands now use TinyTuya LAN."
echo "  PS02-PS05 still use transitional cloud."
echo
echo "Run:"
echo "  ./scripts/dev-run.sh"
