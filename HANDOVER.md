# Session handover — read before resuming

## 1. Current owner instructions and resumed work

**The owner authorized continuing work on the missing package-1 prerequisites and explicitly connected the phone for debugging.** The earlier handover pause and phone-test deferral are lifted for this agreed work. The owner has since disconnected the phone; arrange any future phone tests first. This is not blanket authorization for unattended installs, spending, cloud provisioning or advancing new package-2 features.

Packages **0 and 1 remain incomplete**; preserve the existing partial package-2 foundations. Current-session changes:
- Added local Supabase/security/seed scaffolding, pinned Deno/Supabase tooling, a Play-adjusted public release-metadata handler, partial OpenAPI/fixtures, independent tests and backend CI. No commerce, auth/household business features, website or staging deployment. See `ops/runbooks/local-backend.md` and `ops/release/backend-foundation-verification.md`. At `f677cb5`, independent typecheck/contract validation and 180 handler/contract tests passed after a reviewed validator typing fix. Initial image pulls timed out, but the final bounded retry recovered: reset/migration/seed, all 58 pgTAP assertions and actual loopback-only bindings passed. At `ef02547`, all nine live HTTP/error/immutability probes plus the rollback audit passed after independent probe review. Live Data API nonexposure still needs verification: the harness's legacy `ANON_KEY` lookup is unavailable under pinned CLI 3; a public-key interface review is underway. CI at `1161d5e` failed on external HTTP 504 downloads; do not claim it is green. Integrated backend verification is separate from Android.
- Actual phone execution at `0947c36` exposed **24 failures out of 31 tests** in the Room opener. Separate agents repaired SQLCipher's row-returning logging PRAGMA (`query`, destination `off`, checked status) and added five independent regressions.
- At **`0e31039`**, independent verification passed **78 JVM tests and all 36 actual Android tests** (original 31 + new five), lint/builds and unchanged schemas/locks. See `ops/release/device-storage-verification.md`. Only the separate synthetic library-test APK was installed; the prototype app and SMS were untouched. Test APK remains installed. No logcat or phone identifiers were persisted.

Sections 3–10 below retain the **previous handover snapshot**, not current verification status. In particular, their claims of zero executed Android tests, no backend scaffold, all changes pushed and paused coding are historical. Use this section and linked current verification documents before relying on those statements.

Standing requirements:
- Follow `prd.md` and `buildplan.md`; ask before changing approaches or sequence. Do not change unrelated files.
- Walk the owner through meaningful steps, make small scoped commits, and push reviewed work.
- Use **three separate agents**: implementation, independent test writing, independent execution/verification. The coordinator reviews/integrates; a verifier does not fix code/tests to make them pass.
- The owner authorized the recorded connected-phone tests. Arrange future installs/prompts first. Avoid Gradle connected tests: its default runner collects broad logcat; use reviewed, identifier-redacted direct instrumentation instead.
- No raw production SMS/statements, credentials, signing private keys or phone identifiers in Git/logs/support reports. No spending or cloud provisioning is implied by permission to code.

## 2. Read order

1. This document, then [PRD](prd.md) and [build plan](buildplan.md), especially §§9–10 and the Play migration boundaries.
2. [Development-agent workflow](ops/runbooks/development-agents.md).
3. [Current settings/budget verification](ops/release/settings-budget-verification.md), [earlier rules verification](ops/release/rules-storage-verification.md), and [prototype evidence](ops/release/prototype-evidence.md).
4. [Android instructions](android/README.md), [core-model](android/core-model/README.md), [core-data](android/core-data/README.md).
5. [Release configuration](ops/release/config.md), [Play readiness](ops/release/play-readiness.md), [distribution checklist](ops/release/distribution-validation.md).

The specifications govern product requirements; progress notes do not override them. Several retained Razorpay/website sections are explicitly **superseded reference**, not active implementation instructions.

## 3. Repository / verified snapshot (historical; see current §1)

