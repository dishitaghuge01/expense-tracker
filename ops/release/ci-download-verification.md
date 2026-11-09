# CI download repair — verified 9 November 2026

Verified product source: `77247e88bcec7a8eb9a32aed5f1f3768593a2469`.

## Actual GitHub CI results

All three runs completed successfully on the same source:

| Run | Evidence |
| --- | --- |
| [Backend cold bootstrap — 42574316091](https://github.com/dishitaghuge01/expense-tracker/actions/runs/42574316091) | Full official tool downloads passed SHA-256 verification; complete backend workflow passed |
| [Backend warm cache — 42574575769](https://github.com/dishitaghuge01/expense-tracker/actions/runs/42574575769) | Same-source manual dispatch; restored archive cache, installer reverified both archives; complete backend workflow passed |
| [Android — 42574316127](https://github.com/dishitaghuge01/expense-tracker/actions/runs/42574316127) | 78 JVM tests, lint, APK/AAB builds and frozen schema check passed; device tests assembled, not executed by CI |

Backend checks include 24 offline bootstrap test methods, 180 handler/contract tests, local synthetic reset/migration, 58 pgTAP assertions, live public release routing, unchanged dependency lock and project-local cleanup. The independent local fault run additionally counted 41 scenario variants; this is not 41 separate unittest methods.

## What changed

- Programmatic downloads now use **GitHub's official release-asset API**, with explicit binary Accept/API-version headers, for the **same existing assets**. Browser release URLs had returned repeated HTTP 504s. No third-party mirror, new publisher, version change or checksum bypass was introduced.
- Deno remains **2.9.8**, asset `662551650`, SHA-256 `e890975bc2549807bbcc0b584ffafda72cc7c93349dd99c80483dd784f6c4609`.
- Supabase CLI remains **3.0.0**, asset `701653806`, SHA-256 `eeb61edcd59a4735fcb5e58596574f25d03d420efca779f43ddb307e1cc5c754`.
- Independently checked official tag metadata matched each repository, asset ID/name/size and existing digest. Installer does not select a dynamic latest release or require a token.
- Only complete, hash-verified archives enter `.tools/backend/downloads/`. Cache hits are rechecked before extraction; cache presence never substitutes for integrity verification. Installed binaries/state/credentials are not cached.
- Each uncached asset has at most three attempts with 15/45-second backoff, finite connect/transfer limits, default TLS checks and curlrc disabled. Transient HTTP/transport errors retry; permanent errors, certificate failures and checksum mismatches fail closed. Partial/error/JSON responses are never installed or promoted. Existing binaries remain intact on download/preparation failure.
- Backend CI gates bootstrap with independent offline fault tests and an exact OS/architecture/manifest-hash cache key; no broad restore keys. Cleanup remains conditional on installed tools, including after later failures.

Separate implementation, independent test-writing and independent verification agents were used. Tests cover 504/timeout recovery and exhaustion, permanent errors, bad checksums, corrupt caches, partial cleanup, warm reuse, installed-binary preservation, exact publisher/API headers and HTTP-200 JSON metadata rejection.

## Historical failures and limits

The earlier three-504 cold run is preserved. Local API diagnostics received binary data but timed out before complete-file verification; range probes proved only small-range delivery. Those are **not** relabelled as local cold-download successes. Complete new-route delivery and verified cache reuse were established by the actual GitHub cold/warm runs above. The precise upstream cause of the old browser-route failures was not proven.

Bounded retries cannot guarantee service during a sustained upstream outage. Future cache corruption must fail visibly, not fall back silently. This repair is CI/tooling only: it does not complete package 1, add transaction capture, provision cloud services, qualify Play distribution or authorize phone tests.

Private reports and sanitized execution evidence remain under ignored `ops/private/agent-team/ci-download-resilience/`. See [local backend instructions](../runbooks/local-backend.md) for cache paths, exact retry budgets and deliberate corrupt-cache recovery.
