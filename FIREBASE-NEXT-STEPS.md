# Firebase integration — tahap berikutnya

Starter V1 sengaja memakai `FakeOperationalRepository` supaya APK bisa dibuka
dan diuji dulu tanpa mengubah production website.

Setelah UI V1 berhasil jalan:

1. Daftarkan Android app `com.noirplaybox.operator` pada Firebase project existing.
2. Letakkan `google-services.json` di `app/google-services.json`.
3. Tambahkan Firebase Auth Android SDK.
4. Login menggunakan Firebase Auth.
5. Setelah login, baca profil `users/{uid}`.
6. APK hanya menerima `role == "operational"`.
7. Ambil `cafeId` dari profil.
8. Ambil device yang scoped ke cafe tersebut.
9. Action rental penting tetap diproses backend existing dengan Firebase ID Token.
10. Setelah operational flow stabil, baru tambah TinyTuya local transport.

Prinsip:
- Admin tetap menggunakan web.
- APK khusus operational.
- Billing/session tetap source of truth di backend/Firebase.
- Smart-plug ON bukan sumber billing.
