# Disposable Android prototype

Application ID: `dev.expensetracker.prototype`. Not a production identity. Google Play is now selected. This direct-install prototype does not qualify the Play launch gate. Its original version 0.0.1 footer predates that decision; it does not override the current PRD.

## Scope

- Static, explicitly fictional payment demo.
- Separate opt-in READ_SMS / RECEIVE_SMS grant probe.
- Refresh grant state after returning from Android settings.
- No SMS queries, receivers, notification listener, capture, network access, storage, activation or sharing.

The status “Not granted” intentionally does not guess whether Android/installer restrictions or user denial caused it. After a completed request, lack of an Android rationale produces a settings/restriction hint, not a definitive diagnosis of installer restrictions. Grant success is not evidence of real-alert capture.

## Build

Requires Java 17, Android platform 36 and build-tools 36.0.0. From the repository root on the configured development machine:

```sh
source ops/release/android-env.sh
cd android
./gradlew :core-model:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

The wrapper verifies the Gradle 8.13 distribution checksum. Versions are pinned in `gradle/libs.versions.toml`; dependency locking is enabled. Freeze the supported toolchain only after a clean CI build.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. The release task checks optimized compilation but produces an unsigned APK: no production signing key is configured. Debug keys are disposable and must not become production keys. CI debug keys may differ between runners, so CI artifacts cannot establish same-key updates; use the same local debug key for repeated local prototype builds.

## Phone check (development evidence only)

1. Install the development APK using a deliberate local transfer. This does not validate a launch distribution channel. Uninstall any different-key build first only if willing to erase its disposable data.
2. Open the app: it must say “Prototype — not production” and locked. No permission prompt should appear on launch or when opening the synthetic demo.
3. Verify that the permission-check button is disabled until explicit consent.
4. Consent and run the check. Record READ_SMS and RECEIVE_SMS separately; restricted SMS grants may be unavailable for this installer.
5. Deny a prompt and verify the app still works, without claiming capture is available.
6. Open Android app settings, change/revoke grants, return and verify the status refreshes. Repeat using the refresh button.
7. Rotate the phone, test large text and a screen reader; confirm controls and consent are understandable.
8. Do not send SMS contents, statements or screenshots of real financial data to GitHub.

No live bank alert test is authorized by this APK: it has no activation or capture path. Keep all public-launch gates pending. A new phone can run the same prototype; there is no private ledger to migrate in this build.
