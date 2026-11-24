# Manish Vault 2.0 device test checklist

Use a spare test image, video, PDF and non-sensitive test credentials first.

1. Install the APK over the current Manish Vault app (do not uninstall the old app if retaining its PIN/settings matters).
2. Unlock with the existing six-digit PIN. Confirm the home screen has blue neon edges and all five sections open.
3. Photos & Videos: import one test image and one test video; confirm both appear, open, share and delete work.
4. Secure Documents: import a test PDF; open, share and delete it.
5. Secret Notes: add, edit, restart app, verify persistence, then delete a test note.
6. Password Manager: add, edit, reveal, copy, confirm clipboard clears after 30 seconds, then delete a test entry.
7. Security Settings: change to a temporary test PIN; lock and unlock with the new PIN; verify the old PIN no longer works. Set each auto-lock duration and confirm the expected timeout.
8. Lock Vault: verify it returns to the PIN screen and that a wrong PIN cannot open the data.
9. Restart the phone and verify the vault still opens with the correct PIN and encrypted data remains present.

Do not store important personal data until the app has passed the tests above on the target phone.
