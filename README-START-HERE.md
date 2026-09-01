# Noir Operator v0.6 — Start Here

Versi ini merapikan Noir Playbox Operator menjadi aplikasi operator dengan splash screen, Noir branding, bottom navigation, device provisioning, LAN discovery TinyTuya, import `devices.json`, encrypted local-key storage, rental lifecycle, dan realtime monitoring.

## 1. Buka project

```bash
unzip noir-operator-v0.6-ready.zip
cd noir-playbox-operator-android-main
```

## 2. Firebase

Project sengaja tidak menyertakan `google-services.json` karena file tersebut merupakan konfigurasi milik project Firebase Anda. Letakkan file Anda di:

```text
app/google-services.json
```

## 3. TinyTuya Wizard (sekali saat provisioning)

Di Mac/PC yang berada di jaringan yang sesuai:

```bash
python3 -m pip install -U tinytuya
python3 -m tinytuya wizard
```

Wizard membuat `devices.json`. Di aplikasi buka **Devices → Import TinyTuya Wizard**, lalu pilih file tersebut. Local key disimpan terenkripsi melalui Android Keystore, bukan plaintext.

## 4. Jalankan di Android

Dengan Android Studio, buka folder ini dan Run ke perangkat Android arm64.

Atau dari terminal (Android SDK + ADB sudah tersedia):

```bash
./scripts/one-click-install.sh
```

## Alur operator

- **Home**: monitoring PlayBox, status rental, countdown, refresh.
- Tap PlayBox: prepare, mulai rental, tambah waktu, stop, shutdown.
- **Devices**: scan BARDI/Tuya pada Wi-Fi lokal, import local key wizard, pasangkan ke PS01/PS02/dst.
- **Account**: identitas operator/cafe dan logout.

## Catatan LAN

HP dan BARDI/Tuya harus berada pada LAN/Wi-Fi yang sama. TinyTuya LAN discovery memerlukan broadcast UDP di jaringan. Guest Wi-Fi/client isolation dapat membuat scan tidak menemukan perangkat.
