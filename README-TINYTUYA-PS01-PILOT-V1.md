# Noir Playbox Android — TinyTuya PS01 Pilot V1

## Tujuan

Menguji BARDI PS01 langsung dari APK Android lewat LAN:

```text
Android APK
   ↓ Wi-Fi/LAN
TinyTuya embedded
   ↓ TCP 6668
BARDI PS01
```

**Tidak melewati Tuya Cloud untuk tombol Local Pilot.**

Pilot ini sengaja belum mengganti `TransitionalCloudHardwareController` yang
dipakai lifecycle rental. Jadi kalau test lokal gagal, flow production existing
tidak ikut rusak.

## Dependency

- Chaquopy 17.0.0
- Python runtime 3.13
- TinyTuya 1.20.0
- PyCryptodome 3.21.0

TinyTuya dipasang dengan `--no-deps`, karena pilot LAN tidak menggunakan
TinyTuya Wizard/Cloud API.

## Apply

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1

unzip -o ~/Downloads/noir-playbox-android-tinytuya-ps01-pilot-v1.zip -d .

./scripts/apply-tinytuya-ps01-pilot-v1.sh
```

Jika Python 3.13 belum ada:

```bash
brew install python@3.13
```

Lalu:

```bash
./scripts/apply-tinytuya-ps01-pilot-v1.sh
./scripts/tinytuya-doctor.sh
./scripts/dev-run.sh
```

## Cara membuka pilot

```text
Dashboard
→ PS01
→ OPEN DETAIL
→ TinyTuya Local Pilot
→ BUKA LOCAL PILOT PS01
```

## Data yang diisi di perangkat

- Tuya Device ID PS01
- IP BARDI PS01 pada Wi-Fi lokal
- local_key
- protocol version, biasanya 3.3 / 3.4 / 3.5 sesuai device
- switch DPS, default 1

**Jangan kirim local_key ke chat.**

Semua konfigurasi tersebut disimpan lokal secara terenkripsi menggunakan
Android Keystore AES/GCM.

## Test order

Pertama:

```text
TEST STATUS VIA LAN
```

Kalau berhasil, layar akan menampilkan raw DPS.

Baru centang:

```text
Saya paham tombol ... mengontrol monitor PS01 langsung...
```

Lalu test:

```text
MONITOR ON · LOCAL
MONITOR OFF · LOCAL
```

## Network requirement

Android dan BARDI harus berada pada LAN/Wi-Fi cafe yang sama.

TinyTuya menggunakan local network. Pastikan jaringan tidak memakai client/AP
isolation yang memblokir komunikasi antar-device.

Untuk direct device control TinyTuya menggunakan TCP port 6668.

Jika emulator tidak bisa menjangkau BARDI walaupun Mac bisa, test APK pada
perangkat Android fisik yang terhubung ke Wi-Fi yang sama.

## Penting: belum production switch

V1 hanya test PS01.

Setelah tiga test ini lolos:

```text
STATUS ✅
ON ✅
OFF ✅
```

tahap berikutnya adalah:

```text
PS01 lifecycle
TransitionalCloudHardwareController
              ↓
LocalTinyTuyaHardwareController
```

Kemudian test seluruh lifecycle. Setelah stabil, konfigurasi PS02–PS05 dan
hapus Tuya Cloud dari jalur operational hardware.
