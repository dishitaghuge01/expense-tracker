# Family Expense Tracker

Android-first, private-by-default household expense tracking for India.

**New session:** start with [HANDOVER.md](HANDOVER.md). Further engine coding is paused at the owner's requested handover boundary.

## Specification

- [Product requirements](prd.md)
- [Technical specification and build plan](buildplan.md)
- [Distribution validation checklist](ops/release/distribution-validation.md)
- [Google Play readiness and approved billing model](ops/release/play-readiness.md)
- [Three-agent development workflow](ops/runbooks/development-agents.md)
- [Independent rules-storage verification](ops/release/rules-storage-verification.md)
- [Independent settings/budget-state verification](ops/release/settings-budget-verification.md)

The specifications are authoritative. Work packages 0–8 define the intended sequence; the handover records the actual partial progress and sequencing gap. Public checkout stays closed until all release gates pass. No bank/device coverage has been verified yet.

## Current status

Packages 0 and 1 remain incomplete; some package-2 foundations are already implemented. The disposable Android prototype has a fictional demo and consent-only SMS grant probe. See [Android build and phone-check instructions](android/README.md). The [pure Kotlin foundation](android/core-model/README.md) includes exact INR conversion/formatting, strict IST reporting dates, scoped monthly accounting, fail-closed consent states and integer budget thresholds. The [encrypted storage foundation](android/core-data/README.md) adds Keystore-wrapped secrets, a non-destructive SQLCipher/Room opener and an eleven-table development ledger/rules/undo/coverage/preferences slice with additive migrations, DAOs and schema exports. The remaining Room tables/migrations and device validation are pending. No real capture, backend, checkout, or deployed website exists yet. Google Play is the selected launch channel. Domain and production identifiers remain deferred; the free-install/one-time Play Billing model is approved, initially ₹499 with future Play Console price changes allowed. Play permission eligibility, detailed billing contracts and revised budget/spend remain gated. A prototype install does not satisfy public distribution gates.

The source repository is public at the owner's explicit request, overriding the private-source setting in the build plan. Public installs/updates will use Google Play; direct APKs are development-only, not a separate public release channel.

## Safety

Never commit credentials, production signing keys, raw SMS, statements, or volunteer-identifying information. Use synthetic or consented, redacted fixtures only. Keep private operator artifacts outside Git (the local `ops/private/` directory is ignored).

See [release configuration](ops/release/config.md) for unresolved deployment identifiers. No identifiers or verification results should be invented to bypass a gate.
