# Noir Playbox Android — Rental Lifecycle V3

Patch ini menyambungkan halaman detail Android ke lifecycle backend production.
Source web yang dikirim user menjadi acuan payload dan urutan safety.

## Flow

```text
READY
  ↓ SIAPKAN RENTAL
PREPARING
  ↓ pilih paket
ACTIVE / BILLING
  ├─ ADD TIME
  └─ STOP / expiry
       ↓
SHUTDOWN_PENDING
  ↓ MULAI SHUTDOWN MODE
SHUTDOWN_ACTIVE
  ↓ SELESAI SHUTDOWN
READY
```

## Source of truth

Business:
- Firebase Auth
- `/api/preparing/*`
- `/api/sessions/*`
- `/api/shutdown/*`

Hardware:
- `HardwareController`
- sekarang `TransitionalCloudHardwareController`
- final `LocalTinyTuyaHardwareController`

Tuya Cloud tidak dipanggil langsung oleh UI atau lifecycle business code.

## Safety order

- PREPARING: hardware ON + Firebase PREPARING paralel, rollback bila salah satu gagal.
- START RENTAL: hardware timer lalu Firebase session; Firebase gagal -> hardware STOP rollback.
- ADD TIME: Firebase dulu; hardware sync failure hanya warning karena billing sudah benar.
- STOP: Firebase COMPLETE dulu, lalu hardware STOP best-effort.
- COMPLETE membuat `SHUTDOWN_PENDING` persistent.
- START SHUTDOWN: Firebase `SHUTDOWN_ACTIVE` dulu, baru monitor ON.
- FINISH SHUTDOWN: monitor STOP dulu, baru audit shutdown COMPLETE.
- Expired ACTIVE session: APK menjalankan jalur STOP Firebase-first otomatis.

## Apply

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
unzip -o ~/Downloads/noir-playbox-android-rental-lifecycle-v3.zip -d .
./scripts/apply-rental-lifecycle-v3.sh
./scripts/dev-run.sh
```

## Test aman

Mulai dari PS01 saja:

1. Open Detail PS01.
2. SIAPKAN RENTAL -> harus PREPARING dan monitor ON.
3. Mulai paket 1 Jam -> ACTIVE + countdown + billing.
4. ADD TIME -> total menit dan revenue bertambah.
5. STOP SESSION -> billing selesai dan SHUTDOWN_PENDING muncul.
6. MULAI SHUTDOWN MODE -> monitor ON tanpa billing/PREPARING.
7. Shutdown PS4 secara normal.
8. SELESAI SHUTDOWN -> monitor OFF, state kembali READY.

## TinyTuya

V3 belum mengaktifkan TinyTuya embedded. Yang penting, semua action hardware sekarang sudah lewat interface:

```text
HardwareController
```

Jadi implementasi berikutnya dapat mengganti cloud bridge menjadi TinyTuya LAN tanpa mengubah lifecycle Firebase.
