# Noir Playbox Android — PS01 Local Lifecycle V4

## What changes

PS01 is now routed to TinyTuya LAN for **all hardware operations**:

```text
PS01
PREPARING       -> TinyTuya monitor ON
START RENTAL    -> TinyTuya monitor ON + Firebase session
ADD TIME        -> Firebase billing + local monitor verification
STOP            -> Firebase COMPLETE first -> TinyTuya monitor OFF
SHUTDOWN START  -> Firebase state first -> TinyTuya monitor ON
SHUTDOWN FINISH -> TinyTuya monitor OFF -> Firebase shutdown complete
```

PS02-PS05 remain on `TransitionalCloudHardwareController` for now.

## Important separation

```text
Firebase/backend
= billing, session, preparing, shutdown, history

TinyTuya LAN
= BARDI hardware status + monitor ON/OFF
```

TinyTuya never becomes the billing source of truth.

## Apply

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1

unzip -o ~/Downloads/noir-playbox-android-ps01-local-lifecycle-v4.zip -d .

./scripts/apply-ps01-local-lifecycle-v4.sh

./scripts/ps01-local-v4-doctor.sh

./scripts/dev-run.sh
```

## Existing PS01 local config

The package name/signing key does not change. If `run-android.sh` installs with
update/replace rather than uninstalling the app, the encrypted PS01 config saved
from Local Pilot should remain in Android app data.

If PS01 shows `TinyTuya config belum disimpan`, open:

```text
PS01 -> Open Detail -> TinyTuya Local Pilot
```

and re-save the config.

## Verify dashboard

After refresh, PS01 should show:

```text
TinyTuya LAN
```

PS02-PS05 should still show:

```text
Tuya Cloud · transition
```

## Safe test order

Use PS01 only:

```text
1. READY
2. SIAPKAN RENTAL
   -> monitor ON locally
   -> PREPARING recorded in Firebase
3. Start 1 Jam
   -> ACTIVE billing starts in Firebase
4. ADD TIME (optional)
5. STOP SESSION
   -> Firebase COMPLETE first
   -> monitor OFF locally
   -> SHUTDOWN_PENDING
6. MULAI SHUTDOWN
   -> Firebase shutdown active first
   -> monitor ON locally
7. Shut down the PS4 normally
8. SELESAI SHUTDOWN
   -> monitor OFF locally
   -> shutdown audit closes
9. Return to READY
```

Do not unplug the main power before the shutdown flow is complete.

## Deliberately not changed

- Tuya Cloud code is not deleted yet.
- PS02-PS05 are not migrated yet.
- Billing is not moved to local storage.
- `local_key` is not placed in Firebase/source code.
- Production web is not changed.

Once PS01 passes multiple complete rentals, migrate PS02-PS05 and remove Tuya
Cloud from the operational hardware path entirely.
