# Release configuration

Status: preparation only; no deployment or verified capture coverage.

## Repository visibility decision

Owner explicitly requested a public source repository. This overrides only the private-source setting in build plan §1; all data boundaries and secret-handling requirements remain unchanged.

- Source: https://github.com/dishitaghuge01/expense-tracker
- Releases-only repository: not required for the selected Play launch
- Launch channel: Google Play, selected by owner; commerce implementation blocked on Play policy verification and owner-approved revisions
- Play Console account / SMS declaration / app-signing setup / testing eligibility: not verified
- Domain: owner has none; purchase deferred during local development

## Deployment identifiers — awaiting owner input / verification

| Identifier | Status |
|---|---|
| Owner-controlled domain | Not selected or verified; do not purchase without approval |
| Production application ID | Blocked on owned domain; prototype ID is not production |
| Staging application ID | Production ID plus environment suffix, not assigned |
| Production signing SHA fingerprint | Not generated; production key must be kept offline |
| Staging signing SHA fingerprint | Not generated; separate key required |
| Official site origin and APK asset URL | Not deployed |
| Supabase staging / production URLs | Not provisioned |
| Google OAuth origins / redirect allowlists | Not configured |

Never record signing private keys or provider secrets here.

## Release controls

- Checkout: closed; no commerce infrastructure exists.
- Supported bank/device matrix: empty until actual-device evidence is recorded.
- Notification capture: not enabled or verified.
- Public pilot: Play testing tracks, production package and Play app-signing identity, admin-issued licences only; blocked on identifiers, Play setup and permission eligibility.

## Disposable prototype identity

Owner approved a temporary application ID: `dev.expensetracker.prototype`. This is not derived from an owned domain, is not a production identity, and must not be promoted to public pilot or launch. Initial version: `0.0.1-prototype` (code 1). Debug signing is development-only; no production key has been generated. The prototype has no capture, authentication, network permission, SMS receiver or notification listener. It requests/checks SMS grants only after consent and offers static synthetic data.

## Available test hardware

Owner reports a Redmi Note 11T 5G, model 21091116AI, Android 13, build TP1A.220624.014, available for a few days. Permissions are owner-reported, not verified for our APK. No device identifier or financial data collected. Repeat selected-channel installation, grants and update checks on any replacement phone.

Replacement behavior remains as specified: revoke old-device sync; restore shared records on the new phone; private history/rules/categories do not automatically migrate. CSV export is not a full backup.

## Local toolchain discovery

Installed with owner authorization in user-space under `~/.local/share/expense-tracker-toolchain/`: Temurin Java 17.0.21+9, Android command-line tools (archive 13114758), API 36 platform, build-tools 36.0.0 and platform-tools. SDK licences accepted during installation. Source `ops/release/android-env.sh` to configure the current shell. `java -version` and `adb devices -l` succeeded; no phone was connected. Node/npm are available. Gradle 8.13 is provisioned with a checksum-pinned wrapper. The prototype uses AGP 8.13.0, Kotlin 2.2.20, Compose BOM 2025.09.01 and Activity Compose 1.11.0, with checked-in dependency locks. CI and local test/lint/debug/optimized unsigned-release builds pass; this is the prototype toolchain baseline, with future upgrades reviewed deliberately. Deno and Supabase CLI are not yet provisioned. No device verification has been performed. See `prototype-evidence.md` for build evidence and remaining gates.
