# Noir Playbox Android — Multi-Device Local V5

V5 removes Tuya Cloud from the Android operational hardware path.

```text
Firebase/backend
= auth, cafe scope, preparing, billing/session, add time, stop, shutdown, history

TinyTuya LAN
= BARDI status + monitor ON/OFF

PS01 -> TinyTuya LAN
PS02 -> TinyTuya LAN
PS03 -> TinyTuya LAN
PS04 -> TinyTuya LAN
PS05 -> TinyTuya LAN
```

There is NO automatic Tuya Cloud fallback. A device without local config shows
`LOCAL NOT READY` and hardware commands fail safely instead of calling cloud.

## Apply

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
unzip -o ~/Downloads/noir-playbox-android-multidevice-local-v5.zip -d .
./scripts/apply-multidevice-local-v5.sh
./scripts/multidevice-local-v5-doctor.sh
./scripts/dev-run.sh
```

## Configure PS02–PS05

For each device:

```text
Dashboard
-> OPEN DETAIL
-> BUKA LOCAL SETUP PS02/PS03/PS04/PS05
-> Device ID
-> IP
-> Local Key
-> Protocol
-> Switch DPS
-> SAVE CONFIG
-> TEST STATUS
-> LOCAL OFF
-> LOCAL ON
```

Do not use a unit for rental until STATUS/OFF/ON all pass locally.

## When moving to cafe

If a plug is reset/re-paired:
- IP changes.
- local_key may change.
- provision it again and update the APK local config.

## Polling

Local hardware status refreshes every 10 seconds. The 5 device reads run
concurrently, so an offline unit doesn't block the other units sequentially.

## Important

Do not run:

```bash
adb shell pm clear com.noirplaybox.operator
```

after saving all local keys unless you intentionally want to erase every local
TinyTuya configuration. Keys are stored encrypted with Android Keystore, not in
Firebase or source code.
