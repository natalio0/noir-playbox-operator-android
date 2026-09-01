NOIR PLAYBOX — JAVA 21 GRADLE LOCK V2
=====================================

Masalah:
Build gagal hanya menampilkan:

    25.0.2

Artinya Gradle/Kotlin kembali dijalankan memakai JBR / Java 25 dari Android Studio.

Fix ini mengunci Gradle ke JDK 21 di dua tempat:

1. `gradle.properties`
   `org.gradle.java.home=<JDK 21 path>`

2. `scripts/android-env.sh`
   `JAVA_HOME` dipaksa ke JDK 21

Jadi Android Studio boleh punya JBR 25, tetapi project Noir Playbox tetap memakai JDK 21.

APPLY
-----

cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1

unzip -o ~/Downloads/noir-playbox-java21-gradle-lock-v2.zip -d .

./scripts/apply-java21-gradle-lock-v2.sh

VERIFY
------

./scripts/android-doctor.sh

Harus terlihat kira-kira:

    Java      : openjdk version "21..."
    JAVA_HOME : /Users/hazel/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home

RUN
---

./scripts/dev-run.sh
