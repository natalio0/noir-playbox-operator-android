NOIR PLAYBOX — FIREBASE BUILD FIX V1
=====================================

Fixes:
1. Kotlin compiler 2.0.21 -> 2.3.21
   Firebase Auth 24.2.0 contains Kotlin metadata 2.3.0.
2. Remove old FakeOperationalRepository.kt.
3. Fix Result callback typing in FirebaseOperationalRepository.kt.
4. Stop old Gradle daemon and clear project build output.

APPLY
-----

cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1

unzip -o ~/Downloads/noir-playbox-firebase-build-fix-v1.zip -d .

./scripts/apply-firebase-build-fix-v1.sh

BUILD + RUN
-----------

./scripts/dev-run.sh

google-services.json tetap harus berada di:

app/google-services.json

Patch ini tidak mengubah website, Firebase data, role, cafe data, atau Tuya.
