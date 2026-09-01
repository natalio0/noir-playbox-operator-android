#!/usr/bin/env python3
"""Upload TinyTuya devices.json into Noir Playbox Firestore registry.

Usage:
  python3 scripts/upload_tuya_devices_to_firestore.py \
    --file ~/devices.json \
    --cafe-id black-lounge

Authentication uses Firebase Admin SDK. Set GOOGLE_APPLICATION_CREDENTIALS
or pass --service-account /path/to/serviceAccount.json.
"""

import argparse
import json
import os
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, firestore


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--file", required=True, help="Path to TinyTuya devices.json")
    p.add_argument("--cafe-id", required=True, help="Noir cafeId, e.g. black-lounge")
    p.add_argument("--service-account", help="Firebase service account JSON path")
    return p.parse_args()


def load_devices(path: Path):
    raw = json.loads(path.read_text(encoding="utf-8"))
    rows = raw if isinstance(raw, list) else raw.get("devices", [])
    if not isinstance(rows, list):
        raise ValueError("Format devices.json tidak dikenali.")
    return rows


def first(row, *keys, default=""):
    for key in keys:
        value = row.get(key)
        if value is not None and str(value).strip():
            return str(value).strip()
    return default


def main():
    args = parse_args()
    file_path = Path(args.file).expanduser().resolve()
    if not file_path.exists():
        raise SystemExit(f"File tidak ditemukan: {file_path}")

    if args.service_account:
        cred = credentials.Certificate(str(Path(args.service_account).expanduser().resolve()))
        firebase_admin.initialize_app(cred)
    else:
        # GOOGLE_APPLICATION_CREDENTIALS is honored by ApplicationDefault.
        firebase_admin.initialize_app(credentials.ApplicationDefault())

    db = firestore.client()
    rows = load_devices(file_path)
    uploaded = 0
    skipped = 0

    for row in rows:
        if not isinstance(row, dict):
            skipped += 1
            continue

        device_id = first(row, "id", "dev_id", "device_id")
        local_key = first(row, "key", "local_key", "localKey")
        if not device_id or not local_key:
            skipped += 1
            continue

        payload = {
            "tuyaDeviceId": device_id,
            "name": first(row, "name", "product_name", default="Tuya Device"),
            "localKey": local_key,
            "protocolVersion": first(row, "version", "protocol_version", "protocolVersion", default="3.3"),
            "switchDps": 1,
            "enabled": True,
            "source": "tinytuya-devices-json",
            "updatedAt": firestore.SERVER_TIMESTAMP,
        }

        db.collection("cafes").document(args.cafe_id).collection("tuyaDevices").document(device_id).set(
            payload,
            merge=True,
        )
        uploaded += 1
        print(f"✓ {device_id}  {payload['name']}")

    print()
    print(f"Cafe        : {args.cafe_id}")
    print(f"Uploaded    : {uploaded}")
    print(f"Skipped     : {skipped}")
    print(f"Firestore   : cafes/{args.cafe_id}/tuyaDevices/<tuyaDeviceId>")


if __name__ == "__main__":
    main()
