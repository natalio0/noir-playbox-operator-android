# Noir Playbox Android — Local Fleet V5

## V5 fixes the impossible 28-hour countdown

A 120-minute rental must never display more than 02:00:00.

V5 now:
- syncs Android time against the backend HTTP `Date` header;
- uses that server-adjusted clock for ACTIVE / PREPARING / SHUTDOWN;
- clamps ACTIVE remaining time to the purchased duration.

## V5 also removes PS01 hard-coding

A device automatically becomes TinyTuya-local when its encrypted local config
exists on the Android controller.

```text
config saved?
   YES -> TinyTuya LAN
   NO  -> transitional cloud
```

So later PS02, PS03, PS04 and PS05 can be migrated without another routing patch.

## Moving a device to cafe Wi-Fi

Open:

```text
Dashboard
-> device
-> OPEN DETAIL
-> TinyTuya Local Setup
```

Update the IP and save.

If moving Wi-Fi requires resetting/re-pairing the BARDI, the local_key may also
change. In that case update BOTH the IP and local_key, then run:

```text
SAVE CONFIG
-> TEST STATUS VIA LAN
```

## Apply

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1

unzip -o ~/Downloads/noir-playbox-android-local-fleet-v5.zip -d .

./scripts/apply-local-fleet-v5.sh

./scripts/local-fleet-v5-doctor.sh

./scripts/dev-run.sh
```

## Important test rule

Do not manually delete only `sessions/{id}` in Firestore.

The backend uses `device_runtime/{PSxx}` as its fast runtime state. Manually
deleting one session document without clearing runtime can leave the device
appearing ACTIVE/PREPARING or otherwise out of sync.

Use the APK lifecycle buttons for normal reset/testing:
- cancel PREPARING
- STOP SESSION
- complete SHUTDOWN

That keeps the session documents and `device_runtime` synchronized.
