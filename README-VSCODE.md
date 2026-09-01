# Noir Playbox Operator — VS Code + Terminal Patch V1

Patch ini tidak mengubah source UI, Firebase, rental flow, atau website.
Patch hanya menambahkan workflow development Android lewat VS Code/Terminal.

## Apply ke project

Pastikan berada di:

```text
/Users/hazel/AndroidStudioProjects/NoirPlayboxOperator
```

Extract ZIP patch langsung ke root project.

Contoh:

```bash
cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperator
unzip -o ~/Downloads/noir-playbox-vscode-terminal-patch-v1.zip -d .
```

## Pertama kali

```bash
./scripts/setup-vscode.sh
./scripts/android-doctor.sh
```

`setup-vscode.sh` akan mendownload official Gradle wrapper JAR 8.9 jika belum ada.

Android SDK yang sudah terinstall dari Android Studio akan otomatis dicari di:

```text
~/Library/Android/sdk
```

JDK Android Studio juga otomatis dicari di:

```text
/Applications/Android Studio.app/Contents/jbr/Contents/Home
```

## Buka VS Code

```bash
code /Users/hazel/AndroidStudioProjects/NoirPlayboxOperator
```

## Build APK

```bash
./scripts/build-debug.sh
```

Hasil:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install ke emulator/tablet

Nyalakan emulator atau hubungkan device, lalu:

```bash
./scripts/install-debug.sh
```

## Build + install + buka app

Satu command:

```bash
./scripts/run-android.sh
```

## Dari VS Code tanpa terminal manual

Tekan:

```text
Command + Shift + P
→ Tasks: Run Task
```

Tersedia:
- Noir: Setup VS Code Tooling
- Noir: Android Doctor
- Noir: Build Debug APK
- Noir: Install Debug APK
- Noir: Build + Install + Run

Shortcut build default:

```text
Command + Shift + B
```

## Log Android

```bash
./scripts/logcat-noir.sh
```

## Android Studio masih dibutuhkan?

Tidak untuk coding sehari-hari.

Android Studio tetap berguna untuk:
- memasang/update Android SDK;
- membuat/menyalakan emulator;
- debugging native yang kompleks.

Source Kotlin/Compose, Gradle, build APK, install APK, dan run app bisa dilakukan dari VS Code + Terminal.

## Yang tidak disentuh patch ini

- website Noir Playbox;
- Firebase;
- Tuya Cloud;
- TinyTuya;
- source APK/UI;
- operational rental logic.

Aman dipakai hanya sebagai developer tooling patch.
