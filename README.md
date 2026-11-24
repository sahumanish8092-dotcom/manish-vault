# Manish Vault 2.0 — Android rebuild

This is a fresh Android Gradle project reconstructed from the old Manish Vault material available in the user's archive. It is **not** an original Sketchware `.swb` backup.

## What was recovered from the old project archive

- The archived login screen HTML is retained at `app/src/main/assets/login.html`.
- The archived home screen HTML is retained at `app/src/main/assets/home.html`.
- The original Sketchware `FileActivity.java` and `SketchwareUtil.java` are retained under `reference/sketchware-original/` for traceability.
- The home page keeps the original image-based layout and its original button map: Settings, Search, Files, Folders, Photos, Videos, Notes, Recent, Favorites, Trash, and About Phone.

The original `.swb` file and every original activity/layout source were not present in the archive, so this rebuild cannot honestly be described as a byte-for-byte 100% clone of the old Sketchware project. The two archived web screens are preserved as supplied; secondary native screens are recreated for this Gradle app.

## Features in this rebuild

- Original archived login and home-screen HTML.
- First-run creation of a username and master password. The password is stored as a PBKDF2-HMAC-SHA256 hash with a random salt, not as readable text.
- Biometric unlock through Android's system biometric prompt on supported devices.
- Password reset only after confirming the Android device screen lock.
- Import files, photos, and videos through Android's system document picker.
- Imported items are copied into the app's private internal storage under `VaultFiles/` and the current implementation encrypts stored vault files using AES-GCM with an Android Keystore-managed key.
- Folders, Notes, Recent items, Favorites, Search, Recycle Bin, Restore, Rename, and permanent deletion.
- About Phone and basic security settings.
- A GitHub Actions workflow that builds a debug APK and publishes it as a downloadable workflow artifact.

## Important storage/security note

Imported files are stored in the app's private internal storage, which is isolated from ordinary apps by Android. This build does **not** encrypt file contents at rest. Do not treat it as a professionally audited password manager or a replacement for an encrypted backup. Uninstalling the app or clearing its storage deletes its private vault data.

Remote wipe, theft-location alerts, SIM-change monitoring, trusted-device remote control, and intruder selfie capture are not included in this build. These require additional implementation; remote actions also require a configured backend. No covert camera capture is provided.

## Build locally

Requirements:
- JDK 17
- Android SDK Platform 35 and Build Tools 35.0.0
- Gradle 8.9

From the project root:

```bash
gradle --no-daemon assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build using GitHub Actions (recommended for a phone-only workflow)

The connected repository already contains this workflow. Push a commit to `manish-vault-2.0` or use **Actions → Build Manish Vault 2.0 APK → Run workflow**. When the run finishes, download the `Manish-Vault-2.0-debug-APK` artifact, extract it, and install `app-debug.apk` on your Android phone.

Example Git commands after creating the empty GitHub repository (replace the URL with your own):

```bash
git init
git add .
git commit -m "Rebuild Manish Vault 2.0"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPOSITORY.git
git push -u origin main
```

## Horus note

Horus can be used to edit the project and run Git commands in its Linux workspace. A successful Android APK build still requires Gradle, JDK 17, and the Android SDK. The included GitHub Actions workflow provides those build tools on GitHub-hosted runners, so Horus is not required to contain the full Android SDK locally.
