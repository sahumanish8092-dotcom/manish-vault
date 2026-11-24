# Manish Vault 3.0 device test checklist

## Upgrade safety
1. Keep Manish Vault 2.0 installed. Do not uninstall or clear app data.
2. Install the 3.0 APK over 2.0. Confirm the existing PIN opens the existing files, notes and passwords.
3. Before storing valuable data, export/keep independent copies of test files.

## Core vault regression
4. Import, open, share and delete a test image, video and PDF.
5. Add/edit/delete a note and password entry; verify after app restart.
6. Change main PIN, lock, and verify only the new PIN works. Check auto-lock.

## Recovery
7. In Security Center, create a Recovery Key and save it offline. Do not share it in chat or screenshots.
8. Lock, tap Forgot PIN, use the Recovery Key and set a temporary new PIN. Verify that existing encrypted files still open.
9. Replace the Recovery Key only after saving the newly generated key; the old key must stop working.

## Decoy vault
10. Create a different Decoy PIN. Lock and enter it. Confirm the decoy vault starts empty and does not show main files/notes/passwords.
11. Add a decoy test note and file, lock, and re-enter the decoy PIN. Confirm they persist.
12. Return to main PIN and confirm the main vault data is unchanged.
13. Change the Decoy PIN using the current Decoy PIN, then verify old decoy data remains accessible under the new one.
14. Delete the decoy vault only after confirming you do not need its contents.

## Anti-theft and diagnostics
15. Enter wrong PINs; confirm the activity log records attempts and cooldown starts after repeated failures.
16. In Security Center, inspect RAM and storage values. They are informational.
17. Tap Record Last Known Location; deny permission once, then grant it and test again with Location enabled. Confirm location stays local.
18. Test the manual share sheet for the last-known-location alert. Nothing should send until the user chooses a destination.
19. Activate local Theft Response and verify temporary previews are deleted and the vault locks. Unlock and clear the local theft flag if desired.

Do not store important personal data until these checks pass on the target phone. Remote lock/wipe, camera intruder capture, SIM-change monitoring and cloud backup are not active in this build.
