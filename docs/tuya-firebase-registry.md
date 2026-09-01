# Noir Playbox — TinyTuya Firebase Registry

Android reads TinyTuya secrets from:

`cafes/{cafeId}/tuyaDevices/{tuyaDeviceId}`

Example document:

```json
{
  "tuyaDeviceId": "bfxxxxxxxxxxxx",
  "name": "Bardi Plug PS01",
  "localKey": "xxxxxxxxxxxxxxxx",
  "protocolVersion": "3.3",
  "switchDps": 1,
  "logicalDeviceId": "PS01",
  "enabled": true
}
```

`logicalDeviceId` is optional. If omitted, the operator selects PS01/PS02 in the app before saving.

## Upload devices.json

Install uploader dependency:

```bash
python3 -m pip install -r scripts/requirements-tuya-upload.txt
```

Then upload:

```bash
python3 scripts/upload_tuya_devices_to_firestore.py \
  --file ~/devices.json \
  --cafe-id YOUR_CAFE_ID \
  --service-account ~/Downloads/firebase-service-account.json
```

Do not include the Firebase Admin service-account JSON inside the Android project or APK.

## Android flow

1. Operator logs in; app knows `cafeId` from `users/{uid}`.
2. App scans the local network using TinyTuya.
3. For each discovered Tuya Device ID, the app does an exact Firestore document get.
4. If the registry contains the device, its local key is loaded automatically.
5. Operator selects the PlayBox logical ID and saves.
6. The local key is stored locally using the existing encrypted TinyTuya secure store.

The app does not list all registry secrets and does not need `devices.json` on the phone.
