NOIR PLAYBOX — FIREBASE REPOSITORY SYNTAX FIX V1

Masalah:
FirebaseOperationalRepository.kt memiliki satu karakter backslash (`\`) nyasar
pada baris pertama akibat generator patch sebelumnya.

Efek:
- Kotlin menganggap package/import invalid.
- FirebaseOperationalRepository tidak bisa dikenali.
- Error kemudian merembet ke NoirPlayboxApp.kt.

Apply:

cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
unzip -o ~/Downloads/noir-playbox-firebase-repository-syntax-fix-v1.zip -d .
./scripts/apply-firebase-repository-syntax-fix-v1.sh
./scripts/dev-run.sh
