# Manish Vault — UI Preview 0.1

A responsive, offline-friendly front-end prototype for the Manish Vault Android app concept.

## Included in this preview
- Premium black-and-gold dashboard
- Overview, My Files, Secret Notes, Passwords, Security Center screens
- PIN creation/unlock gate (local demo only)
- Add demo entries for files/notes/password records
- Local browser persistence for preview records

## Important security status
This is a **visual and interaction prototype**, not a production secure vault. Preview records are stored in browser localStorage without encryption. Do not enter real passwords, private documents, or sensitive information. The PIN gate is only a demo gate and is not secure authentication. Biometric unlock, Android Keystore integration, AES-GCM encrypted file storage, safe file import/export, and audited backup/restore are not implemented yet.

## Preview
Open `index.html` in a modern browser. On Android, extract the ZIP and open the file in Chrome or serve the folder locally. The first launch asks you to create a demo PIN.

## Next Android packaging stage
This ZIP is the reviewed UI prototype source. The next stage is to wrap this UI in a native Android WebView project and add native biometric/Keystore features. APK compilation requires Android SDK/Gradle tooling; it is not included as a prebuilt APK in this ZIP.
