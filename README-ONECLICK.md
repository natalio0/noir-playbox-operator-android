NOIR PLAYBOX — VS CODE ONE CLICK V1
===================================

Apply dari root project:

cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
unzip -o ~/Downloads/noir-playbox-vscode-oneclick-v1.zip -d .

Setelah itu di VS Code:

Command + Shift + P
→ Tasks: Run Task
→ Noir: Run App

Atau gunakan shortcut build default:

Command + Shift + B

Karena `Noir: Run App` diset sebagai default build task, shortcut tersebut akan:

1. Start emulator jika belum hidup
2. Tunggu Android boot selesai
3. Build APK debug
4. Install APK
5. Launch Noir Playbox Operator

Task lain tetap tersedia:
- Noir: Start Emulator
- Noir: Stop Emulator
- Noir: Build Debug APK
- Noir: Install Debug APK
- Noir: Build + Install + Run
- Noir: Android Doctor
- Noir: Setup VS Code Tooling
- Noir: Logcat

Patch ini hanya mengubah `.vscode/tasks.json`.
Source APK, UI, Firebase, website, dan rental logic tidak disentuh.
