# Noir Playbox Operator Android — Project Handoff

## Architecture

```text
ADMIN -> Next.js web

OPERATIONAL -> Android APK
  -> Firebase/backend: auth, role, cafeId, preparing, session, billing, add time, stop, shutdown, history
  -> TinyTuya LAN: BARDI monitor status / ON / OFF
```

TinyTuya is hardware transport only. Firebase/backend remains the business and billing source of truth.

## Current Android state

- Package: `com.noirplaybox.operator`
- Kotlin + Jetpack Compose
- Firebase Auth + Firestore
- Chaquopy embedded Python
- TinyTuya 1.20.0 + PyCryptodome
- Gradle builds with JDK 21
- Realtime dashboard PS01–PS05
- Device detail + rental lifecycle implemented
- TinyTuya PS01 local pilot succeeded:
  - status via LAN
  - DPS decrypted
  - local ON
  - local OFF
- PS01 protocol: 3.5
- PS01 main relay: DPS 1
- local_key stored encrypted using Android Keystore

## Lifecycle rules

```text
READY
-> PREPARING
-> ACTIVE / Firebase billing
-> STOP: Firebase COMPLETE first
-> monitor OFF
-> SHUTDOWN_PENDING
-> SHUTDOWN_ACTIVE + monitor ON
-> operator shuts down PS4 normally
-> FINISH SHUTDOWN
-> monitor OFF
-> READY
```

BARDI controls the monitor only, never hard-powers the PS4.

## Migration target

```text
PS01 -> LocalTinyTuyaHardwareController -> LAN
PS02–PS05 -> TransitionalCloudHardwareController temporarily
```

After PS01 passes repeated full lifecycle tests, migrate PS02–PS05 and remove Tuya Cloud from the operational hardware path.

## Known bug to fix next

During an ACTIVE test, Android displayed about:

```text
28:15:13
```

while the same session showed:

```text
Total billing: Rp22.000
Total durasi: 120 menit
```

This is wrong.

Investigate Android ACTIVE countdown parsing/calculation:
- seconds vs milliseconds
- `startedAt` / `endAt`
- Firestore Timestamp / ISO / epoch conversion
- accidental x1000 or /1000
- duration must not depend on timezone
- ACTIVE countdown must come from Firebase/backend session timing, NOT TinyTuya DPS countdown

Do not break the proven TinyTuya PS01 local pilot while fixing this.

## Security — never commit

- `devices.json`
- `tinytuya.json`
- `snapshot.json`
- `tuya-raw.json`
- Tuya Access Secret
- local_key
- service-account private keys
- `.env` secrets

## Continue in a new ChatGPT conversation

Send the GitHub repository URL and say:

`Read PROJECT-HANDOFF.md first and use the repo as the source of truth. First fix the ACTIVE countdown showing ~28 hours for a 120-minute session. Preserve PS01 TinyTuya LAN control.`
