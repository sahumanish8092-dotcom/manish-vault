# Manish Vault 2.0 Neon

Android application (native Java) with a black / electric-blue neon interface.

## Implemented in this version

- Existing six-digit PIN hash is preserved for upgrades from the first Manish Vault build.
- On first unlock after upgrade, a random 256-bit vault key is created and wrapped with a PIN-derived AES key. New installs do the same at PIN creation.
- AES-256-GCM encryption for imported files and the local notes/password/file-index database.
- Photos and videos: import via Android document picker, encrypted app-private storage, open/share decrypted temporary copies, delete.
- Secure documents: same encrypted import/open/share/delete flow for PDFs and other file types.
- Secret notes: create, edit, delete.
- Password manager: create, edit, delete, reveal and copy. Clipboard contents are cleared after 30 seconds when they still match the copied password.
- Security settings: change PIN (re-wraps the vault key) and configure auto-lock (30 seconds, 1 minute, 5 minutes, 10 minutes).
- Temporary decrypted preview files are removed when the vault locks. Screenshots are blocked while this app is open.
- Android Auto Backup is disabled.

## Build

The project uses Android Gradle Plugin 8.7.3, Gradle 8.9, Java 17, compile SDK 35 and AndroidX Core 1.13.1. The GitHub Actions workflow builds `app/build/outputs/apk/debug/app-debug.apk` and uploads it as `Manish-Vault-APK`.

## Important security notes

- Imported files are encrypted after selection. The original file in the phone's Photos/Downloads app is not deleted or changed.
- Opening or sharing a file creates a temporary decrypted copy in this app's cache. It is deleted when the vault locks; a receiving app may make its own copy after sharing.
- The vault is local to this app installation. There is no cloud sync or server-side recovery.
- **Remember the PIN. If it is forgotten, encrypted vault data cannot be recovered.** Uninstalling or clearing app data also removes the local vault.
- A six-digit PIN has limited entropy. Use a strong device lock as well, and do not treat this app as a substitute for a professionally audited password manager.
- This source change has not been compiled or exercised on a physical Android device in this environment. Run the GitHub Actions build and then follow the device test checklist before relying on it for important data.
