# Encrypted storage foundation

Implements the storage boundary in build plan §4 using Room 2.8.3, SQLCipher for Android 4.20.0 and Android Keystore. The Compose prototype does **not** open a database or capture alerts yet. The eleven-table development ledger/rules/preferences slice is implemented; this is not the completed v1 product storage work package.

## Implemented

- `DatabaseKeyVault`: one random 32-byte database secret per authenticated subject. A Keystore AES-256-GCM key wraps it, with the subject digest authenticated as AAD. Paths use SHA-256 rather than account identifiers.
- Wrapped keys, databases and sidecars live under Android's `noBackupFilesDir`. Backup/device-transfer exclusions also cover every applicable app data domain.
- Missing keys, corrupted envelopes and existing database/journal files without their secret fail closed. Nothing is silently erased, regenerated over existing data or opened as plaintext.
- Wrapping does not require a biometric prompt; background access and the eventual UI lock are independent as specified.
- `EncryptedRoomFactory`: caller-supplied Room class and explicit migrations, SQLCipher native initialization, foreign keys enabled, no destructive-migration fallback and immediate opening/key/schema verification. Run on a worker thread. Keep the returned handle alive while using the database, then close it to clear the retained factory secret.
- `PreservingCipherFactory`: uses SQLCipher's SQLiteOpenHelper/Room interface with an explicit non-deleting corruption handler. Upstream's default adapter supplies a null handler, which can invoke destructive corruption recovery. Minimum-supported-version deletion is disabled (zero).
- SQLCipher Java logging is disabled and core cipher logging is disabled with a checked `PRAGMA cipher_log = 'off'` query on open. This setter returns a row: do not use `execSQL` or the destination `none`. JNI/library log behavior still needs a full privacy review before release; this is not a completed dependency audit.

## Ledger schema and DAOs

`ledger/LocalLedgerDatabase` development version 3 adds `local_settings` and `budget_alerts` to version 2's merchant/sharing rules, typed undo and coverage tables, and version 1's `account_alias`, `personal_category`, `personal_budget`, `local_transaction` and `capture_observation`. The explicit additive `FROM_1_TO_2` and `FROM_2_TO_3` migration chain leaves old tables/data/triggers intact; frozen v1/v2 exports are retained. Open it through `LocalLedgerDatabase.open` so its constraint callbacks are installed alongside encryption. UUID/date/month/UTC-nanosecond converters preserve canonical values; instants use fixed nine-digit fractions for correct text ordering.

The DAO atomically records a normalized event and its observation, rejects non-private initial records, and treats fingerprint replay idempotently without overwriting corrections. Different IDs remain distinct. Category edits/exclusion use version checks, never mutate amount/date, and do not enqueue changes to an existing share. There is no blank transaction-entry UI or generic transaction update DAO.

Triggers enforce integer amount bounds, captured amount/date immutability and active category-name uniqueness via SQLite `lower(name)`; archived categories retain their history. Category labels must already be trimmed/NFC-normalized. SQLite's built-in lowercase behavior is not a claim of complete Unicode case-folding parity with PostgreSQL. Schema JSON does not capture these triggers, so they also have separate compiled device tests.

`LocalRulesDao` stores future merchant rules without modifying past records, prefers exact account scopes, and returns all enabled exact sharing matches without resolving category conflicts or sharing anything. Initial sharing rules default disabled. Coverage confirmation is a separate digest-bound operation requiring zero unresolved rows; saving coverage never verifies a period automatically. Undo operations are insert-only, expire within 24 hours and use strict typed JSON containing only category inverse changes and rule snapshots—not arbitrary alert fields. Undo application and scheduled expiry cleanup are not implemented yet.

`LocalPreferencesDao` initializes settings only when explicitly requested, with capture opt-ins off and an explicit device-screen-lock-dependent default. Reinitializing preserves choices; missing-row setters do not create settings. Typed packs, cached permission health and <=90-day scan checkpoints carry no raw alerts and cannot grant activation. List UPDATEs explicitly bind a single encoded JSON cell. Budget markers include ledger kind plus identity, category, month and threshold; insert-ignore keeps the first emitted timestamp and prevents duplicates. No notification delivery or scheduling is implemented.

The prototype does not instantiate this database. Household cache and outbox/sync tables remain to be implemented. The full v1 storage deliverable is incomplete; add explicit migrations for existing development databases as this schema evolves rather than enabling destructive fallback.

## Tests

- 32 passing JVM tests: eight ledger-value/converter tests, eight rule/coverage/strict undo-JSON tests, ten preferences/health/checkpoint/scope tests and six AES-GCM envelope tests. These do not emulate Android Keystore.
- 36 Android tests executed and passed on an owner-authorized Redmi Android 13 phone: seven vault/native SQLCipher cases, eight baseline Room cases, seven rule/undo/coverage cases, seven preferences/marker cases, two encrypted frozen-old-schema migration cases, and five new Room-open isolation/regression cases. The first run found 24 Room-open failures; the reviewed logging-PRAGMA repair and unchanged original tests then passed. Synthetic data only; one-device evidence, not an OEM/Android matrix. See [independent device evidence](../../ops/release/device-storage-verification.md).
- `ops/release/check-room-schema.py` verifies the exported field allowlist, integer affinities, keys/FKs and synthetic in-memory SQLite DDL. This is not a substitute for the Android encryption/trigger tests.

```sh
# From android/, with the documented Java/SDK environment
./gradlew :core-data:testDebugUnitTest :core-data:assembleDebugAndroidTest
# Phone execution requires arranging installation with the owner.
# Do not use connectedDebugAndroidTest unattended: its runner collects broad logcat.
# Use reviewed, identifier-redacted direct instrumentation as documented in:
# ../../ops/release/device-storage-verification.md
```

## Still required

Remaining Room tables/DAOs and migrations, account/session composition, explicit wipe/reactivation UI, key-loss/backup device validation and broader device/release/privacy validation remain pending. This partial development schema is not the completed product database. Never introduce an unencrypted fallback or destructive recovery to make a test pass.

Source references reviewed: [SQLCipher Android integration](https://github.com/sqlcipher/sqlcipher-android), including `SupportHelper`, `SQLiteOpenHelper` and `DatabaseErrorHandler` behavior. Native encrypted-file tests passed on the recorded phone; this layer remains disconnected from private payment records pending the other activation, privacy and product gates.
