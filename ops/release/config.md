# Release configuration

Status: preparation only; no deployment or verified capture coverage.

## Repository visibility decision

Owner explicitly requested a public source repository. This overrides only the private-source setting in build plan §1; all data boundaries and secret-handling requirements remain unchanged.

- Source: https://github.com/dishitaghuge01/expense-tracker
- Releases-only repository: not created

## Deployment identifiers — awaiting owner input / verification

| Identifier | Status |
|---|---|
| Owner-controlled domain | Not selected or verified; do not purchase without approval |
| Production application ID | Blocked on owned domain |
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
- Public pilot: production package and signing key, admin-issued licences only.

## Local toolchain discovery

Initial inspection found Node/npm available, but no Java, Gradle, Android SDK tools, Deno, or Supabase CLI on PATH. Android SDK environment variables were unset. No build or device verification has been performed. Follow the specified stable Java 17 / API 36 toolchain when provisioning; freeze versions only after a clean CI build.
