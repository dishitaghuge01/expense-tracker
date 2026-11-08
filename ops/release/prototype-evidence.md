# Disposable prototype build evidence

This is development evidence, not completion of work package 0 or any public-launch gate.

## Original build verification (historical)

- Application ID `dev.expensetracker.prototype`, code 1, version `0.0.1-prototype`.
- Java 17, minimum API 29, compile/target API 36; Gradle 8.13 distribution checksum verified.
- CI source commit `b8724d6`: [successful test/lint/debug/unsigned-release build](https://github.com/dishitaghuge01/expense-tracker/actions/runs/42458924739).
- Local equivalent build succeeded, then succeeded again without `--write-locks` using the checked-in dependency locks.
- Three pure Kotlin tests passed: integer-paise synthetic totals, permission-state truth table, and revocation state.
- Static prototype boundary check passed. It is a tripwire, not a complete security audit.
- Local and CI development APK signatures verified with `apksigner` (v2 signing).
- CI APK metadata inspection confirms min 29 / target 36, READ_SMS and RECEIVE_SMS, plus AndroidX's app-scoped signature-level dynamic-receiver permission. No INTERNET permission.
- Lint passes. Update-version suggestion checks are disabled to keep deliberate stable version pins; OldTargetApi is disabled because the specification fixes API 36. Correctness/privacy lint checks remain enabled.

## Original version 0.0.1 local APK (historical build)

- Path: `android/app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `ec130061568780c9f6a72390dbd7db2a8977d7a8eed89d3a106b4e11f6931de2`
- Signed with the local development debug key, not a production key.
- CI artifacts expire after seven days and use a different disposable signing key. Their APK hashes differ; do not mix builds when testing same-key updates.
- No sensitive data is stored by the prototype's application code. No private ledger or full backup exists to migrate.

## Redmi development check and process-restart regression

The version 0.0.1 local debug APK was installed over USB after the owner approved the Redmi installation prompt; `am start -W` reported a successful cold launch. This is not a Play-install gate result or a formal performance benchmark.

Owner reported both READ_SMS and RECEIVE_SMS as granted with capture disabled. After denying SMS in Android settings, both displayed “Not checked” instead of an actual non-granted state. The original in-memory-only resume guard loses probe history on activity/process recreation.

Version 0.0.2 (code 2) persists only the probe-started boolean and refreshes real grant status on resume/relaunch. No alert content or permission result is persisted, and no prompt/capture is automatically enabled. It also updates the footer to reflect the selected Play launch.

- Version 0.0.2 with the unused pure Kotlin foundation, SHA-256 `cb3f094c648f2fa302c10c60e3d78499c45f217009bb8e47cd8439707e117613`, was installed over USB and opened successfully. The original 0.0.2 fix-build hash was `59f1df1a3a9d8735238765c3225690241479628ad6efb27392c243ac771b3066`.
- Local locked-dependency tests, lint, debug and optimized unsigned-release builds pass; APK v2 signature verified.
- Disposable unsigned `bundleRelease` build passes and AAB ZIP integrity check reports no errors. This is not Play acceptance or production signing.
- Owner completed the 0.0.2 consent/grant/revoke/reopen retest and reported the expected “Not granted” result. This is owner-reported Redmi development evidence, not automated proof of every Android process-death path or Play-install qualification.
- Same-key USB update installation succeeded from 0.0.1 to 0.0.2; broader data-preservation/update testing remains pending because no real ledger exists.
- Rotation, large text, screen-reader and Play-install/live-alert checks remain pending.

## Current version 0.0.3 foundation build

- Code 3, debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256: `e69ef0ae3108d986403c4ff184d559ee77ab617ba91ffb4a49a23fae81fa5616`.
- 40 core-model tests and 6 encryption-envelope JVM tests pass; lint, debug/release APK, unsigned AAB and Android storage-test compilation pass using dependency locks.
- Adds fail-closed capture consent/source states and inactive Keystore-wrapped-secret/SQLCipher/Room opening infrastructure. The prototype does not open a ledger or read alerts. Full Room v1 schema/DAOs/migrations are not implemented yet.
- Seven Android storage tests are compiled but not executed successfully: USB disconnects prevented device discovery. Owner requested further device/user tests be deferred; no more install prompts triggered. Native SQLCipher/Keystore outcomes remain pending.
- Debug APK signature verifies, packaged app permissions still contain only READ_SMS/RECEIVE_SMS plus AndroidX's internal signature permission, and `zipalign -c -P 16` passes. SQLCipher arm64-v8a/x86_64 ELF LOAD segments report 0x4000 alignment. These are static packaging checks, not a 16 KiB-device runtime test.
- Version 0.0.3 has not been installed on the phone.

No live alert reading, parsing, activation or sharing exists in this build. No claim of bank compatibility or installer qualification is made. Google Play has since been selected. Production domain/package/Play signing identity, Play permission eligibility, Play-track distribution/update tests, commerce/budget revisions and all other PRD gates remain pending. The original prototype footer reflects the earlier deferred-channel decision. A replacement phone requires its own device-specific checks.
