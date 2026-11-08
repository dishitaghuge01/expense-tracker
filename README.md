# Family Expense Tracker

Android-first, private-by-default household expense tracking for India.

## Specification

- [Product requirements](prd.md)
- [Technical specification and build plan](buildplan.md)
- [Distribution validation checklist](ops/release/distribution-validation.md)

The specifications are authoritative. Implementation follows work packages 0–8 in order. Public checkout stays closed until all release gates pass. No bank/device coverage has been verified yet.

## Current status

Work package 0: distribution validation preparation. No APK, backend, checkout, or deployed website exists yet.

The source repository is public at the owner's explicit request, overriding the private-source setting in the build plan. Signed APKs will use a separate public releases-only repository.

## Safety

Never commit credentials, production signing keys, raw SMS, statements, or volunteer-identifying information. Use synthetic or consented, redacted fixtures only. Keep private operator artifacts outside Git (the local `ops/private/` directory is ignored).

See [release configuration](ops/release/config.md) for unresolved deployment identifiers. No identifiers or verification results should be invented to bypass a gate.
