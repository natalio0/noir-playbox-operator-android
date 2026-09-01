# Noir Playbox Android — Firebase Operational V1

Patch ini mengubah login demo menjadi Firebase Auth asli dan memvalidasi role
`operational` dari Firestore.

## Yang dilakukan

```text
APK
↓
Firebase Auth email/password
↓
users/{uid}
↓
role == operational ?
↓
ambil cafeId
↓
query devices where cafeId == cafeId operator
↓
tampilkan daftar PS cafe itu
```

Akun `admin` atau role lain otomatis ditolak dan di-sign-out dari APK.

## Website tidak diubah

Patch hanya mengubah project Android.

Tidak ada perubahan pada:
- Next.js website;
- Vercel;
- Firebase data;
- Tuya Cloud;
- session/rental backend.

## 1. Apply patch

Dari root Android project:

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
unzip -o ~/Downloads/noir-playbox-android-firebase-operational-v1.zip -d .
```

## 2. Daftarkan Android app di Firebase

Gunakan Firebase project Noir Playbox yang existing.

Android package:

```text
com.noirplaybox.operator
```

Download:

```text
google-services.json
```

Taruh PERSIS di:

```text
app/google-services.json
```

Bukan di root project.

`google-services.json` tidak perlu dikirim ke chat dan sudah di-ignore oleh `.gitignore`
starter project.

## 3. Build

```bash
./scripts/dev-run.sh
```

atau di VS Code:

```text
Command + Shift + B
```

## 4. Login

Gunakan akun Firebase Auth operational yang sudah existing.

APK melakukan:

```text
Firebase Auth
↓
Firestore users/{uid}
↓
role
↓
cafeId
```

Jika:

```text
role = operational
```

login diterima.

Jika:

```text
role = admin
```

login ditolak.

## 5. Device registry

V1 membaca:

```text
devices
where cafeId == <cafeId operator>
```

Ada compatibility fallback ke `cafe_id`.

Nama device diprioritaskan dari:
- `name`
- `deviceName`
- `label`
- fallback document ID

Status card saat ini membaca best-effort field pada document device:
- `state`
- `status`
- `rentalState`
- `online` / `isOnline`

Jika schema registry tidak menyimpan runtime state, card fallback ke READY.
Pada tahap berikutnya kita akan menyambungkan card ke backend/runtime source yang sama
dengan website operational agar status ACTIVE/PREPARING/countdown 100% identik.

## Scope patch ini

SUDAH:
- Firebase Auth nyata
- role operational nyata
- cafeId nyata
- daftar PS dari Firestore nyata
- session login restore
- logout
- responsive tablet/smartphone

BELUM:
- rental action backend
- PREPARING backend
- ACTIVE countdown backend
- ADD TIME
- STOP
- SHUTDOWN
- Tuya hardware control
- TinyTuya

Tahap berikutnya setelah login + daftar PS berhasil adalah menghubungkan operational API
existing supaya flow APK sama persis dengan website.
