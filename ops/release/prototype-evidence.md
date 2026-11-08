# Disposable prototype build evidence

This is development evidence, not completion of work package 0 or any public-launch gate.

## Verified

- Application ID `dev.expensetracker.prototype`, code 1, version `0.0.1-prototype`.
- Java 17, minimum API 29, compile/target API 36; Gradle 8.13 distribution checksum verified.
- CI source commit `b8724d6`: [successful test/lint/debug/unsigned-release build](https://github.com/dishitaghuge01/expense-tracker/actions/runs/42458924739).
- Local equivalent build succeeded, then succeeded again without `--write-locks` using the checked-in dependency locks.
- Three pure Kotlin tests passed: integer-paise synthetic totals, permission-state truth table, and revocation state.
- Static prototype boundary check passed. It is a tripwire, not a complete security audit.
- Local and CI development APK signatures verified with `apksigner` (v2 signing).
- CI APK metadata inspection confirms min 29 / target 36, READ_SMS and RECEIVE_SMS, plus AndroidX's app-scoped signature-level dynamic-receiver permission. No INTERNET permission.
- Lint passes. Update-version suggestion checks are disabled to keep deliberate stable version pins; OldTargetApi is disabled because the specification fixes API 36. Correctness/privacy lint checks remain enabled.

## Local APK for Redmi testing

- Path: `android/app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `ec130061568780c9f6a72390dbd7db2a8977d7a8eed89d3a106b4e11f6931de2`
- Signed with the local development debug key, not a production key.
- CI artifacts expire after seven days and use a different disposable signing key. Their APK hashes differ; do not mix builds when testing same-key updates.
- No sensitive data is stored by the prototype's application code. No private ledger or full backup exists to migrate.

## Pending — actual phone needed

Follow `android/README.md`: initial launch with no permission prompt; demo without permissions; consent gating; each SMS grant/denial; return-from-settings revocation refresh; rotation; large text and screen reader. Available phone: Redmi Note 11T 5G, Android 13. Not connected or tested yet.

No live alert reading, parsing, activation or sharing exists in this build. No claim of bank compatibility or installer qualification is made. Google Play has since been selected. Production domain/package/Play signing identity, Play permission eligibility, Play-track distribution/update tests, commerce/budget revisions and all other PRD gates remain pending. The original prototype footer reflects the earlier deferred-channel decision. A replacement phone requires its own device-specific checks.
