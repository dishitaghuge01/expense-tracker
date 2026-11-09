# Independent Android storage verification — 9 November 2026

Verified source: `0e31039d17989844570438bd0b052b0a8bd3ad5d`.

## Finding and repair

The first actual phone run at `0947c36` executed 31 synthetic tests: seven passed and 24 Room/migration/DAO tests failed with bounded `DATABASE_OPEN_FAILED` errors. Earlier host success and Android compilation had not established native Room behavior.

Separate implementation and test-writing agents investigated; an independent verifier executed the integrated repair. SQLCipher 4.20.0 returns a status row for `PRAGMA cipher_log`. The factory incorrectly used `execSQL` and the destination `none`; SQLCipher requires `off` to disable the destination. The minimal repair consumes and closes a query cursor, verifies status zero, and preserves bounded public failures. No exception logging, destructive recovery, schema change or dependency change was added.

Five independent native/Room/factory isolation and fresh-open/reopen regressions were added. Existing tests and assertions were unchanged.

## Actual results

- **78 JVM tests passed** (46 model, 32 data), zero failures/errors/skips.
- Both prototype-boundary and Room-schema checks passed; lint reported zero issues.
- Debug APK, unsigned release APK and unsigned AAB built successfully. App unit tests were NO-SOURCE, not extra passes.
- **36 tests executed and passed on the phone**: all original 31 plus five new regressions; zero failures/errors/skips.
- Native outcomes included both frozen-old-schema migrations, encryption/reopening, corrupt/wrong-key preservation, foreign keys/triggers, rules/preferences and concurrency.
- Frozen v1/v2/v3 schema exports and all tracked locks remained byte-identical.

Phone: Xiaomi/Redmi model 21091116AI, Android 13/API 33. This is one device/run, not a supported-device matrix or Play qualification.

## Host commands

```sh
source ops/release/android-env.sh
python3 ops/release/check-prototype-boundary.py
python3 ops/release/check-room-schema.py
(cd android && ./gradlew --no-daemon --max-workers=2 \
  :core-model:test :core-data:testDebugUnitTest :core-data:assembleDebugAndroidTest \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease)
git diff --exit-code -- android/core-data/schemas
```

## Safe phone execution

The owner explicitly authorized the connected-phone run. The default Gradle connected runner was **not used**: its UTP plugin starts broad logcat collection; the inspected legacy alternative automatically uninstalls APKs. Preflight stopped before installation, then the coordinator reviewed direct instrumentation instead.

The verifier checked exactly one authorized device in memory, redacted unique identifiers before output/persistence, and verified the APK manifest/package/hash. Only the separate library package `dev.expensetracker.prototype.data.test` was installed using `adb install -r -t`, without `-g` or uninstall. Its instrumentation target is itself, not the prototype app. Then:

```text
adb -s [DEVICE] shell am instrument -w -r -e disableAnalytics true \
  dev.expensetracker.prototype.data.test/androidx.test.runner.AndroidJUnitRunner
```

`[DEVICE]` is a redacted placeholder, not a recorded identifier. No logcat, real SMS, inbox queries, runtime grants, prototype update, settings changes or user database access occurred. Test fixtures were synthetic. The test APK remains installed.

Native protocol showed 36 unique starts, 36 successful terminal events and final `OK (36 tests)`. Counts come from actual instrumentation events, not annotations or adb exit alone. Private JUnit XML was derived from those events and explicitly labelled; it is not native Gradle XML. No raw device logs/identifiers are published.

Test APK SHA-256: `df0b536263d8cfb18f3f3846bb4f0b5b13a39ca5d5067d754c88a19dcb2c243f`.

Private sanitized evidence/reports remain under ignored `ops/private/agent-team/device-room-open/`; the initial failed run is retained under `ops/private/agent-team/package1-foundation/`.

## Limits

This resolves the observed synthetic Room-opening defect on this source/phone. It does not establish a complete JNI/dependency logging audit, real-app storage integration, release-device behavior, clean byte-identical build reproducibility, production signing, backend correctness, Play eligibility or package completion. The prototype still does not open a ledger or capture alerts. Arrange any future phone installation with the owner; authorization for this batch is not an unattended-test policy.
