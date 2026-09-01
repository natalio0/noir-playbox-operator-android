# Noir Playbox Android — Realtime Overview V2

Kiblat: source web Noir Playbox yang diberikan user.

## Tujuan

V2 memisahkan **business state** dan **hardware transport** sejak awal.

```text
Firebase/backend existing
├─ /api/devices
├─ /api/sessions/active
├─ /api/preparing/active
└─ /api/shutdown/active
       ↓
BusinessRuntime
       ↓
RealtimeOverviewRepository
       ↑
HardwareController
       ├─ TransitionalCloudHardwareController (sementara)
       └─ LocalTinyTuyaHardwareController (target final)
```

### Rule penting

Business state selalu menang atas hardware:

```text
ACTIVE session
> SHUTDOWN
> PREPARING
> hardware OFFLINE
> READY
```

Artinya hardware offline tidak boleh menghapus ACTIVE billing Firebase.

## Cloud usage sementara

Untuk membuat dashboard tetap berguna sebelum TinyTuya Android aktif:

- business runtime refresh: 15 detik, **tanpa Tuya Cloud**;
- hardware cloud refresh: initial/manual/15 menit;
- countdown ACTIVE: 1 detik lokal dari `startedAt + totalMinutes`;
- PREPARING clock: lokal;
- tidak ada hardware polling per 15 detik.

Saat TinyTuya siap:

```text
TransitionalCloudHardwareController
↓ ganti
LocalTinyTuyaHardwareController
```

Business/backend layer tidak perlu dibongkar.

## Apply

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
unzip -o ~/Downloads/noir-playbox-android-realtime-v2.zip -d .
./scripts/apply-realtime-v2.sh
./scripts/dev-run.sh
```

## Yang sudah aktif di V2

- Firebase Auth operational existing
- server-scoped device registry `/api/devices`
- active rental dari backend
- PREPARING dari backend
- SHUTDOWN pending/active dari backend
- countdown ACTIVE berdasarkan Firebase timestamp
- preparing elapsed clock lokal
- hardware telemetry transitional
- responsive 1/2/3 column
- PS4 visual dari source web
- manual refresh
- TinyTuya-ready hardware boundary

## Belum aktif

V2 sengaja belum menyalakan tombol action agar tidak membuat dua implementasi business flow sekaligus.
Patch berikutnya:

```text
SIAPKAN RENTAL
→ PREPARING
→ START RENTAL
→ ADD TIME
→ STOP
→ SHUTDOWN
→ SELESAI SHUTDOWN
```

Action tersebut akan memakai API/backend existing untuk Firebase/business logic,
dan HardwareController untuk ON/OFF sehingga nanti TinyTuya dapat menggantikan cloud.
