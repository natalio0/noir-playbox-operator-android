# Noir Playbox — TinyTuya v3.5 Fix V2

This patch fixes the PS01 Local Pilot behavior where TinyTuya returned:

    Err 904
    Unexpected Payload from Device

but the APK incorrectly showed "PS01 merespons lewat LAN".

Changes:
- TinyTuya error dictionaries are now treated as failures.
- v3.5 device setup follows documented pattern:
  Device(...) -> set_version(3.5) -> persistent socket.
- socket timeout raised to 5 seconds.
- retry limit raised to 3.
- ON/OFF verification stays on the same persistent 3.5 socket/session.
- Local key is never logged by this bridge.

Apply:

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
unzip -o ~/Downloads/noir-playbox-tinytuya-v35-fix-v2.zip -d .
./scripts/apply-tinytuya-v35-fix-v2.sh
./scripts/dev-run.sh
```

Then open:

Dashboard -> PS01 -> Open Detail -> TinyTuya Local Pilot

and run `TEST STATUS VIA LAN` again.

If it still returns 904 while the Mac wizard succeeds, re-check the value typed in
the APK:
- Device ID must match PS01 exactly.
- IP: 192.168.0.100 for the current home LAN.
- Protocol: 3.5.
- Switch DPS: 1.
- Local Key: copy only the value of the `key` field from devices.json, without
  JSON quotes or surrounding spaces.
