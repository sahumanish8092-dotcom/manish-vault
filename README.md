# Manish Vault 3.0 – Ultimate Security Core

Premium black + electric-blue Android vault. It keeps the same `applicationId` (`com.manish.vault`), the same SharedPreferences file, and the same main vault data file names. Android still requires the APK signing certificate to match for an in-place update. The earlier 2.0 GitHub build did not preserve its debug keystore as an artifact/cache, so the 3.0 APK may be rejected as an update if that earlier certificate differs. Do not uninstall 2.0 or clear its data if Android reports a signature conflict. The workflow now caches the 3.0 debug key so future 3.x builds stay consistent.

## Implemented in 3.0 source

- Existing encrypted Photos/Videos and Documents import, open, share, delete.
- Existing encrypted Secret Notes and Password Manager.
- Existing six-digit PIN, PIN change, auto-lock, secure window flag.
- AES-256-GCM file and store encryption; PBKDF2-derived PIN wrapping.
- **Forgot PIN** recovery using a randomly generated 128-bit Recovery Key. The recovery key is displayed once at setup/replacement.
- Failed-PIN event log and increasing temporary lockout cooldown.
- Separate Decoy Vault with its own PIN, master key, encrypted store and files; changing the decoy PIN re-wraps the same decoy key.
- Manual, permission-gated last-known location capture; coordinates stay on this device until the user manually shares an alert.
- Local theft-response flag, emergency lock, temporary decrypted preview cleanup.
- RAM / app-heap and storage status dashboard (read-only; Android manages RAM).
- Security activity log (metadata only; no passwords, note bodies or file names).

## Important upgrade and recovery notes

1. Build and install this APK **over** Manish Vault 2.0. Do not uninstall 2.0 and do not clear its app data.
2. Unlock with the existing 2.0 PIN. Your existing main vault files remain in the existing `vault_data` folder and `vault_store.enc` file.
3. Immediately open **Security Center → Set up Recovery Key**. Save the 32-character code offline. It is shown only once. Replacing it invalidates the previous code.
4. Create a different Decoy PIN only if you need a decoy profile. The decoy vault starts empty and stores its data separately.
5. Test with copies of non-sensitive files before moving valuable data. Keep an independent backup.

If both the main PIN and the Recovery Key are lost, AES-GCM encrypted data cannot be recovered. A Recovery Key is not sent to a server and is not stored in plaintext by the app.

## Explicitly not connected in this build

- Remote lock / remote wipe / automatic theft notifications: require a trusted owner account, secure backend, pairing, authenticated commands and a deployment endpoint. No fake or unauthenticated remote-control endpoint is included.
- SIM-change monitoring and background camera capture: not enabled. Android restricts these capabilities and they require separate, visible consent and platform-specific testing. The app does not secretly record camera, microphone or location.
- Cloud backup, push notifications, biometric key wrapping and battery-usage diagnostics: not yet implemented.

Location is optional and requested only when the user taps **Record Last Known Location**. The app reads the device's last-known location; it is not live tracking and nothing is uploaded automatically. RAM values are informational and do not require broad storage or device-admin permissions.

## Build

GitHub Actions runs `gradle --no-daemon assembleDebug` and uploads `Manish-Vault-3.0-Ultimate-APK`. The generated debug APK is for personal testing, not Play Store release.
