NOIR PLAYBOX OPERATOR — STARTER V1
===================================

Target V1:
- Kotlin + Jetpack Compose
- responsive smartphone/tablet
- login operational demo
- daftar PS responsive
- detail device
- paket rental demo
- struktur siap Firebase/backend
- TIDAK menyentuh website production

CARA BUKA
---------
1. Extract ZIP.
2. Buka Android Studio.
3. File > Open.
4. Pilih folder `NoirPlayboxOperatorStarterV1`.
5. Tunggu Gradle Sync selesai.
6. Pilih emulator Pixel Tablet.
7. Tekan Run ▶.

LOGIN DEMO
----------
Untuk V1, isi email dan password apa pun, lalu LOGIN.

Ini bukan security production. Tujuannya memastikan UI APK bisa jalan.

RESPONSIVE
----------
- < 600dp   : 1 kolom
- 600–899dp : 2 kolom
- >= 900dp  : 3 kolom

Jadi project yang sama dapat dites di smartphone dan tablet.

BELUM DI V1
-----------
- Firebase Auth nyata
- Firestore nyata
- existing Next.js API authentication
- Tuya Cloud
- TinyTuya
- foreground gateway service
- boot receiver

Semua itu sengaja bertahap supaya APK pertama cepat terbukti bisa build/install.

CATATAN GRADLE WRAPPER
----------------------
ZIP ini menyertakan konfigurasi Gradle 8.9 tetapi tidak menyertakan binary
`gradle-wrapper.jar`.

Untuk penggunaan awal, buka lewat Android Studio dan lakukan Gradle Sync.
Android Studio akan menangani toolchain Gradle yang dibutuhkan.

Setelah project sukses sync, kita bisa generate wrapper lengkap dari Android Studio
bila kamu ingin build 100% dari terminal.
