# Family Expense Tracker

Android-first, private-by-default household expense tracking for India.

## Specification

- [Product requirements](prd.md)
- [Technical specification and build plan](buildplan.md)
- [Distribution validation checklist](ops/release/distribution-validation.md)
- [Google Play readiness and approved billing model](ops/release/play-readiness.md)

The specifications are authoritative. Implementation follows work packages 0–8 in order. Public checkout stays closed until all release gates pass. No bank/device coverage has been verified yet.

## Current status

Work package 0: disposable Android prototype with a fictional demo and consent-only SMS grant probe. See [Android build and phone-check instructions](android/README.md). The [pure Kotlin foundation](android/core-model/README.md) now includes exact INR conversion/formatting, strict IST reporting dates and scoped monthly accounting, covered by synthetic tests. No real capture, backend, checkout, or deployed website exists yet. Google Play is the selected launch channel. Domain and production identifiers remain deferred; the free-install/one-time Play Billing model is approved, initially ₹499 with future Play Console price changes allowed. Play permission eligibility, detailed billing contracts and revised budget/spend remain gated. A prototype install does not satisfy public distribution gates.

The source repository is public at the owner's explicit request, overriding the private-source setting in the build plan. Signed APKs will use a separate public releases-only repository.

## Safety

Never commit credentials, production signing keys, raw SMS, statements, or volunteer-identifying information. Use synthetic or consented, redacted fixtures only. Keep private operator artifacts outside Git (the local `ops/private/` directory is ignored).

See [release configuration](ops/release/config.md) for unresolved deployment identifiers. No identifiers or verification results should be invented to bypass a gate.
