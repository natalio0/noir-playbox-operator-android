NOIR PLAYBOX — Kotlin JVM Target Fix V1
========================================

Error:
  Using 'jvmTarget: String' is an error.
  Please migrate to the compilerOptions DSL.

Fix:
  kotlinOptions { jvmTarget = "17" }

diganti menjadi:

  kotlin {
      compilerOptions {
          jvmTarget.set(JvmTarget.JVM_17)
      }
  }

APPLY:

  cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
  unzip -o ~/Downloads/noir-playbox-kotlin-jvmtarget-fix-v1.zip -d .
  ./scripts/apply-kotlin-jvmtarget-fix-v1.sh
  ./scripts/dev-run.sh

Patch ini hanya memperbaiki Gradle/Kotlin config.
