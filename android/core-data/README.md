# Encrypted storage foundation

Implements the storage boundary in build plan §4 using Room 2.8.3, SQLCipher for Android 4.20.0 and Android Keystore. The Compose prototype does **not** open a database or capture alerts yet. This is not the completed Room v1 schema/migration work package.

## Implemented

- `DatabaseKeyVault`: one random 32-byte database secret per authenticated subject. A Keystore AES-256-GCM key wraps it, with the subject digest authenticated as AAD. Paths use SHA-256 rather than account identifiers.
- Wrapped keys, databases and sidecars live under Android's `noBackupFilesDir`. Backup/device-transfer exclusions also cover every applicable app data domain.
- Missing keys, corrupted envelopes and existing database/journal files without their secret fail closed. Nothing is silently erased, regenerated over existing data or opened as plaintext.
- Wrapping does not require a biometric prompt; background access and the eventual UI lock are independent as specified.
- `EncryptedRoomFactory`: caller-supplied Room class and explicit migrations, SQLCipher native initialization, foreign keys enabled, no destructive-migration fallback and immediate opening/key/schema verification. Run on a worker thread. Keep the returned handle alive while using the database, then close it to clear the retained factory secret.
- `PreservingCipherFactory`: uses SQLCipher's SQLiteOpenHelper/Room interface with an explicit non-deleting corruption handler. Upstream's default adapter supplies a null handler, which can invoke destructive corruption recovery. Minimum-supported-version deletion is disabled (zero).
- SQLCipher Java logging is disabled and core cipher logging is set to none on open. JNI/library log behavior still needs device privacy review before release; this is not a completed dependency audit.

## Tests

- Six JVM AES-GCM tests: round trip, random IVs, wrong key, wrong subject, tampering, malformed/unknown envelope. These do not emulate Android Keystore.
- Seven compiled Android tests: vault recreation, subject separation, missing/tampered keys, orphan journal, SQLCipher encrypted-file reopen/wrong-key rejection, and non-destructive corrupt-file handling. They use generated synthetic data only. USB interruptions prevented execution; device results remain pending. Do not claim that encrypted database opening has been device-verified yet.

```sh
# From android/, with the documented Java/SDK environment
./gradlew :core-data:testDebugUnitTest :core-data:assembleDebugAndroidTest
# Only when an authorized device is connected and installation is convenient:
./gradlew :core-data:connectedDebugAndroidTest
```

## Still required

The full Room schema/DAOs, schema export, migrations, account/session composition, explicit wipe/reactivation UI, key-loss/backup device validation and Room/SQLCipher integration tests against the concrete schema remain to be implemented. A generic opener does not validate a schema that does not exist. Never introduce an unencrypted fallback or destructive recovery to make a test pass.

Source references reviewed: [SQLCipher Android integration](https://github.com/sqlcipher/sqlcipher-android), including `SupportHelper`, `SQLiteOpenHelper` and `DatabaseErrorHandler` behavior. Native encrypted-file tests must pass before this layer is connected to private payment records.