- Public source repo (explicit owner override of original private-source plan): https://github.com/dishitaghuge01/expense-tracker
- Branch: `main`; all product changes pushed.
- Last product-code checkpoint: `1ddd18df750055eff6a496462dce224f78ac5e7d`. A later handover-only commit may be HEAD.
- Latest product CI is **green**: [run 42485465210](https://github.com/dishitaghuge01/expense-tracker/actions/runs/42485465210), source `1ddd18d`.
- Independent latest host verification ran source `269027d`; the generated v3 schema and documentation were committed afterward without changing its runtime/test code.
- Before writing this handover the main working tree was clean. Recheck Git/CI rather than assume this snapshot is still current.

### Package status — do not overclaim

| Package | Actual state |
|---|---|
| 0: prototype / distribution validation | Permission-only APK and synthetic demo built; one Redmi USB development check. Play eligibility/track install/update/live-alert gates, identifiers/signing/account setup and volunteer matrix are unfinished. |
| 1: repository / CI | Repo, three Android modules, wrapper/catalog/locks, JVM tests, lint, APK/AAB and schema checks work in CI. **Staging environment/deployment, local Supabase reset/migrate, backend contracts/OpenAPI validation and web typecheck are not implemented.** Production signing and byte-identical clean/cross-directory reproducibility are not established. |
| 2: local engine | **Partial work already done:** money/dates/accounting, consent policy, encrypted-storage opener, partial Room schema/DAOs, rules/undo/coverage/preferences/alert markers. No bank parser/capture/history/dedup pipeline or completed engine. |
| 3–8 | No functional Personal/Household app, auth/leases, sync, billing, pilot or release. Some supporting data structures exist, not those completed features. |

Do not interpret an initial Room schema or a green Android workflow as completion of package 1's entire deliverable.

## 4. Approved decisions (do not re-open without reason)

- **Google Play launch**, not website APK sales. Free installation, **one-time non-consumable Play Billing household unlock**, initially **₹499** for up to five adults, one active capture device per adult.
- Owner may later set supported INR prices such as ₹299/₹399 in Play Console. Existing purchasers retain their licence without paying again, subject to refund/revocation rules. Future purchase UI must use Play-returned pricing, not hardcode ₹499 as a verification rule.
- No Razorpay/website checkout integration. Detailed Play server schema/API/event/restore/refund contract still needs finishing before commerce coding. Billing-model approval is not registration/budget approval.
- Owner intends to publish **as an individual**; no Play Console account yet. Confirm appropriate account classification for this expense-only product before asking them to pay. Relevant public policies are documented, not app approval.
- Owner has **no domain**; purchase can wait during local development. Production domain/application ID/Play signing setup must be settled before public pilot. Do not invent or register a production identity.
- Approved disposable ID: `dev.expensetracker.prototype`. Current local development APK `0.0.3-prototype`, code 3. It must not be promoted to the production Play app. No production key exists.
- Keep all sales closed. No backend/Google OAuth/Supabase/Play configuration has been provisioned.

Policy review found a potential **SMS-based money-management** exception, subject to declaration/review/approval. New personal-account testing currently requires 12 closed-test users opted in continuously for 14 days before applying for production access. Reviewed registration fee: US$25 once, not a confirmed INR charge or authorization to spend. Recheck official requirements at submission; do not claim eligibility from the USB prototype.

## 5. What exists in code

### `android/app`

Single-activity Kotlin/Compose Material 3 prototype: fictional payment demo, explicit-consent READ_SMS/RECEIVE_SMS grant check, refresh on return/relaunch, secure-screen flag and backup exclusions. Probe-started boolean persists in SharedPreferences so revocation/process recreation does not incorrectly reset status to “Not checked.”

**No ledger database is opened by the app. No SMS receiver/inbox query/notification listener, capture, sign-in, billing, sync, real feed or posted reminder exists.** Packaged app has no INTERNET permission. The existing screen remains basic; Home/Personal feed/Review UI is package 3, not completed by data-layer progress.

### `android/core-model`

Pure Kotlin:
- `Inr`: exact integer paise and bounds, normalized statement decimals, known-template amount-digit helper, Indian grouping and checked signed totals. **Not a bank SMS parser.**
- `ReportingDates`: strict ISO/UTC formats, IST reporting, statement date validation, Monday weeks.
- `Accounting`: separately scoped monthly gross/refunds/net/categories/withdrawals, reversals, repeat-record replay and invalid-refund review. **Not cross-source alert deduplication.**
- `CaptureAccess`: independent opt-ins, permissions, pause, registration and lease-state gates. It consumes an **already verified** lease status; it does not verify signed leases or register devices.
- `BudgetThresholds`: integer-ceiling 80%/100% NET-spend thresholds and suppression of prior emissions, including negative/extreme spend. It does not send notifications.

### `android/core-data`

- Per-subject random database secrets wrapped by Android Keystore AES-GCM; subject digests authenticate envelopes and name files. Data lives under `noBackupFilesDir`.
- SQLCipher/Room opener retains the secret until handle close, forces opening/key/schema verification, enables FKs and refuses destructive fallback. Wrapping is available to background work; UI lock is separate.
- `PreservingCipherFactory` supplies a non-deleting corruption handler: upstream default behavior can delete a database. Do not revert this protection or regenerate missing keys over existing DB/journal data.
- JNI/library logging/privacy and actual native opening still require device review; host crypto tests do not prove Android Keystore/SQLCipher behavior.
- Room development schema **v3**, explicit **1→2→3** migrations and frozen exports under `android/core-data/schemas/`.

Eleven tables:

```text
account_alias       personal_category  personal_budget
local_transaction   capture_observation
merchant_rule       sharing_rule       correction_undo
coverage_check      local_settings     budget_alerts
```

DAO behavior includes atomic detected-record/observation insertion, private initial records, replay without overwriting corrections, version-checked category/exclusion changes, monthly budgets, exact merchant scopes, disabled-by-default sharing rules, strict typed undo JSON with insert-only IDs/24-hour expiry, explicit digest-bound coverage confirmation, singleton settings, typed health/checkpoints and identity-scoped once-only alert markers.

Important limits:
- No generic amount/date editing; SQLite triggers also enforce immutability and money bounds.
- Category-name uniqueness uses SQLite `lower(name)` plus typed NFC validation, not full Unicode case-folding parity with PostgreSQL.
- List UPDATEs explicitly encode **one JSON string cell**; don't introduce Room list-placeholder expansion when editing these setters.
- Cached health, stored opt-ins and scan checkpoints **do not authorize capture**. Lock defaults require an explicit current device-lock input; actual biometric/device-credential UI is absent.
- Rule application/preview, bulk correction/undo application, scheduled expiry cleanup, statement CSV workflow, notification posting, cache/outbox/sync tables and full product storage remain unfinished.
- Preserve old schema JSON and add explicit migrations when extending this partial development database. Do not renumber/wipe it to pretend it is a finished “v1 product database.”

## 6. Verification and phone evidence

**Latest actual results:** 78 passing JVM tests (46 model + 32 data), zero failures/errors/skips; lint/build/static checks green locally and in current CI. App unit-test task is NO-SOURCE, not additional passing tests.

**31 Android tests compile; zero executed on Android.** They cover encryption/Keystore/Room/migrations/concurrency but remain deferred. Static host SQLite DDL alignment and schema guards are not device migration/trigger/native proof. Clean byte-identical build reproducibility and production signing are unproven.

Owner's short-term phone: Redmi Note 11T 5G, model 21091116AI, Android 13 (`TP1A.220624.014`). Do not record its unique USB/device ID. Current connection/availability must be checked only when testing is agreed.

- USB installation/launch of v0.0.1 succeeded after Redmi approval.
- First permission-revocation test exposed the in-memory probe-history bug.
- v0.0.2 installed as a same-key update; owner reported both SMS grants and then the expected **Not granted** after denial/reopen. This is owner-reported development evidence only.
- v0.0.3 has **not** been installed; no raw messages read. Further installs/device tests/prompts are deferred by owner request.
- Phone replacement is not full private-history migration: planned shared restore + old-device sync revocation; private categories/rules/history remain on old phone. CSV is portable records, not a full backup. Those replacement features are not implemented yet.

Verifier-built current APK at standard ignored output path:
`android/app/build/outputs/apk/debug/app-debug.apk`

Recorded SHA-256: `99a97e830c58f6c9ab30c500d9f3440b44c3838f827e34bda65e2045fb9a05a7`. Recheck existence/hash before use; build outputs are not Git source and can become stale. CI debug keys/artifact hashes can differ. Never uninstall automatically to resolve a signing mismatch.

## 7. Toolchain / verification commands

Kotlin (not Java app code), Java 17 toolchain, min API 29, compile/target 36. Versions are authoritative in `android/gradle/libs.versions.toml`: Gradle 8.13, AGP 8.13.0, Kotlin 2.2.20, Compose BOM 2025.09.01, Room 2.8.3, SQLCipher 4.20.0, SQLite 2.7.0, KSP 2.2.20-2.0.3, coroutines 1.8.1, serialization 1.7.3.

User-space Java/Android/Gradle are under `~/.local/share/expense-tracker-toolchain/`; source `ops/release/android-env.sh`. Node/npm are available; Deno/Supabase CLI and backend infrastructure have not been set up.

Have the **verification agent**, not the implementation/test-writing roles, execute:

```sh
source ops/release/android-env.sh
python3 ops/release/check-prototype-boundary.py
python3 ops/release/check-room-schema.py
(cd android && ./gradlew --no-daemon --max-workers=2 \
  :core-model:test :core-data:testDebugUnitTest :core-data:assembleDebugAndroidTest \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease)
git diff --exit-code -- android/core-data/schemas
```

Wrapper distribution is checksum-pinned. Earlier GitHub/Gradle HTTP 504s were external bootstrap failures; workflow now has bounded retries/cache and latest CI is green. An existing checksum-verified installed Gradle 8.13 is also at `~/.local/share/expense-tracker-toolchain/gradle/gradle-8.13/bin/gradle`. Do not silently change versions/providers/mirrors to bypass failures. Use `--write-locks` only for deliberate reviewed dependency/schema work, then repeat locked checks.

Never run `connectedDebugAndroidTest` or ADB installs as part of routine host verification while phone tests remain deferred.

## 8. Three-agent workspace / Git workflow

[Workflow rules](ops/runbooks/development-agents.md) are checked in. Actual delegation used separate **Pi CLI subprocesses**, not a pretend built-in subagent tool. Reuse configured authorized model/auth; never print credentials. Read the installed Pi CLI/subagent documentation before changing this harness workflow.

Local worktrees at handover (all clean):

| Role | Path | Current branch/state |
|---|---|---|
| Coordinator | `/home/dishita/expense-tracker` | `main` |
| Implementation | `/home/dishita/expense-tracker-agents/implementation` | `agents/settings-budget-implementation` |
| Test writer | `/home/dishita/expense-tracker-agents/tests` | `agents/settings-budget-tests` |
| Verifier | `/home/dishita/expense-tracker-agents/verification` | detached at last main checkpoint |

Implementation/test branches contain their original commits; main has reviewed cherry-picks. **Do not re-merge those branches wholesale.** For another batch, check every worktree for changes and create fresh role branches from current main; pin final verifier checkout to the integrated commit. Source-only agents never run tests. Verifier never repairs failing tests/code; route findings back and rerun.

Same-machine private handoff material (ignored, not available from a fresh clone):
- `ops/private/agent-team/rules-storage/`
- `ops/private/agent-team/settings-budget/`

These contain task contracts, role reports, session directories and execution logs. Public verification summaries contain the durable evidence. All delegated jobs for these batches finished. **Old PID files are stale; do not kill processes using them.** No new jobs are intentionally left running for this handover.

A prior accidental editor undo reverted the billing-approval checklist. It was restored precisely, with no code loss; don't treat that as an approval reversal. Check saved diffs before restoring anything, and never reset unrelated/user edits wholesale.

## 9. Next-session priorities

1. Confirm this handover, latest Git/CI, and the user's requested pause. Clearly explain that packages 0/1 are not complete and some package-2 work already exists.
2. Audit/finish missing package-1 items within approved scope: local Supabase tooling/reset/migrations, non-billing backend/contract scaffolding, staging prerequisites/deployment and relevant validation. No staging project, web app, `supabase/`, `contracts/openapi.yaml` or fixture tree currently exists. Do not claim those tasks passed because Android CI is green.
3. Resolve owner-dependent package-0 blockers: correct Play account setup before fee payment, domain/production identity/signing, SMS declaration and actual Play-track tests. Use the existing readiness/checklist rather than inventing successful gate evidence. Budget/spend approval remains separate.
4. Before commerce SQL/API implementation, replace the explicitly superseded Razorpay-specific contract with verified Play-specific design. Model approval is already recorded; don't ask the owner to approve ₹499/free install again unless proposing a real change.
5. **Ask before resuming/advancing package 2 or changing sequence.** Remaining engine tasks include supported-bank synthetic parser fixtures, provenance/HMAC observations, conservative dedup, gated receiver/listener/history processing and review workflows. Don't wire dormant storage to real alerts before activation, valid verified lease, source consent and permission gates exist.
6. Actual Home/Personal feed/Review UI belongs to package 3; nothing here says it is complete. A synthetic screen preview ahead of sequence would require the owner's approval, not a silent reprioritization.

Suggested first message for a new session:

> “I read HANDOVER.md and the specifications. The Android prototype/CI and partial local-engine storage are built; packages 0/1 still have outstanding external/staging/contract work. I will preserve that code and stay at the requested handover boundary. Shall we address the remaining package-1 prerequisites before resuming package 2?”
